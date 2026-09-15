package ch.sthomas.stddivelogger.service.importer.reprocess;

import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.BUDDIES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.GAS_CONSUMPTION;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.NOTES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.VISIBILITY;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.ImportFileDataService;
import ch.sthomas.stddivelogger.data.service.ReprocessConflictDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;
import ch.sthomas.stddivelogger.model.dive.profile.ProfileMeasurementMerge;
import ch.sthomas.stddivelogger.model.dive.profile.ReimportSimilarityCheck;
import ch.sthomas.stddivelogger.model.entity.DiveImportFileFieldEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.ImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.ImportReprocessConflictEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.importfile.ImportLocator;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflict;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictKind;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictStatus;
import ch.sthomas.stddivelogger.model.importfile.ReprocessedProfile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.ImportFileService;
import ch.sthomas.stddivelogger.service.importer.ImportParserVersions;
import ch.sthomas.stddivelogger.service.importer.ImportService;
import ch.sthomas.stddivelogger.service.importer.ImportedFieldValues;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Re-derives profiles and dive-level values from the stored files they came from, after an importer
 * update ({@link ImportParserVersions}) or a dive-site change (the clock of a source without a
 * timezone depends on it). What only adds data is applied; real changes wait as conflicts on the
 * backfill page. Alignment and trims the diver made are kept.
 */
@Service
public class ImportReprocessService {
    private static final Logger logger = LoggerFactory.getLogger(ImportReprocessService.class);
    private static final JsonMapper JSON = ObjectMapperUtils.objectMapperBuilder(_ -> {}).build();
    private static final int BATCH_SIZE = 200;
    private static final Duration SAME_INSTANT = Duration.ofMillis(1);
    private static final Duration CLIP_TOLERANCE = Duration.ofSeconds(1);

    public record RunResult(int profiles, int diveLinks, int applied, int conflicts, int failed) {}

    private enum Outcome {
        UNCHANGED,
        APPLIED,
        CONFLICT
    }

    private record Derived(
            DiveProfileImportFileEntity link, DiveProfileUpload upload, Instant activeStart) {}

    private record Proposal(DiveProfileUpload upload, Duration clockShift) {}

    private record Resolved(long diveId, boolean profile, @Nullable String staleMessage) {}

    private final ImportService importService;
    private final ImportFileService importFileService;
    private final ImportFileDataService importFileDataService;
    private final ReprocessConflictDataService conflictData;
    private final DiveDataService diveDataService;
    private final DiveService diveService;
    private final UserDataService userDataService;
    private final ReprocessTransactions transactions;

    public ImportReprocessService(
            final ImportService importService,
            final ImportFileService importFileService,
            final ImportFileDataService importFileDataService,
            final ReprocessConflictDataService conflictData,
            final DiveDataService diveDataService,
            final DiveService diveService,
            final UserDataService userDataService,
            final ReprocessTransactions transactions) {
        this.importService = importService;
        this.importFileService = importFileService;
        this.importFileDataService = importFileDataService;
        this.conflictData = conflictData;
        this.diveDataService = diveDataService;
        this.diveService = diveService;
        this.userDataService = userDataService;
        this.transactions = transactions;
    }

    /** One batch of what an importer update or a site change left to re-process. */
    public RunResult reprocessPending() {
        var applied = 0;
        var conflicts = 0;
        var failed = 0;
        final var profileIds =
                importFileDataService.findProfilesNeedingReprocessing(
                        ImportParserVersions::current, BATCH_SIZE);
        for (final var profileId : profileIds) {
            try {
                final var outcome = transactions.run(() -> reprocessProfile(profileId));
                applied += outcome == Outcome.APPLIED ? 1 : 0;
                conflicts += outcome == Outcome.CONFLICT ? 1 : 0;
            } catch (final RuntimeException e) {
                failed++;
                logger.warn("Re-processing profile {} failed", profileId, e);
                quietly("mark profile " + profileId, () -> markProfileProcessed(profileId));
            }
        }
        final var diveLinkIds =
                importFileDataService.findDiveLinksNeedingReprocessing(
                        ImportParserVersions::current, BATCH_SIZE);
        for (final var linkId : diveLinkIds) {
            try {
                final var outcome = transactions.run(() -> reprocessDiveLink(linkId));
                applied += outcome == Outcome.APPLIED ? 1 : 0;
                conflicts += outcome == Outcome.CONFLICT ? 1 : 0;
            } catch (final RuntimeException e) {
                failed++;
                logger.warn("Re-processing dive file link {} failed", linkId, e);
                quietly("mark dive link " + linkId, () -> markDiveLinkProcessed(linkId));
            }
        }
        final var result =
                new RunResult(profileIds.size(), diveLinkIds.size(), applied, conflicts, failed);
        if (!profileIds.isEmpty() || !diveLinkIds.isEmpty()) {
            logger.info("Re-processed stored import files: {}", result);
        }
        return result;
    }

    // A failed stamp must not end the batch; the link then simply comes up again next run.
    private void quietly(final String what, final Supplier<Boolean> work) {
        try {
            transactions.run(work);
        } catch (final RuntimeException e) {
            logger.warn("Could not {} as processed", what, e);
        }
    }

    private Outcome reprocessProfile(final long profileId) {
        final var ctx = diveDataService.findReprocessContext(profileId).orElse(null);
        final var links = importFileDataService.findProfileLinks(profileId);
        if (ctx == null || links.isEmpty()) {
            return Outcome.UNCHANGED;
        }
        final var user = userDataService.findUserById(ctx.userId());
        final var derived = links.stream().map(link -> derive(user, link, ctx.site())).toList();
        final var proposal = propose(ctx, derived);
        final Map<Long, Instant> rawStarts =
                derived.stream()
                        .collect(Collectors.toMap(d -> d.link().getId(), Derived::activeStart));
        final var diff =
                ProfileReprocessDiff.compare(
                        ctx.measurements(),
                        ctx.decoSettings(),
                        proposal.upload().measurements(),
                        proposal.upload().decoSettings(),
                        proposal.clockShift());
        final var siteId = ctx.site().id();
        return switch (diff.outcome()) {
            case IDENTICAL -> {
                stamp(links, rawStarts, siteId);
                conflictData.supersedeOpenForProfile(ctx.diveId(), profileId);
                yield Outcome.UNCHANGED;
            }
            case ADDITIONS_ONLY -> {
                diveDataService.applyReprocessedProfile(
                        ctx.diveId(), profileId, proposal.upload(), Duration.ZERO);
                stamp(links, rawStarts, siteId);
                conflictData.supersedeOpenForProfile(ctx.diveId(), profileId);
                yield Outcome.APPLIED;
            }
            case CHANGED -> {
                conflictData.open(
                        new ImportReprocessConflictEntity(
                                ctx.userId(),
                                ctx.diveId(),
                                profileId,
                                null,
                                ReprocessConflictKind.PROFILE,
                                null,
                                diff.summary(),
                                currentSummary(ctx),
                                JSON.valueToTree(
                                        new ReprocessedProfile(
                                                proposal.upload(),
                                                proposal.clockShift(),
                                                rawStarts,
                                                ctx.start(),
                                                ctx.end(),
                                                ctx.measurements().size()))));
                // Not re-raised every run; the clock reference moves only once the diver decides.
                links.forEach(l -> l.stampVersion(version(l), siteId));
                importFileDataService.saveProfileLinks(links);
                yield Outcome.CONFLICT;
            }
        };
    }

    private Derived derive(
            final User user, final DiveProfileImportFileEntity link, final DiveSite site) {
        final var parsed = parseLinked(user, link.getFile(), link.getLocator());
        final var profiles = importService.correctedForSite(parsed, site).payload().profiles();
        final var index = link.getLocator().profileIndex();
        if (index >= profiles.size()) {
            throw new IllegalStateException(
                    "Stored file "
                            + link.getFile().getId()
                            + " no longer has profile "
                            + index
                            + " of "
                            + link.getLocator());
        }
        final var upload = profiles.get(index);
        return new Derived(
                link,
                upload,
                ReimportSimilarityCheck.activeStart(upload.measurements(), upload.start()));
    }

    private ParsedImport parseLinked(
            final User user, final ImportFileEntity file, final ImportLocator locator) {
        final ParsedImportResultStreaming.Result result;
        try {
            result = importService.parseStored(user, file, importFileService.read(file));
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not parse stored file " + file.getId(), e);
        }
        final var wholeFile = locator.entry() == null && locator.id() == null;
        return result.parsed().stream()
                .filter(p -> wholeFile || locator.sameDive(p.locator()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Stored file "
                                                + file.getId()
                                                + " no longer contains "
                                                + locator.dive()
                                                + ": "
                                                + String.join("; ", result.errors())));
    }

    /**
     * The profile as its files read today: merged like a refine merges them, on the profile's own
     * clock (the diver's alignment kept) moved by whatever the importer now reads differently, with
     * the recorded trims applied.
     */
    private static Proposal propose(
            final DiveDataService.ReprocessContext ctx, final List<Derived> derived) {
        final var base = derived.getFirst();
        final var clockShift =
                Duration.between(base.link().getRawActiveStart(), base.activeStart());
        var merged = base.upload();
        for (final var other : derived.subList(1, derived.size())) {
            final var aligned =
                    other.upload()
                            .shifted(Duration.between(other.activeStart(), base.activeStart()));
            merged =
                    new DiveProfileUpload(
                            merged.diveComputerId(),
                            earlier(merged.start(), aligned.start()),
                            later(merged.end(), aligned.end()),
                            ProfileMeasurementMerge.merge(
                                    merged.measurements(), aligned.measurements()),
                            DecoSettings.merge(merged.decoSettings(), aligned.decoSettings()));
        }
        final var currentActive =
                ReimportSimilarityCheck.activeStart(ctx.measurements(), ctx.start());
        var upload =
                new DiveProfileUpload(
                                ctx.computerId(),
                                merged.start(),
                                merged.end(),
                                merged.measurements(),
                                merged.decoSettings())
                        .shifted(
                                Duration.between(
                                        base.activeStart(), currentActive.plus(clockShift)));
        upload = applyTrims(upload, ctx.trimStartOffset(), ctx.trimEndOffset());
        if (!ctx.importFilesComplete()) {
            // Samples from files that aren't kept are in it too: only add, and only within what
            // it covers - trims made before any file was kept were never recorded.
            final var current =
                    new DiveProfileUpload(
                                    ctx.computerId(),
                                    ctx.start(),
                                    ctx.end(),
                                    ctx.measurements(),
                                    ctx.decoSettings())
                            .shifted(clockShift);
            final var from = current.start().minus(CLIP_TOLERANCE);
            final var to = current.end().plus(CLIP_TOLERANCE);
            final var within =
                    upload.measurements().stream()
                            .filter(m -> !m.time().isBefore(from) && !m.time().isAfter(to))
                            .toList();
            upload =
                    new DiveProfileUpload(
                            ctx.computerId(),
                            current.start(),
                            current.end(),
                            ProfileMeasurementMerge.merge(current.measurements(), within),
                            DecoSettings.merge(current.decoSettings(), upload.decoSettings()));
        }
        return new Proposal(upload, clockShift);
    }

    private static DiveProfileUpload applyTrims(
            final DiveProfileUpload upload,
            final @Nullable Duration startOffset,
            final @Nullable Duration endOffset) {
        if (startOffset == null && endOffset == null) {
            return upload;
        }
        final var active =
                ReimportSimilarityCheck.activeStart(upload.measurements(), upload.start());
        return upload.trimmed(
                startOffset == null ? null : active.plus(startOffset),
                endOffset == null ? null : active.plus(endOffset));
    }

    private Outcome reprocessDiveLink(final long linkId) {
        final var link = importFileDataService.findDiveLink(linkId).orElse(null);
        if (link == null) {
            return Outcome.UNCHANGED;
        }
        final var dive = diveDataService.findDiveById(link.getDiveId()).orElse(null);
        if (dive == null) {
            return Outcome.UNCHANGED;
        }
        final var user = userDataService.findUserById(dive.user().id());
        final var parsed = parseLinked(user, link.getFile(), link.getLocator());
        final var fromFile = ImportedFieldValues.reprocessable(parsed.payload());
        final var current = ImportedFieldValues.current(dive);
        final var rows =
                importFileDataService
                        .findFieldsByDiveLink(List.of(linkId))
                        .getOrDefault(linkId, List.of())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        DiveImportFileFieldEntity::getField, Function.identity()));
        var outcome = Outcome.UNCHANGED;
        for (final var field : ImportedDiveField.REPROCESSABLE) {
            final var value = fromFile.get(field);
            final var now = current.get(field);
            final var row = rows.get(field);
            if (row != null) {
                final var imported = row.getImportedValue();
                if (!ImportedFieldValues.same(field, now, imported)) {
                    continue; // edited by the diver since - theirs
                }
                if (value == null || ImportedFieldValues.same(field, value, imported)) {
                    conflictData.supersedeOpenForField(dive.id(), field);
                    continue;
                }
                conflictData.open(
                        new ImportReprocessConflictEntity(
                                user.id(),
                                dive.id(),
                                null,
                                linkId,
                                ReprocessConflictKind.DIVE_FIELD,
                                field,
                                ImportedFieldValues.describeChange(field, now, value),
                                now,
                                value));
                outcome = Outcome.CONFLICT;
            } else if (value != null && ImportedFieldValues.isEmpty(field, now)) {
                applyFieldValue(dive.id(), field, value);
                importFileDataService.saveField(linkId, field, value);
                if (outcome == Outcome.UNCHANGED) {
                    outcome = Outcome.APPLIED;
                }
            }
        }
        link.stampVersion(ImportParserVersions.current(link.getFile().getSource()));
        importFileDataService.saveDiveLink(link);
        return outcome;
    }

    private boolean markProfileProcessed(final long profileId) {
        final var siteId =
                diveDataService
                        .findReprocessContext(profileId)
                        .map(ctx -> ctx.site().id())
                        .orElse(null);
        final var links = importFileDataService.findProfileLinks(profileId);
        links.forEach(l -> l.stampVersion(version(l), siteId));
        importFileDataService.saveProfileLinks(links);
        return true;
    }

    private boolean markDiveLinkProcessed(final long linkId) {
        importFileDataService
                .findDiveLink(linkId)
                .ifPresent(
                        link -> {
                            link.stampVersion(
                                    ImportParserVersions.current(link.getFile().getSource()));
                            importFileDataService.saveDiveLink(link);
                        });
        return true;
    }

    public List<ReprocessConflict> listOpen(final User user) {
        return conflictData.listOpen(user.id());
    }

    public long countOpen(final User user) {
        return conflictData.countOpen(user.id());
    }

    /** Applies the proposal; refused (and dropped) when the data changed since it was made. */
    public List<ReprocessConflict> apply(final User user, final long conflictId) {
        final var resolved = transactions.run(() -> applyInTransaction(user, conflictId));
        if (resolved.staleMessage() != null) {
            throw new IllegalArgumentException(resolved.staleMessage());
        }
        if (resolved.profile()) {
            diveService.createSaveDivePreview(user, resolved.diveId());
        }
        return listOpen(user);
    }

    /**
     * Keeps what the dive has; the same difference isn't raised again for this importer version.
     */
    public List<ReprocessConflict> keep(final User user, final long conflictId) {
        transactions.run(() -> keepInTransaction(user, conflictId));
        return listOpen(user);
    }

    private Resolved applyInTransaction(final User user, final long conflictId) {
        final var conflict = openConflict(user, conflictId);
        if (!diveService.hasWriteAccess(user, conflict.getDiveId())) {
            throw ForbiddenException.forDiveId(user, conflict.getDiveId());
        }
        return switch (conflict.getKind()) {
            case PROFILE -> applyProfile(conflict);
            case DIVE_FIELD -> applyField(conflict);
        };
    }

    private Resolved applyProfile(final ImportReprocessConflictEntity conflict) {
        final var profileId = Objects.requireNonNull(conflict.getProfileId());
        final var proposal = proposalOf(conflict);
        final var ctx = diveDataService.findReprocessContext(profileId).orElse(null);
        if (ctx == null || changedSince(ctx, proposal)) {
            conflictData.resolve(conflict, ReprocessConflictStatus.SUPERSEDED);
            final var links = importFileDataService.findProfileLinks(profileId);
            links.forEach(DiveProfileImportFileEntity::requestReprocessing);
            importFileDataService.saveProfileLinks(links);
            return new Resolved(
                    conflict.getDiveId(),
                    true,
                    "The profile changed since this was proposed - it is checked again on the next"
                            + " re-processing run.");
        }
        diveDataService.applyReprocessedProfile(
                ctx.diveId(), profileId, proposal.profile(), proposal.clockShift());
        stamp(
                importFileDataService.findProfileLinks(profileId),
                proposal.rawActiveStartByLink(),
                ctx.site().id());
        conflictData.resolve(conflict, ReprocessConflictStatus.APPLIED);
        return new Resolved(ctx.diveId(), true, null);
    }

    private Resolved applyField(final ImportReprocessConflictEntity conflict) {
        final var field = Objects.requireNonNull(conflict.getField());
        final var dive =
                diveDataService
                        .findDiveById(conflict.getDiveId())
                        .orElseThrow(
                                () -> new NoSuchElementException("Dive " + conflict.getDiveId()));
        final var now = ImportedFieldValues.current(dive).get(field);
        if (!ImportedFieldValues.same(field, now, conflict.getCurrentValue())) {
            conflictData.resolve(conflict, ReprocessConflictStatus.SUPERSEDED);
            return new Resolved(
                    dive.id(), false, "The value was edited since this was proposed - kept as is.");
        }
        applyFieldValue(dive.id(), field, conflict.getProposedValue());
        final var linkId = conflict.getDiveImportFileId();
        if (linkId != null) {
            importFileDataService.saveField(linkId, field, conflict.getProposedValue());
        }
        conflictData.resolve(conflict, ReprocessConflictStatus.APPLIED);
        return new Resolved(dive.id(), false, null);
    }

    private boolean keepInTransaction(final User user, final long conflictId) {
        final var conflict = openConflict(user, conflictId);
        switch (conflict.getKind()) {
            case PROFILE -> {
                final var profileId = Objects.requireNonNull(conflict.getProfileId());
                final var siteId =
                        diveDataService
                                .findReprocessContext(profileId)
                                .map(ctx -> ctx.site().id())
                                .orElse(null);
                // The stored profile is what the diver wants from these files: later importer
                // updates are compared with how the files read now.
                stamp(
                        importFileDataService.findProfileLinks(profileId),
                        proposalOf(conflict).rawActiveStartByLink(),
                        siteId);
            }
            case DIVE_FIELD -> {
                // Seen: from now on the dive's value counts as the diver's own edit.
                final var linkId = conflict.getDiveImportFileId();
                if (linkId != null) {
                    importFileDataService.saveField(
                            linkId,
                            Objects.requireNonNull(conflict.getField()),
                            conflict.getProposedValue());
                }
            }
        }
        conflictData.resolve(conflict, ReprocessConflictStatus.KEPT_CURRENT);
        return true;
    }

    private ImportReprocessConflictEntity openConflict(final User user, final long conflictId) {
        return conflictData
                .findOwned(conflictId, user.id())
                .filter(c -> c.getStatus() == ReprocessConflictStatus.OPEN)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "No open re-processing change " + conflictId));
    }

    private void applyFieldValue(
            final long diveId, final ImportedDiveField field, final JsonNode value) {
        diveDataService.applyReimportResolution(
                diveId,
                field == NOTES ? ImportedFieldValues.notes(value) : null,
                field == VISIBILITY ? ImportedFieldValues.visibility(value) : null,
                field == BUDDIES ? ImportedFieldValues.buddies(value) : null,
                field == GAS_CONSUMPTION ? ImportedFieldValues.gasConsumption(value) : null);
    }

    private void stamp(
            final List<DiveProfileImportFileEntity> links,
            final Map<Long, Instant> rawStarts,
            final @Nullable Long siteId) {
        links.forEach(
                l ->
                        l.stamp(
                                version(l),
                                rawStarts.getOrDefault(l.getId(), l.getRawActiveStart()),
                                siteId));
        importFileDataService.saveProfileLinks(links);
    }

    private static int version(final DiveProfileImportFileEntity link) {
        return ImportParserVersions.current(link.getFile().getSource());
    }

    private static ReprocessedProfile proposalOf(final ImportReprocessConflictEntity conflict) {
        return Objects.requireNonNull(
                JSON.treeToValue(conflict.getProposedValue(), ReprocessedProfile.class));
    }

    private static boolean changedSince(
            final DiveDataService.ReprocessContext ctx, final ReprocessedProfile proposal) {
        return Duration.between(ctx.start(), proposal.startBefore()).abs().compareTo(SAME_INSTANT)
                        > 0
                || Duration.between(ctx.end(), proposal.endBefore()).abs().compareTo(SAME_INSTANT)
                        > 0
                || ctx.measurements().size() != proposal.sampleCountBefore();
    }

    private static JsonNode currentSummary(final DiveDataService.ReprocessContext ctx) {
        final var summary = new LinkedHashMap<String, Object>();
        summary.put("start", ctx.start().toString());
        summary.put("end", ctx.end().toString());
        summary.put("samples", ctx.measurements().size());
        return JSON.valueToTree(summary);
    }

    private static Instant earlier(final Instant a, final Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant later(final Instant a, final Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
