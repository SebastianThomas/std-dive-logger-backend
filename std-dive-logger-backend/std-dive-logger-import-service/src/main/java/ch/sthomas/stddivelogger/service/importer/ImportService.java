package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.data.service.PendingImportDataService;
import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.divesoft.DivesoftReaderService;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class ImportService {
    private static final Duration PENDING_IMPORT_EXPIRY = Duration.ofHours(48);

    private final FitReaderService fitReaderService;
    private final UddfReaderService uddfReaderService;
    private final SubsurfaceXmlReaderService subsurfaceXmlReaderService;
    private final DivesoftReaderService divesoftReaderService;
    private final JsonReaderService jsonReaderService;
    private final PendingImportDataService pendingImportDataService;
    private final DiveService diveService;

    public ImportService(
            final FitReaderService fitReaderService,
            final UddfReaderService uddfReaderService,
            final SubsurfaceXmlReaderService subsurfaceXmlReaderService,
            final DivesoftReaderService divesoftReaderService,
            final JsonReaderService jsonReaderService,
            final PendingImportDataService pendingImportDataService,
            final DiveService diveService) {
        this.fitReaderService = fitReaderService;
        this.uddfReaderService = uddfReaderService;
        this.subsurfaceXmlReaderService = subsurfaceXmlReaderService;
        this.divesoftReaderService = divesoftReaderService;
        this.jsonReaderService = jsonReaderService;
        this.pendingImportDataService = pendingImportDataService;
        this.diveService = diveService;
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
            case XML_SUBSURFACE ->
                    subsurfaceXmlReaderService.parse(
                            user, Objects.requireNonNull(filename), inputStream);
            case JSON ->
                    Stream.of(
                            new ParsedImportResultStreaming(
                                    Stream.of(
                                            jsonReaderService.parse(
                                                    user,
                                                    Objects.requireNonNull(filename),
                                                    inputStream)),
                                    Stream.empty()));
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
            result = attach(user, attachToNumber, overrides, payload);
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
            final PendingImportPayload payload) {
        final var notes = Optional.ofNullable(overrides.notes()).orElse(payload.notes());
        SimplifiedDive result = null;
        for (final var profile : payload.profiles()) {
            result = diveService.addProfile(user, diveNumber, notes, profile);
        }
        return Objects.requireNonNull(result, "Pending import has no profiles to attach");
    }

    private SimplifiedDive createDive(
            final User user,
            final PendingImportEntity entity,
            final PendingImportCommitRequest overrides,
            final PendingImportPayload payload) {
        final var siteId = resolveSite(entity, overrides);
        final var diveIdentifier =
                Optional.ofNullable(overrides.diveIdentifier())
                        .or(() -> Optional.ofNullable(entity.getDiveIdentifierGuess()))
                        .orElse("Imported dive");
        final var notes = Optional.ofNullable(overrides.notes()).orElse(payload.notes());
        final var visibility =
                Optional.ofNullable(overrides.visibility()).orElse(payload.visibility());
        final var namedBuddies =
                Optional.ofNullable(overrides.namedBuddies()).orElse(payload.namedBuddies());
        final var diveNumber =
                overrides.diveNumber() != null
                        ? Optional.of(overrides.diveNumber())
                        : Optional.ofNullable(payload.diveNumberGuess()).map(DiveNumber::number);
        final var saveResult =
                diveService.saveDive(
                        user,
                        diveNumber,
                        diveIdentifier,
                        notes,
                        visibility,
                        payload.gasConsumption(),
                        payload.configuration(),
                        siteId,
                        payload.profiles(),
                        namedBuddies);
        if (saveResult.isException()) {
            throw saveResult.dbException();
        }
        return saveResult.value();
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
     * Reimports a single profile's raw measurements from its original source file, leaving every
     * other dive property untouched. Currently only supported for UDDF files.
     */
    public Dive reimportProfile(
            final User user,
            final long diveId,
            final long profileId,
            final int entry,
            final MultipartFile file) {
        final var filename = file.getOriginalFilename();
        final var fileType = UploadFileType.fromFilename(filename);
        if (fileType != UploadFileType.UDDF_SHEARWATER) {
            throw new IllegalArgumentException(
                    "Reimporting a profile is currently only supported for UDDF files, got: "
                            + filename);
        }
        try {
            return uddfReaderService.reimportProfile(
                    user, diveId, profileId, entry, file.getInputStream());
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read uploaded file " + filename, e);
        }
    }
}
