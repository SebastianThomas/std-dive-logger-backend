package ch.sthomas.stddivelogger.service.importer;

import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.BUDDIES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.CONFIGURATION;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.DIVE_IDENTIFIER;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.DIVE_NUMBER;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.GAS_CONSUMPTION;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.NOTES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.SITE;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.VISIBILITY;

import ch.sthomas.stddivelogger.data.location.LocationTimezoneResolver;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.ImportFileDataService;
import ch.sthomas.stddivelogger.data.service.PendingImportDataService;
import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportConflicts;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportPreviewResult;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.NamedBuddy;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.ProfileMeasurementMerge;
import ch.sthomas.stddivelogger.model.dive.profile.ReimportSimilarityCheck;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.entity.DiveImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.ImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.ImportFileService;
import ch.sthomas.stddivelogger.service.importer.divesoft.DivesoftReaderService;
import ch.sthomas.stddivelogger.service.importer.dl7.Dl7ReaderService;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterDbReaderService;
import ch.sthomas.stddivelogger.service.importer.uddf.UddfReaderService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);
    private static final Duration PENDING_IMPORT_EXPIRY = Duration.ofHours(48);
    // The database keeps microseconds; a parsed start may carry more.
    private static final Duration SAME_START = Duration.ofMillis(1);

    // Every Shearwater export carries the dive computer's plain local wall-clock reading with no
    // timezone of its own - the UDDF even suffixes it with a "Z" it doesn't mean (its own export
    // timestamp in the same file *is* real UTC, the dive's is not). Confirmed against a Suunto
    // recording of the same dive, which carries a real offset: Shearwater "10:30:52" (XML and UDDF)
    // vs. Suunto "10:30:54+02:00". Each reader parses the reading as if it were UTC; see
    // correctForUnknownTimezone for how that is corrected once a real dive-site location is known.
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
    private final DiveDataService diveDataService;
    private final LocationTimezoneResolver locationTimezoneResolver;
    private final ImportFileService importFileService;
    private final ImportFileDataService importFileDataService;

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
            final DiveDataService diveDataService,
            final LocationTimezoneResolver locationTimezoneResolver,
            final ImportFileService importFileService,
            final ImportFileDataService importFileDataService) {
        this.fitReaderService = fitReaderService;
        this.uddfReaderService = uddfReaderService;
        this.xmlReaderService = xmlReaderService;
        this.divesoftReaderService = divesoftReaderService;
        this.jsonReaderService = jsonReaderService;
        this.dl7ReaderService = dl7ReaderService;
        this.shearwaterDbReaderService = shearwaterDbReaderService;
        this.pendingImportDataService = pendingImportDataService;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
        this.locationTimezoneResolver = locationTimezoneResolver;
        this.importFileService = importFileService;
        this.importFileDataService = importFileDataService;
    }

    /**
     * Stages dives fetched from the Divesoft API. {@code rawDives} - the API's own JSON, one per
     * dive, same order - is what an account that keeps its files stores, one file per dive.
     */
    public StageImportResult stageDivesoft(
            final User user, final DivesoftImportRequest request, final List<byte[]> rawDives) {
        final var staged = new ArrayList<PendingImportSummary>();
        final var errors = new ArrayList<String>();
        final var dives = request.dives();
        for (var i = 0; i < dives.size(); i++) {
            final var result =
                    divesoftReaderService
                            .parse(user, new DivesoftImportRequest(List.of(dives.get(i))))
                            .toResult();
            errors.addAll(result.errors());
            final var fileId =
                    i < rawDives.size()
                            ? keepFile(
                                    user,
                                    divesoftFilename(result.parsed()),
                                    "application/json",
                                    rawDives.get(i),
                                    result.parsed(),
                                    divesoftMetadata(result.parsed()))
                            : null;
            result.parsed().forEach(p -> staged.add(stageOne(user, p, fileId).toSummary()));
        }
        return new StageImportResult(staged, errors);
    }

    public StageImportResult stageUpload(final User user, final List<MultipartFile> files) {
        final var staged = new ArrayList<PendingImportSummary>();
        final var errors = new ArrayList<String>();
        for (final var file : files) {
            final var filename = file.getOriginalFilename();
            final byte[] bytes;
            final ParsedImportResultStreaming.Result result;
            try {
                bytes = file.getBytes();
                result = parse(user, filename, bytes);
            } catch (final IOException e) {
                errors.add(MessageFormat.format("Could not import the file {0}", filename));
                continue;
            }
            errors.addAll(result.errors());
            final var fileId =
                    keepFile(user, filename, file.getContentType(), bytes, result.parsed(), null);
            result.parsed().forEach(p -> staged.add(stageOne(user, p, fileId).toSummary()));
        }
        return new StageImportResult(staged, errors);
    }

    private PendingImportEntity stageOne(
            final User user, final ParsedImport parsed, final @Nullable Long importFileId) {
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
                parsed.payload(),
                importFileId,
                importFileId == null ? null : parsed.locator());
    }

    /** Stores the upload for an account that keeps its files; null otherwise. */
    private @Nullable Long keepFile(
            final User user,
            final @Nullable String filename,
            final @Nullable String contentType,
            final byte[] bytes,
            final List<ParsedImport> parsed,
            final @Nullable JsonNode metadata) {
        if (parsed.isEmpty()) {
            return null;
        }
        return importFileService.storeIfKept(
                user,
                parsed.getFirst().source(),
                filename,
                contentType,
                bytes,
                parsed.size(),
                parsed.stream().mapToInt(p -> p.payload().profiles().size()).sum(),
                metadata);
    }

    private static String divesoftFilename(final List<ParsedImport> parsed) {
        final var id = parsed.isEmpty() ? null : parsed.getFirst().externalId();
        return "divesoft-" + Objects.requireNonNullElse(id, "dive") + ".json";
    }

    private static @Nullable JsonNode divesoftMetadata(final List<ParsedImport> parsed) {
        if (parsed.isEmpty()) {
            return null;
        }
        final var metadata = new LinkedHashMap<String, String>();
        final var id = parsed.getFirst().externalId();
        if (id != null) {
            metadata.put("divesoftId", id);
        }
        metadata.put("fetchedAt", Instant.now().toString());
        return ImportedFieldValues.JSON.valueToTree(metadata);
    }

    private ParsedImportResultStreaming.Result parse(
            final User user, final @Nullable String filename, final byte[] bytes)
            throws IOException {
        return parseFile(user, filename, new ByteArrayInputStream(bytes))
                .reduce(ParsedImportResultStreaming::concat)
                .map(ParsedImportResultStreaming::toResult)
                .orElse(new ParsedImportResultStreaming.Result(List.of(), List.of()));
    }

    /** Parses a stored file again - the same dispatch as its upload; Divesoft from its API JSON. */
    public ParsedImportResultStreaming.Result parseStored(
            final User user, final ImportFileEntity file, final byte[] bytes) throws IOException {
        if (file.getSource() == PendingImportSource.DIVESOFT) {
            final var dive =
                    ImportedFieldValues.JSON.readValue(bytes, DivesoftDiveDetailResponse.class);
            return divesoftReaderService
                    .parse(user, new DivesoftImportRequest(List.of(dive)))
                    .toResult();
        }
        return parse(user, dispatchFilename(file), bytes);
    }

    private static String dispatchFilename(final ImportFileEntity file) {
        final var name = file.getOriginalFilename();
        final var type = UploadFileType.fromFilename(name);
        if (name != null && type != null && type != UploadFileType.NONE) {
            return name;
        }
        final var extension =
                switch (file.getSource()) {
                    case UDDF_SHEARWATER -> "uddf";
                    case FIT_GARMIN, FIT_SUUNTO -> "fit";
                    case XML_SUBSURFACE, XML_SHEARWATER -> "xml";
                    case JSON_SUUNTO, DIVESOFT -> "json";
                    case DL7_SHEARWATER -> "zxu";
                    case DB_SHEARWATER -> "db";
                };
        return "stored-file-" + file.getId() + "." + extension;
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

    /**
     * A commit's result, plus what provenance needs: the payload as saved (site timezone applied)
     * and the dive-level values taken from the file.
     */
    private record Committed(
            SimplifiedDive dive,
            PendingImportPayload saved,
            Map<ImportedDiveField, JsonNode> taken) {}

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
        final var committed =
                attachToNumber != null
                        ? attach(user, attachToNumber, overrides, payload, entity.getSource())
                        : createDive(user, entity, overrides, payload);
        recordProvenance(entity, payload, committed, overrides.profileTrims());
        pendingImportDataService.deleteById(pendingImportId);
        return committed.dive();
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
        final var trimByIndex = trimsByIndex(trims);
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

    private static Map<Integer, PendingImportCommitRequest.ProfileTrim> trimsByIndex(
            final @Nullable List<PendingImportCommitRequest.ProfileTrim> trims) {
        if (trims == null) {
            return Map.of();
        }
        return trims.stream()
                .collect(
                        Collectors.toMap(
                                PendingImportCommitRequest.ProfileTrim::profileIndex,
                                Function.identity(),
                                (a, b) -> b));
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

    private Committed attach(
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
        final var taken = new EnumMap<ImportedDiveField, JsonNode>(ImportedDiveField.class);
        if (overrides.notes() == null) {
            ImportedFieldValues.putTaken(taken, NOTES, payload.notes());
        }
        return new Committed(
                Objects.requireNonNull(result, "Pending import has no profiles to attach"),
                correctedPayload,
                taken);
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

    private Committed createDive(
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
        final var diveNumberGuess = correctedPayload.diveNumberGuess();
        final var diveNumber =
                overrides.diveNumber() != null
                        ? Optional.of(overrides.diveNumber())
                        : Optional.ofNullable(diveNumberGuess).map(DiveNumber::number);
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

        final var taken = new EnumMap<ImportedDiveField, JsonNode>(ImportedDiveField.class);
        if (overrides.notes() == null) {
            ImportedFieldValues.putTaken(taken, NOTES, correctedPayload.notes());
        }
        if (overrides.visibility() == null) {
            ImportedFieldValues.putTaken(taken, VISIBILITY, correctedPayload.visibility());
        }
        if (overrides.namedBuddies() == null) {
            ImportedFieldValues.putTaken(taken, BUDDIES, correctedPayload.namedBuddies());
        }
        ImportedFieldValues.putTaken(taken, GAS_CONSUMPTION, correctedPayload.gasConsumption());
        if (hasContent(correctedPayload.configuration())) {
            ImportedFieldValues.putTaken(taken, CONFIGURATION, correctedPayload.configuration());
        }
        if (overrides.diveNumber() == null && diveNumberGuess != null) {
            ImportedFieldValues.putTaken(taken, DIVE_NUMBER, diveNumberGuess.number());
        }
        if (overrides.diveIdentifier() == null) {
            ImportedFieldValues.putTaken(taken, DIVE_IDENTIFIER, entity.getDiveIdentifierGuess());
        }
        final var siteNameGuess = entity.getSiteNameGuess();
        if (overrides.diveSiteId() == null
                && overrides.newSiteName() == null
                && siteNameGuess != null) {
            ImportedFieldValues.putTaken(
                    taken,
                    SITE,
                    new ImportedFieldValues.SiteValue(
                            siteNameGuess, entity.getLatitudeGuess(), entity.getLongitudeGuess()));
        }
        return new Committed(saveResult.value(), correctedPayload, taken);
    }

    private static boolean hasContent(final DiveConfiguration configuration) {
        return !configuration.cylinders().isEmpty()
                || configuration.base() != null
                || configuration.weight() != null;
    }

    /**
     * Records pre-commit trims, and - for an account that keeps its files - which profiles and
     * dive-level values came from the stored file.
     */
    private void recordProvenance(
            final PendingImportEntity entity,
            final PendingImportPayload trimmed,
            final Committed committed,
            final @Nullable List<PendingImportCommitRequest.ProfileTrim> trims) {
        final var fileId = entity.getImportFileId();
        final var trimByIndex = trimsByIndex(trims);
        if (fileId == null && trimByIndex.isEmpty()) {
            return;
        }
        final var diveId = committed.dive().id();
        final var untrimmed = entity.getPayload();
        final var saved = committed.saved();
        final var keys = diveDataService.findProfileKeys(diveId);
        final var siteId =
                Optional.ofNullable(committed.dive().site()).map(DiveSite::id).orElse(null);
        final var version = ImportParserVersions.current(entity.getSource());
        for (var i = 0; i < saved.profiles().size(); i++) {
            final var upload = saved.profiles().get(i);
            final var profileId = profileIdOf(keys, upload);
            if (profileId == null) {
                logger.warn(
                        "Profile {} of pending import {} not found on dive {}",
                        i,
                        entity.getId(),
                        diveId);
                continue;
            }
            final var original = untrimmed.profiles().get(i);
            final var activeStart =
                    ReimportSimilarityCheck.activeStart(original.measurements(), original.start());
            final var trim = trimByIndex.get(i);
            if (trim != null) {
                diveDataService.recordProfileTrim(
                        profileId,
                        between(activeStart, trim.trimStart()),
                        between(activeStart, trim.trimEnd()));
            }
            if (fileId != null) {
                // The uniform shift the site's timezone applied to every sample of this profile.
                final var timezoneShift =
                        Duration.between(trimmed.profiles().get(i).start(), upload.start());
                importFileDataService.linkProfile(
                        profileId,
                        fileId,
                        entity.getImportLocator().withProfile(i),
                        version,
                        activeStart.plus(timezoneShift),
                        siteId);
                diveDataService.setImportFilesComplete(profileId, true);
            }
        }
        if (fileId != null) {
            importFileDataService.linkDive(
                    diveId, fileId, entity.getImportLocator().dive(), version, committed.taken());
        }
    }

    private static @Nullable Duration between(final Instant from, final @Nullable Instant to) {
        return to == null ? null : Duration.between(from, to);
    }

    private static @Nullable Long profileIdOf(
            final List<DiveDataService.ProfileKey> keys, final DiveProfileUpload upload) {
        return keys.stream()
                .filter(k -> k.computerId() == upload.diveComputerId())
                .filter(
                        k ->
                                Duration.between(k.start(), upload.start())
                                                .abs()
                                                .compareTo(SAME_START)
                                        <= 0)
                .map(DiveDataService.ProfileKey::profileId)
                .findFirst()
                .orElse(null);
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
     * The parsed dive with its clock read in {@code site}'s timezone, where the source has none.
     */
    public ParsedImport correctedForSite(final ParsedImport parsed, final @Nullable DiveSite site) {
        final var payload = correctForUnknownTimezone(parsed.source(), parsed.payload(), site);
        if (payload == parsed.payload()) {
            return parsed;
        }
        final var startDate =
                parsed.startDate() == null || payload.profiles().isEmpty()
                        ? parsed.startDate()
                        : payload.profiles().getFirst().start();
        return parsed.withPayload(payload, startDate);
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
        final byte[] bytes;
        final ParsedImportResultStreaming.Result result;
        try {
            bytes = file.getBytes();
            result = parse(user, filename, bytes);
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
        final var fileId =
                keepFile(user, filename, file.getContentType(), bytes, result.parsed(), null);
        return previewReimport(user, diveId, profileId, result.parsed().get(entry), fileId);
    }

    /**
     * {@link #previewReimportProfile} from a file the account already stored - the dive in it is
     * the one this dive was linked to before, or its only dive.
     */
    public ReimportPreviewResult previewReimportProfileFromStoredFile(
            final User user, final long diveId, final long profileId, final long importFileId) {
        final var file = importFileService.getOwned(user, importFileId);
        final ParsedImportResultStreaming.Result result;
        try {
            result = parseStored(user, file, importFileService.read(file));
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read stored file " + importFileId, e);
        }
        final var knownDives =
                Stream.concat(
                                importFileDataService.findDiveLinks(diveId).stream()
                                        .filter(l -> l.getFile().getId() == importFileId)
                                        .map(DiveImportFileEntity::getLocator),
                                importFileDataService.findProfileLinksOfDive(diveId).stream()
                                        .filter(l -> l.getFile().getId() == importFileId)
                                        .map(DiveProfileImportFileEntity::getLocator))
                        .toList();
        final var parsed =
                result.parsed().stream()
                        .filter(p -> knownDives.stream().anyMatch(l -> l.sameDive(p.locator())))
                        .findFirst()
                        .or(
                                () ->
                                        result.parsed().size() == 1
                                                ? Optional.of(result.parsed().getFirst())
                                                : Optional.empty())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Could not tell which dive of the stored file"
                                                        + " belongs to this dive"));
        return previewReimport(user, diveId, profileId, parsed, file.getId());
    }

    private ReimportPreviewResult previewReimport(
            final User user,
            final long diveId,
            final long profileId,
            final ParsedImport chosen,
            final @Nullable Long importFileId) {
        final var context = diveService.getReimportPreviewContext(user, diveId, profileId);
        // The target dive's site is a real location, so a timezone-less source is corrected
        // exactly like at a normal commit - otherwise refining a correctly placed dive with the
        // same dive's Shearwater export would read as a whole-hour clock offset.
        final var parsedImport = correctedForSite(chosen, context.dive().site());
        final var reimportedProfile = parsedImport.payload().profiles().getFirst();

        // Throws on a genuine "different dive"; returns a whole-hour offset when the clocks only
        // differ by a timezone artefact, which becomes a resolvable conflict instead.
        final var clockOffset =
                ReimportSimilarityCheck.requirePlausibleReimport(
                        context.profileStart(),
                        context.profileEnd(),
                        context.profileMeasurements(),
                        reimportedProfile.start(),
                        reimportedProfile.end(),
                        reimportedProfile.measurements());
        final var clockOffsetConflict =
                clockOffset
                        .map(
                                off ->
                                        new ReimportConflicts.ClockOffset(
                                                context.profileStart(),
                                                reimportedProfile.start(),
                                                off.toMinutes()))
                        .orElse(null);

        final var conflicts =
                ReimportFieldMerge.computeConflicts(
                        clockOffsetConflict,
                        context.dive().notes(),
                        context.dive().visibility(),
                        context.dive().namedBuddies().stream().map(NamedBuddy::name).toList(),
                        context.dive().gasConsumption(),
                        parsedImport.payload().notes(),
                        parsedImport.payload().visibility(),
                        parsedImport.payload().namedBuddies(),
                        parsedImport.payload().gasConsumption());

        final var pendingImport = stageOne(user, parsedImport, importFileId);
        pendingImportDataService.markReimportTarget(pendingImport.getId(), diveId, profileId);
        return new ReimportPreviewResult(pendingImport.getId(), conflicts);
    }

    /**
     * Phase 2: merges the file into the target profile's measurements (see {@link
     * ProfileMeasurementMerge}; re-running the similarity check as a defense-in-depth double check)
     * and applies the given resolution for whichever fields {@link #previewReimportProfile} flagged
     * as conflicting - a null choice for a field that wasn't actually conflicting is fine (nothing
     * to resolve there); a null choice for one that was throws.
     */
    @Transactional
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
        final var context = diveService.getReimportPreviewContext(user, diveId, profileId);
        final var clock =
                resolveReimportClock(
                        context, payload.profiles().getFirst(), resolution.startClock());
        final var reimported = clock.reimported();
        // Merged, not replaced: the refined profile keeps every sample and field either file has,
        // and comes out the same whichever of the two was imported first.
        final var existing =
                context.profileMeasurements().stream()
                        .map(m -> m.shifted(clock.existingShift()))
                        .toList();
        final var existingStart = context.profileStart().plus(clock.existingShift());
        final var existingEnd = context.profileEnd().plus(clock.existingShift());
        diveService.reimportProfile(
                user,
                diveId,
                profileId,
                ProfileMeasurementMerge.merge(existing, reimported.measurements()),
                existingStart.isBefore(reimported.start()) ? existingStart : reimported.start(),
                existingEnd.isAfter(reimported.end()) ? existingEnd : reimported.end(),
                reimported.decoSettings());

        final var existingBuddyNames =
                context.dive().namedBuddies().stream().map(NamedBuddy::name).toList();
        final var notes =
                ReimportFieldMerge.resolveNotes(
                        context.dive().notes(), payload.notes(), resolution.notes());
        final var visibility =
                ReimportFieldMerge.resolveVisibility(
                        context.dive().visibility(), payload.visibility(), resolution.visibility());
        final var namedBuddies =
                ReimportFieldMerge.resolveNamedBuddies(
                        existingBuddyNames, payload.namedBuddies(), resolution.namedBuddies());
        final var gasConsumption =
                ReimportFieldMerge.resolveGasConsumption(
                        context.dive().gasConsumption(),
                        payload.gasConsumption(),
                        resolution.gasConsumption());
        final var updated =
                diveService.applyReimportResolution(
                        user, diveId, notes, visibility, namedBuddies, gasConsumption);

        final var taken = new EnumMap<ImportedDiveField, JsonNode>(ImportedDiveField.class);
        if (notes != null) {
            ImportedFieldValues.putTaken(taken, NOTES, payload.notes());
        }
        if (visibility != null) {
            ImportedFieldValues.putTaken(taken, VISIBILITY, payload.visibility());
        }
        if (namedBuddies != null) {
            ImportedFieldValues.putTaken(taken, BUDDIES, payload.namedBuddies());
        }
        if (gasConsumption != null) {
            ImportedFieldValues.putTaken(taken, GAS_CONSUMPTION, payload.gasConsumption());
        }
        recordReimportProvenance(pendingImport, diveId, profileId, context.dive().site(), taken);
        pendingImportDataService.deleteById(pendingImportId);
        return updated;
    }

    private void recordReimportProvenance(
            final PendingImportEntity pendingImport,
            final long diveId,
            final long profileId,
            final @Nullable DiveSite site,
            final Map<ImportedDiveField, JsonNode> taken) {
        final var fileId = pendingImport.getImportFileId();
        if (fileId == null) {
            // Samples from a file that isn't kept: re-processing may only add to this profile now.
            diveDataService.setImportFilesComplete(profileId, false);
            return;
        }
        final var version = ImportParserVersions.current(pendingImport.getSource());
        final var upload = pendingImport.getPayload().profiles().getFirst();
        importFileDataService.linkProfile(
                profileId,
                fileId,
                pendingImport.getImportLocator().withProfile(0),
                version,
                ReimportSimilarityCheck.activeStart(upload.measurements(), upload.start()),
                site == null ? null : site.id());
        importFileDataService.linkDive(
                diveId, fileId, pendingImport.getImportLocator().dive(), version, taken);
    }

    /**
     * Both recordings on one clock: {@code reimported} as the file to merge in, and the shift the
     * existing profile needs (non-zero only when the diver adopted the file's clock). When the
     * clocks are a whole number of hours apart (a UTC vs. local-zone artefact - {@link
     * ReimportConflicts.ClockOffset}), EXISTING re-aligns the parsed data onto the dive's current
     * clock, NEW keeps the file's clock; a missing choice for a real offset is an error.
     */
    private record ClockResolution(DiveProfileUpload reimported, Duration existingShift) {}

    private static ClockResolution resolveReimportClock(
            final DiveDataService.ReimportPreviewContext context,
            final DiveProfileUpload reimported,
            final ReimportResolution.@Nullable Choice choice) {
        final var offset =
                ReimportSimilarityCheck.requirePlausibleReimport(
                        context.profileStart(),
                        context.profileEnd(),
                        context.profileMeasurements(),
                        reimported.start(),
                        reimported.end(),
                        reimported.measurements());
        final var retainExistingClock =
                Duration.between(
                        ReimportSimilarityCheck.activeStart(
                                reimported.measurements(), reimported.start()),
                        ReimportSimilarityCheck.activeStart(
                                context.profileMeasurements(), context.profileStart()));
        if (offset.isEmpty()) {
            return new ClockResolution(reimported.shifted(retainExistingClock), Duration.ZERO);
        }
        if (choice == null) {
            throw new ch.sthomas.stddivelogger.model.exception.ReimportClockConflictException(
                    new ReimportConflicts.ClockOffset(
                            context.profileStart(), reimported.start(), offset.get().toMinutes()));
        }
        return switch (choice) {
            case EXISTING ->
                    new ClockResolution(reimported.shifted(retainExistingClock), Duration.ZERO);
            case NEW -> new ClockResolution(reimported, retainExistingClock.negated());
        };
    }
}
