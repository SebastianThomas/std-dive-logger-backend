package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.data.service.PendingImportDataService;
import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportPreviewResult;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.NamedBuddy;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.ReimportSimilarityCheck;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.divesoft.DivesoftReaderService;
import ch.sthomas.stddivelogger.service.importer.dl7.Dl7ReaderService;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterDbReaderService;
import ch.sthomas.stddivelogger.service.importer.uddf.UddfReaderService;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class ImportService {
    private static final Duration PENDING_IMPORT_EXPIRY = Duration.ofHours(48);

    // Shearwater's own export formats carry a plain wall-clock reading with no timezone of their
    // own - every reader for these three sources parses it as if it were UTC (see e.g.
    // ShearwaterXmlReaderService.parseStartDate's own doc comment). See
    // correctForUnknownTimezone's doc comment for how that guess gets corrected once a real
    // dive-site location is known.
    private static final Set<PendingImportSource> SOURCES_WITH_UNKNOWN_TIMEZONE =
            EnumSet.of(
                    PendingImportSource.XML_SHEARWATER,
                    PendingImportSource.UDDF_SHEARWATER,
                    PendingImportSource.DL7_SHEARWATER,
                    PendingImportSource.DB_SHEARWATER);

    private final FitReaderService fitReaderService;
    private final UddfReaderService uddfReaderService;
    private final XmlReaderService xmlReaderService;
    private final DivesoftReaderService divesoftReaderService;
    private final JsonReaderService jsonReaderService;
    private final Dl7ReaderService dl7ReaderService;
    private final ShearwaterDbReaderService shearwaterDbReaderService;
    private final PendingImportDataService pendingImportDataService;
    private final DiveService diveService;
    private final LocationTimezoneResolver locationTimezoneResolver;

    public ImportService(
            final FitReaderService fitReaderService,
            final UddfReaderService uddfReaderService,
            final XmlReaderService xmlReaderService,
            final DivesoftReaderService divesoftReaderService,
            final JsonReaderService jsonReaderService,
            final Dl7ReaderService dl7ReaderService,
            final ShearwaterDbReaderService shearwaterDbReaderService,
            final PendingImportDataService pendingImportDataService,
            final DiveService diveService,
            final LocationTimezoneResolver locationTimezoneResolver) {
        this.fitReaderService = fitReaderService;
        this.uddfReaderService = uddfReaderService;
        this.xmlReaderService = xmlReaderService;
        this.divesoftReaderService = divesoftReaderService;
        this.jsonReaderService = jsonReaderService;
        this.dl7ReaderService = dl7ReaderService;
        this.shearwaterDbReaderService = shearwaterDbReaderService;
        this.pendingImportDataService = pendingImportDataService;
        this.diveService = diveService;
        this.locationTimezoneResolver = locationTimezoneResolver;
    }

    public StageImportResult stageDivesoft(final User user, final DivesoftImportRequest request) {
        return stage(user, Stream.of(divesoftReaderService.parse(user, request)));
    }

    public StageImportResult stageUpload(final User user, final List<MultipartFile> files) {
        return stage(user, files.stream().flatMap(file -> parseFile(user, file)));
    }

    private StageImportResult stage(
            final User user, final Stream<ParsedImportResultStreaming> parsed) {
        final var result =
                parsed.reduce(ParsedImportResultStreaming::concat)
                        .map(ParsedImportResultStreaming::toResult)
                        .orElse(new ParsedImportResultStreaming.Result(List.of(), List.of()));
        final var summaries =
                result.parsed().stream()
                        .map(p -> stageOne(user, p))
                        .map(PendingImportEntity::toSummary)
                        .toList();
        return new StageImportResult(summaries, result.errors());
    }

    private PendingImportEntity stageOne(final User user, final ParsedImport parsed) {
        return pendingImportDataService.save(
                user,
                parsed.source(),
                parsed.externalId(),
                parsed.filename(),
                parsed.diveIdentifierGuess(),
                parsed.siteNameGuess(),
                parsed.latitudeGuess(),
                parsed.longitudeGuess(),
                parsed.computerSerial(),
                parsed.startDate(),
                parsed.durationSeconds(),
                parsed.maxDepth(),
                parsed.payload());
    }

    private Stream<ParsedImportResultStreaming> parseFile(
            final User user, final MultipartFile file) {
        try {
            return parseFile(user, file.getOriginalFilename(), file.getInputStream());
        } catch (final IOException e) {
            return Stream.of(
                    new ParsedImportResultStreaming(
                            Stream.empty(),
                            Stream.of(
                                    MessageFormat.format(
                                            "Could not import the file {0}",
                                            file.getOriginalFilename()))));
        }
    }

    private Stream<ParsedImportResultStreaming> parseFile(
            final User user, final @Nullable String filename, final InputStream inputStream)
            throws IOException {
        final var fileType = UploadFileType.fromFilename(filename);
        return switch (fileType) {
            case null ->
                    throw new IllegalArgumentException(
                            MessageFormat.format(
                                    "Could not resolve file type for filename {0}, supported extensions: {1}",
                                    filename, UploadFileType.supportedExtensions()));
            case NONE ->
                    throw new IllegalArgumentException(
                            MessageFormat.format(
                                    "Could not resolve file type for filename {0}, supported extensions: {1}",
                                    filename, UploadFileType.supportedExtensions()));
            case UDDF_SHEARWATER ->
                    uddfReaderService.parse(user, Objects.requireNonNull(filename), inputStream);
            case FIT_GARMIN ->
                    Stream.of(
                            new ParsedImportResultStreaming(
                                    Stream.of(
                                            fitReaderService.parse(
                                                    user,
                                                    Objects.requireNonNull(filename),
                                                    inputStream)),
                                    Stream.empty()));
            case XML -> xmlReaderService.parse(user, Objects.requireNonNull(filename), inputStream);
            case JSON ->
                    Stream.of(
                            new ParsedImportResultStreaming(
                                    Stream.of(
                                            jsonReaderService.parse(
                                                    user,
                                                    Objects.requireNonNull(filename),
                                                    inputStream)),
                                    Stream.empty()));
            case DL7 ->
                    Stream.of(
                            new ParsedImportResultStreaming(
                                    Stream.of(
                                            dl7ReaderService.parse(
                                                    user,
                                                    Objects.requireNonNull(filename),
                                                    inputStream.readAllBytes())),
                                    Stream.empty()));
            case DB ->
                    shearwaterDbReaderService.parse(
                            user, Objects.requireNonNull(filename), inputStream);
        };
    }

    public List<PendingImportSummary> listPending(final User user) {
        return pendingImportDataService.findByUser(user).stream()
                .map(PendingImportEntity::toSummary)
                .toList();
    }

    @Transactional
    public SimplifiedDive commit(
            final User user,
            final long pendingImportId,
            final PendingImportCommitRequest overrides) {
        // Row-locked: without this, a double-click (or a frontend retry racing its own earlier
        // request) can have two concurrent commits of the same pending import both see it still
        // present and both create a dive from it. The lock makes the second commit wait for the
        // first to finish; by then the row is already deleted, so it correctly 404s here instead
        // of silently creating a duplicate.
        final var entity =
                pendingImportDataService
                        .findByIdAndUserForCommit(pendingImportId, user)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No pending import " + pendingImportId));
        final var payload = applyProfileTrims(entity.getPayload(), overrides.profileTrims());

        final var attachToNumber = resolveAttachTarget(user, overrides, payload);
        final SimplifiedDive result;
        if (attachToNumber != null) {
            result = attach(user, attachToNumber, overrides, payload, entity.getSource());
        } else {
            result = createDive(user, entity, overrides, payload);
        }
        pendingImportDataService.deleteById(pendingImportId);
        return result;
    }

    /**
     * Applies each requested trim (by profile index) to the staged payload's profiles before
     * they're used to create/attach a dive - the pre-commit counterpart of trimming an
     * already-saved profile. A no-op copy of the payload when there's nothing to trim.
     */
    private PendingImportPayload applyProfileTrims(
            final PendingImportPayload payload,
            final @Nullable List<PendingImportCommitRequest.ProfileTrim> trims) {
        if (trims == null || trims.isEmpty()) {
            return payload;
        }
        final var trimByIndex =
                trims.stream()
                        .collect(
                                Collectors.toMap(
                                        PendingImportCommitRequest.ProfileTrim::profileIndex,
                                        Function.identity()));
        final var profiles = payload.profiles();
        final var trimmedProfiles =
                IntStream.range(0, profiles.size())
                        .mapToObj(
                                i -> {
                                    final var trim = trimByIndex.get(i);
                                    return trim == null
                                            ? profiles.get(i)
                                            : profiles.get(i)
                                                    .trimmed(trim.trimStart(), trim.trimEnd());
                                })
                        .toList();
        return new PendingImportPayload(
                trimmedProfiles,
                payload.notes(),
                payload.visibility(),
                payload.gasConsumption(),
                payload.configuration(),
                payload.namedBuddies(),
                payload.diveNumberGuess());
    }

    /**
     * Full profile data (including measurements) for a staged-but-not-yet-committed import - the
     * pre-commit counterpart of fetching an already-saved dive, so the frontend can render the same
     * chart/trim UI against a pending import as it does for a real one. Deliberately not returned
     * at stage time itself (only the lightweight {@code PendingImportSummary} guess fields are) -
     * this is fetched separately, on demand, only when the user actually opens a preview.
     */
    @Transactional(readOnly = true)
    public List<DiveProfile> previewPending(final User user, final long pendingImportId) {
        final var entity =
                pendingImportDataService
                        .findByIdAndUser(pendingImportId, user)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No pending import " + pendingImportId));
        final var profiles = entity.getPayload().profiles();
        return IntStream.range(0, profiles.size())
                .mapToObj(
                        i -> {
                            final var upload = profiles.get(i);
                            final var computer =
                                    diveService
                                            .getDiveComputerById(user, upload.diveComputerId())
                                            .orElseThrow(
                                                    () ->
                                                            new NoSuchElementException(
                                                                    "Dive computer "
                                                                            + upload
                                                                                    .diveComputerId()
                                                                            + " not found"));
                            final var measurements = upload.measurements();
                            final var measurementsWithIds =
                                    IntStream.range(0, measurements.size())
                                            .mapToObj(
                                                    j ->
                                                            new DiveMeasurementWithId(
                                                                    measurements.get(j), j))
                                            .toList();
                            return new DiveProfile(
                                    i,
                                    computer,
                                    upload.start(),
                                    upload.end(),
                                    measurementsWithIds,
                                    true);
                        })
                .toList();
    }

    /**
     * Non-null when the import should be attached to an already-existing dive instead of creating a
     * new one - either because the frontend explicitly asked for it ({@code linkToExistingDiveId}),
     * or because the source file itself encoded a "+"-prefixed fractional dive number
     * (UDDF/Shearwater's auto-merge convention) and the frontend didn't override the dive number.
     */
    private @Nullable DiveNumber resolveAttachTarget(
            final User user,
            final PendingImportCommitRequest overrides,
            final PendingImportPayload payload) {
        if (overrides.linkToExistingDiveId() != null) {
            final var target =
                    diveService
                            .getDiveById(user, overrides.linkToExistingDiveId())
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Dive "
                                                            + overrides.linkToExistingDiveId()
                                                            + " not found"));
            return new DiveNumber(target.number());
        }
        final var effective =
                overrides.diveNumber() != null
                        ? new DiveNumber(overrides.diveNumber())
                        : payload.diveNumberGuess();
        return effective != null && effective.isFractional() ? effective : null;
    }

    private SimplifiedDive attach(
            final User user,
            final DiveNumber diveNumber,
            final PendingImportCommitRequest overrides,
            final PendingImportPayload payload,
            final PendingImportSource source) {
        final var notes = Optional.ofNullable(overrides.notes()).orElse(payload.notes());
        final var correctedPayload =
                correctPayloadForAttachTimezone(user, overrides, source, payload);
        SimplifiedDive result = null;
        for (final var profile : correctedPayload.profiles()) {
            result = diveService.addProfile(user, diveNumber, notes, profile);
        }
        return Objects.requireNonNull(result, "Pending import has no profiles to attach");
    }

    /**
     * Only correctable when the frontend explicitly linked to an existing dive ({@code
     * linkToExistingDiveId}) - that dive's own site is a real, already-known location. The other
     * way to reach {@code attach} (Shearwater/UDDF's "+"-prefixed fractional dive-number auto-merge
     * convention, resolved purely from a number with no dive fetched at all) has no site lookup
     * available at this point without adding one - left uncorrected, same as before this fix,
     * rather than guessing.
     */
    private PendingImportPayload correctPayloadForAttachTimezone(
            final User user,
            final PendingImportCommitRequest overrides,
            final PendingImportSource source,
            final PendingImportPayload payload) {
        if (overrides.linkToExistingDiveId() == null) {
            return payload;
        }
        return diveService
                .getDiveById(user, overrides.linkToExistingDiveId())
                .map(Dive::site)
                .map(site -> correctForUnknownTimezone(source, payload, site))
                .orElse(payload);
    }

    private SimplifiedDive createDive(
            final User user,
            final PendingImportEntity entity,
            final PendingImportCommitRequest overrides,
            final PendingImportPayload payload) {
        final var siteId = resolveSite(entity, overrides);
        final var site = diveService.getSiteById(siteId).orElseThrow();
        final var correctedPayload = correctForUnknownTimezone(entity.getSource(), payload, site);
        final var diveIdentifier =
                Optional.ofNullable(overrides.diveIdentifier())
                        .or(() -> Optional.ofNullable(entity.getDiveIdentifierGuess()))
                        .orElse("Imported dive");
        final var notes = Optional.ofNullable(overrides.notes()).orElse(correctedPayload.notes());
        final var visibility =
                Optional.ofNullable(overrides.visibility()).orElse(correctedPayload.visibility());
        final var namedBuddies =
                Optional.ofNullable(overrides.namedBuddies())
                        .orElse(correctedPayload.namedBuddies());
        final var diveNumber =
                overrides.diveNumber() != null
                        ? Optional.of(overrides.diveNumber())
                        : Optional.ofNullable(correctedPayload.diveNumberGuess())
                                .map(DiveNumber::number);
        final var saveResult =
                diveService.saveDive(
                        user,
                        diveNumber,
                        diveIdentifier,
                        notes,
                        visibility,
                        correctedPayload.gasConsumption(),
                        correctedPayload.configuration(),
                        siteId,
                        correctedPayload.profiles(),
                        namedBuddies);
        if (saveResult.isException()) {
            throw saveResult.dbException();
        }
        return saveResult.value();
    }

    /**
     * Shearwater's own export formats (native XML, UDDF, DL7) carry no timezone/GPS of their own
     * (see {@link #SOURCES_WITH_UNKNOWN_TIMEZONE}'s doc comment) - their readers parse the raw
     * wall-clock reading as if it were UTC, which is only actually correct for a diver in UTC+0.
     * Once a real dive-site location is known (only ever true at commit time for these formats),
     * this looks up the site's real timezone and re-interprets that same wall-clock reading in it,
     * shifting every measurement in every profile uniformly. A no-op for any other source, or in
     * the (in practice essentially unreachable - the underlying data covers the whole globe via
     * nautical offset zones, see {@code LocationTimezoneResolverTest}) case that the site's
     * coordinates don't resolve to any zone at all - the original UTC-labeled guess is kept rather
     * than left partially corrected.
     */
    private PendingImportPayload correctForUnknownTimezone(
            final PendingImportSource source,
            final PendingImportPayload payload,
            final @Nullable DiveSite site) {
        if (!SOURCES_WITH_UNKNOWN_TIMEZONE.contains(source) || site == null) {
            return payload;
        }
        final var zone = locationTimezoneResolver.resolve(site.latitude(), site.longitude());
        if (zone.isEmpty()) {
            return payload;
        }
        final var correctedProfiles =
                payload.profiles().stream()
                        .map(
                                profile ->
                                        profile.shifted(
                                                timezoneOffset(profile.start(), zone.get())))
                        .toList();
        return new PendingImportPayload(
                correctedProfiles,
                payload.notes(),
                payload.visibility(),
                payload.gasConsumption(),
                payload.configuration(),
                payload.namedBuddies(),
                payload.diveNumberGuess());
    }

    /**
     * The shift needed to move an {@link Instant} that was naively parsed as "this wall-clock
     * reading, in UTC" to what it should actually be: the same wall-clock reading, in {@code zone}.
     */
    private static Duration timezoneOffset(final Instant assumedUtc, final ZoneId zone) {
        final var wallClock = LocalDateTime.ofInstant(assumedUtc, ZoneOffset.UTC);
        return Duration.between(assumedUtc, wallClock.atZone(zone).toInstant());
    }

    /**
     * Site override precedence: explicit existing-site id, explicit new name+location, then the
     * guess captured at stage time (get-or-create when the guess has coordinates, exact-name lookup
     * when it doesn't - matching each reader's original per-source resolution rule).
     */
    private long resolveSite(
            final PendingImportEntity entity, final PendingImportCommitRequest overrides) {
        if (overrides.diveSiteId() != null) {
            return diveService.getSiteById(overrides.diveSiteId()).orElseThrow().id();
        }
        if (overrides.newSiteName() != null && overrides.newSiteLocation() != null) {
            return diveService
                    .getOrCreateDiveSite(overrides.newSiteName(), overrides.newSiteLocation())
                    .id();
        }
        final var nameGuess = entity.getSiteNameGuess();
        final var lat = entity.getLatitudeGuess();
        final var lon = entity.getLongitudeGuess();
        if (nameGuess != null && lat != null && lon != null) {
            return diveService.getOrCreateDiveSite(nameGuess, new Location(lat, lon)).id();
        }
        if (nameGuess != null) {
            return diveService
                    .getSiteByName(nameGuess)
                    .orElseThrow(() -> new MissingDiveSiteValueException(nameGuess))
                    .id();
        }
        throw new MissingDiveSiteValueException("");
    }

    public void discard(final User user, final long pendingImportId) {
        pendingImportDataService
                .findByIdAndUser(pendingImportId, user)
                .orElseThrow(
                        () -> new NoSuchElementException("No pending import " + pendingImportId));
        pendingImportDataService.deleteById(pendingImportId);
    }

    public int expireOldPendingImports() {
        return pendingImportDataService.deleteOlderThan(Instant.now().minus(PENDING_IMPORT_EXPIRY));
    }

    /**
     * Phase 1 of "reimport in place": parses the uploaded file with the same per-format dispatch as
     * a normal upload (any supported format, not just UDDF), checks it against {@code
     * ReimportSimilarityCheck} using the target profile's own current data (throws if it doesn't
     * look like the same dive - see that class's doc comment for why), computes any field-level
     * conflicts between the dive's current notes/visibility/namedBuddies/gasConsumption and what
     * the reimport would bring in, and stages the parsed result as a pending import (tagged with
     * the reimport target) without changing anything yet. If {@code conflicts.hasAny()} is false on
     * the result, the caller can commit immediately with an all-null {@link ReimportResolution};
     * otherwise the caller must resolve each conflicting field first.
     */
    public ReimportPreviewResult previewReimportProfile(
            final User user,
            final long diveId,
            final long profileId,
            final int entry,
            final MultipartFile file) {
        final var filename = Objects.requireNonNull(file.getOriginalFilename());
        final ParsedImportResultStreaming.Result result;
        try {
            result =
                    parseFile(user, filename, file.getInputStream())
                            .reduce(ParsedImportResultStreaming::concat)
                            .map(ParsedImportResultStreaming::toResult)
                            .orElse(new ParsedImportResultStreaming.Result(List.of(), List.of()));
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read uploaded file " + filename, e);
        }
        if (!result.errors().isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not parse " + filename + ": " + String.join("; ", result.errors()));
        }
        if (entry < 0 || entry >= result.parsed().size()) {
            throw new IllegalArgumentException(
                    "Entry "
                            + entry
                            + " not found - "
                            + filename
                            + " has "
                            + result.parsed().size()
                            + " dive(s)");
        }
        final var parsedImport = result.parsed().get(entry);
        final var reimportedProfile = parsedImport.payload().profiles().getFirst();

        final var context = diveService.getReimportPreviewContext(user, diveId, profileId);
        final var mismatch =
                ReimportSimilarityCheck.checkSameDive(
                        context.profileStart(),
                        context.profileEnd(),
                        context.profileMeasurements(),
                        reimportedProfile.start(),
                        reimportedProfile.end(),
                        reimportedProfile.measurements());
        if (mismatch.isPresent()) {
            throw new IllegalArgumentException(
                    "The uploaded file doesn't look like the same dive as the profile you're "
                            + "replacing ("
                            + mismatch.get()
                            + "). If this is meant to be a different dive computer's own "
                            + "recording of the same dive, use \"merge profiles\" instead of "
                            + "reimport.");
        }

        final var conflicts =
                ReimportFieldMerge.computeConflicts(
                        context.dive().notes(),
                        context.dive().visibility(),
                        context.dive().namedBuddies().stream().map(NamedBuddy::name).toList(),
                        context.dive().gasConsumption(),
                        parsedImport.payload().notes(),
                        parsedImport.payload().visibility(),
                        parsedImport.payload().namedBuddies(),
                        parsedImport.payload().gasConsumption());

        final var pendingImport = stageOne(user, parsedImport);
        pendingImportDataService.markReimportTarget(pendingImport.getId(), diveId, profileId);
        return new ReimportPreviewResult(pendingImport.getId(), conflicts);
    }

    /**
     * Phase 2: replaces the target profile's measurements (re-running the similarity check as a
     * defense-in-depth double check) and applies the given resolution for whichever fields {@link
     * #previewReimportProfile} flagged as conflicting - a null choice for a field that wasn't
     * actually conflicting is fine (nothing to resolve there); a null choice for one that was
     * throws.
     */
    public Dive commitReimportProfile(
            final User user,
            final long diveId,
            final long profileId,
            final long pendingImportId,
            final ReimportResolution resolution) {
        final var pendingImport =
                pendingImportDataService
                        .findByIdAndUserForCommit(pendingImportId, user)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No pending import " + pendingImportId));
        final var targetDiveId = pendingImport.getReimportTargetDiveId();
        final var targetProfileId = pendingImport.getReimportTargetProfileId();
        if (targetDiveId == null || targetProfileId == null) {
            throw new IllegalArgumentException(
                    "Pending import " + pendingImportId + " is not a staged reimport");
        }
        if (targetDiveId != diveId || targetProfileId != profileId) {
            throw new IllegalArgumentException(
                    "Pending import "
                            + pendingImportId
                            + " was staged for dive "
                            + targetDiveId
                            + "/profile "
                            + targetProfileId
                            + ", not dive "
                            + diveId
                            + "/profile "
                            + profileId);
        }
        final var payload = pendingImport.getPayload();
        final var reimportedProfile = payload.profiles().getFirst();

        diveService.reimportProfile(
                user,
                diveId,
                profileId,
                reimportedProfile.measurements(),
                reimportedProfile.start(),
                reimportedProfile.end());

        final var context = diveService.getReimportPreviewContext(user, diveId, profileId);
        final var existingBuddyNames =
                context.dive().namedBuddies().stream().map(NamedBuddy::name).toList();
        final var updated =
                diveService.applyReimportResolution(
                        user,
                        diveId,
                        ReimportFieldMerge.resolveNotes(
                                context.dive().notes(), payload.notes(), resolution.notes()),
                        ReimportFieldMerge.resolveVisibility(
                                context.dive().visibility(),
                                payload.visibility(),
                                resolution.visibility()),
                        ReimportFieldMerge.resolveNamedBuddies(
                                existingBuddyNames,
                                payload.namedBuddies(),
                                resolution.namedBuddies()),
                        ReimportFieldMerge.resolveGasConsumption(
                                context.dive().gasConsumption(),
                                payload.gasConsumption(),
                                resolution.gasConsumption()));
        pendingImportDataService.deleteById(pendingImportId);
        return updated;
    }
}
