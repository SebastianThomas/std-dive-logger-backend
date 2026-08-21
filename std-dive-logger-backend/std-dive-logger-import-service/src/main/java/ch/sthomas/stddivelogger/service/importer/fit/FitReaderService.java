package ch.sthomas.stddivelogger.service.importer.fit;

import static java.time.Duration.*;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSummary;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;

import com.garmin.fit.*;
import com.google.common.base.CaseFormat;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class FitReaderService extends BaseReaderService {
    private static final Instant garminEpochOffset = Instant.ofEpochMilli(DateTime.OFFSET);
    private static final Logger logger = LoggerFactory.getLogger(FitReaderService.class);
    private final DiveService diveService;

    public FitReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    /**
     * Parses a single-session FIT file. Doesn't touch the dive/site tables - only the dive
     * computer(s) are resolved eagerly (get-or-create by serial number is idempotent, so doing it
     * now rather than at commit is harmless even if the staged import is later discarded).
     */
    public ParsedImport parse(
            final User user, final String filename, final InputStream inputStream) {
        final var messages = new FitDecoder().decode(inputStream);
        if (messages.getSessionMesgs().size() != 1) {
            throw new IllegalArgumentException("Only one-session dive logs supported.");
        }
        final var summaryMessages = messages.getDiveSummaryMesgs();

        final var computers = saveComputers(user, messages);
        if (computers.isEmpty()) {
            throw new IllegalStateException("Expected to save computers, but this failed.");
        }
        final var diveNumber = getDiveNumber(summaryMessages);
        final var summary = getSummary(summaryMessages);

        final var session = messages.getSessionMesgs().getFirst();
        final var gases = getGases(messages);

        final var computer = getComputer(messages, computers);
        final var profile =
                getDiveProfile(
                        messages.getRecordMesgs(),
                        messages.getEventMesgs(),
                        gases,
                        computer,
                        summary);

        final var payload =
                new PendingImportPayload(
                        List.of(profile),
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        List.of(),
                        diveNumber.map(DiveNumber::new).orElse(null));

        final var hasCoordinates =
                session.getStartPositionLong() != null && session.getStartPositionLat() != null;
        final var lat = hasCoordinates ? semicirclesToDegrees(session.getStartPositionLat()) : null;
        final var lon =
                hasCoordinates ? semicirclesToDegrees(session.getStartPositionLong()) : null;

        return new ParsedImport(
                PendingImportSource.FIT_GARMIN,
                null,
                filename,
                getDiveName(filename),
                hasCoordinates ? MessageFormat.format("unnamed-{0}-{1}", lat, lon) : null,
                lat,
                lon,
                computer.serialNumber(),
                summary.map(DiveProfileSummary::start).orElse(null),
                summary.map(s -> Duration.between(s.start(), s.end()).toSeconds()).orElse(null),
                null,
                payload);
    }

    private static DiveComputer getComputer(
            final FitMessages messages, final List<DiveComputer> computers) {
        final var computerIds =
                messages.getRecordMesgs().stream()
                        .map(
                                record ->
                                        record.getDeviceIndex() != null
                                                ? record.getDeviceIndex()
                                                : 0)
                        .map(computers::get)
                        .mapToLong(DiveComputer::id)
                        .sorted()
                        .distinct()
                        .toArray();
        if (computerIds.length > 1) {
            logger.info("Got computer ids: {}", computerIds);
            throw new IllegalArgumentException(
                    "Fit file contains multiple computers, unsupported at the moment.");
        }
        return computers.getFirst();
    }

    static @NonNull List<Gas> getGases(final FitMessages messages) {
        return messages.getDiveGasMesgs().stream()
                .map(
                        gasMsg ->
                                new Gas(
                                        Objects.requireNonNullElse(
                                                        gasMsg.getOxygenContent(), (short) 21)
                                                / 100.0,
                                        Objects.requireNonNullElse(
                                                        gasMsg.getHeliumContent(), (short) 0)
                                                / 100.0))
                .toList();
    }

    DiveProfileUpload getDiveProfile(
            final List<RecordMesg> records,
            final List<EventMesg> events,
            final List<Gas> gases,
            final DiveComputer computer,
            final Optional<DiveProfileSummary> summary) {
        var eventIndex = 0;
        var currentGasIndex = 0;
        final var measurements = new ArrayList<DiveMeasurement>(records.size());
        var i = 0;
        for (final var record : records) {
            final var time = toInstant(record.getTimestamp());
            var nextEvent = events.size() > eventIndex + 1 ? events.get(eventIndex + 1) : null;
            // Process events before and at now
            while (nextEvent != null && !time.isBefore(toInstant(nextEvent.getTimestamp()))) {
                // Process Event
                if (nextEvent.getEvent() == Event.DIVE_GAS_SWITCHED) {
                    currentGasIndex = Math.toIntExact(nextEvent.getData());
                } else {
                    logger.trace(
                            "Time {}: Ignoring Event {} (event: {})",
                            toInstant(nextEvent.getTimestamp()),
                            nextEvent.getEvent().name(),
                            nextEvent);
                }
                eventIndex += 1;
                nextEvent = events.size() > eventIndex + 1 ? events.get(eventIndex + 1) : null;
            }
            final var deco = getDeco(record);
            final var gas =
                    currentGasIndex >= 0 && currentGasIndex < gases.size()
                            ? gases.get(currentGasIndex)
                            : null;
            final var depthField = record.getField(RecordMesg.DepthFieldNum);
            measurements.add(
                    new DiveMeasurement(
                            time,
                            new Temperature(
                                    record.getTemperature(), Temperature.TemperatureUnit.CELSIUS),
                            depthField != null ? depthField.getDoubleValue() : 0.0,
                            Optional.ofNullable(record.getNdlTime())
                                    .map(Duration::ofSeconds)
                                    .orElse(null),
                            deco,
                            gas,
                            null, // Garmin does not yet support rebreathers AFAIK, check regularly
                            getRMV(record, gas),
                            Optional.ofNullable(record.getN2Load())
                                    .map(Integer::doubleValue)
                                    .orElse(null),
                            i == records.size() - 1
                                    ? summary.map(DiveProfileSummary::o2Toxicity).orElse(null)
                                    : null,
                            Optional.ofNullable(record.getCnsLoad())
                                    .map(Short::doubleValue)
                                    .orElse(null),
                            null)); // Garmin does not report CCR mode
            i++;
        }
        return new DiveProfileUpload(
                computer.id(),
                summary.map(DiveProfileSummary::start).orElseThrow(),
                summary.map(DiveProfileSummary::end).orElseThrow(),
                measurements);
    }

    @Nullable
    private static Double getRMV(final RecordMesg record, final @Nullable Gas gas) {
        return Optional.ofNullable(record.getVolumeSac())
                .or(() -> Optional.ofNullable(record.getRmv()))
                .map(Float::doubleValue)
                .or(
                        () ->
                                Optional.ofNullable(gas)
                                        .map(Gas::size)
                                        .map(CylinderSize::liters)
                                        .flatMap(
                                                l ->
                                                        // Assertion: Pressure = Bar
                                                        Optional.ofNullable(record.getPressureSac())
                                                                .map(p -> p * l)))
                .orElse(null);
    }

    // Package-private (not private) so it can be unit-tested directly with mocked DiveSummaryMesg
    // values, same convention as getDiveProfile() above.
    Optional<DiveProfileSummary> getSummary(final List<DiveSummaryMesg> summaryMessages) {
        if (summaryMessages.size() <= 1) {
            return Optional.empty();
        }
        final var first = summaryMessages.getFirst();
        final var last = summaryMessages.getLast();
        // Every FIT SDK typed getter below (getAvgDepth(), getBottomTime(), ...) already applies
        // that field's profile-declared scale/offset internally (confirmed via javap on
        // com.garmin.fit.DiveSummaryMesg - e.g. avg_depth/max_depth/bottom_time/descent_time/
        // ascent_time/avg_ascent_rate all have scale=1000 baked into FieldBase.getValueInternal(),
        // same code path used by the generic getDoubleValue() call in getDiveProfile() above).
        // Re-dividing those by 1000 here was double-scaling every one of them down to ~1/1000th of
        // the real value, and ofMillis(x.longValue()) on an already-in-seconds Float additionally
        // truncated the duration fields to a handful of milliseconds. Use the typed getters as-is,
        // and Duration.ofMillis(Math.round(seconds * 1000)) to keep sub-second precision instead of
        // truncating via longValue().
        return Optional.of(
                new DiveProfileSummary(
                        toInstant(first.getTimestamp()),
                        toInstant(last.getTimestamp()),
                        last.getAvgDepth(),
                        last.getMaxDepth(),
                        ofSeconds(last.getSurfaceInterval()),
                        ofMillis(Math.round(last.getBottomTime() * 1000)),
                        ofMillis(Math.round(last.getDescentTime() * 1000)),
                        ofMillis(Math.round(last.getAscentTime() * 1000)),
                        Optional.ofNullable(last.getAvgAscentRate())
                                .map(Float::doubleValue)
                                .orElse(null),
                        Optional.ofNullable(last.getStartN2())
                                .map(Integer::doubleValue)
                                .orElse(null),
                        Optional.ofNullable(last.getEndN2()).map(Integer::doubleValue).orElse(null),
                        // o2Toxicity's FIT unit is OTUs (scale 1), not a percentage - dividing by
                        // 100 had no unit justification and doesn't match how OTUs are stored from
                        // other import sources (a plain OTU count - see e.g. UDDF's otu="12" ->
                        // o2Tox()==12.0).
                        Optional.ofNullable(last.getO2Toxicity())
                                .map(Integer::doubleValue)
                                .orElse(null),
                        Optional.ofNullable(last.getStartCns()).map(f -> f / 100.0).orElse(null),
                        Optional.ofNullable(last.getEndCns()).map(f -> f / 100.0).orElse(null)));
    }

    private List<DiveComputer> saveComputers(final User user, final FitMessages messages) {
        final var fileIdMessages = messages.getFileIdMesgs();
        final var manufacturers =
                fileIdMessages.stream()
                        .map(FileIdMesg::getManufacturer)
                        .map(Manufacturer::getStringFromValue)
                        .map(s -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s))
                        .toList();
        final var serialNumbers = fileIdMessages.stream().map(FileIdMesg::getSerialNumber).toList();
        final var timesCreated =
                fileIdMessages.stream()
                        .map(FileIdMesg::getTimeCreated)
                        .map(FitReaderService::toInstant)
                        .toList();
        final var products =
                fileIdMessages.stream()
                        .map(FileIdMesg::getProduct)
                        .map(GarminProduct::getStringFromValue) // Potentially unsafe
                        .map(s -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, s))
                        .toList();
        return getComputers(user, manufacturers, serialNumbers, products, timesCreated);
    }

    private List<DiveComputer> getComputers(
            final User user,
            final List<String> manufacturers,
            final List<Long> serialNumbers,
            final List<String> products,
            final List<Instant> timesCreated) {
        final var count = manufacturers.size();
        if (count != serialNumbers.size()
                || count != products.size()
                || count != timesCreated.size()) {
            throw new IllegalArgumentException("Lengths do not match");
        }
        return IntStream.range(0, count)
                .sequential()
                .mapToObj(
                        i ->
                                getComputer(
                                        user,
                                        manufacturers.get(i),
                                        serialNumbers.get(i),
                                        products.get(i),
                                        timesCreated.get(i)))
                .filter(Objects::nonNull)
                .toList();
    }

    private @Nullable DiveComputer getComputer(
            final User user,
            final String manufacturer,
            final @Nullable Long serialNumber,
            final String product,
            final Instant timeCreated) {
        if (serialNumber == null) {
            logger.warn(
                    "Serial number is null for {} {} (of user {}), time created: {}",
                    manufacturer,
                    product,
                    user,
                    timeCreated);
            return null;
        }
        final var sn = String.valueOf(serialNumber);
        final var existingComputer =
                diveService.getDiveComputerBySerialNumber(user, manufacturer, sn);
        return existingComputer.orElseGet(
                () -> diveService.createDiveComputer(sn, product, manufacturer, user.id()));
    }

    private Optional<Integer> getDiveNumber(final List<DiveSummaryMesg> summaries) {
        return summaries.stream()
                .skip(1)
                .map(DiveSummaryMesg::getDiveNumber)
                .findFirst()
                .map(Math::toIntExact);
    }

    // Package-private (not private) so it can be unit-tested directly, same convention as
    // getSummary() above.
    List<DecoStop> getDeco(final RecordMesg record) {
        if (Objects.requireNonNullElse(record.getNextStopDepth(), 0.0f) > 0
                && Objects.requireNonNullElse(record.getNextStopTime(), 0L) > 0) {
            // getNextStopDepth() already has its scale applied (same double-scaling bug as
            // getSummary() above - see its comment) - don't divide again.
            return List.of(
                    new DecoStop(
                            "mandatory",
                            record.getNextStopDepth().doubleValue(),
                            record.getNextStopTime()));
        }
        return List.of();
    }

    static Instant toInstant(final DateTime garminTime) {
        final var base = garminEpochOffset.plusSeconds(garminTime.getTimestamp());
        return base.plusMillis((long) (garminTime.getFractionalTimestamp() * 1000));
    }

    private static final PrecisionModel precisionModel = new PrecisionModel(10000);

    static double semicirclesToDegrees(final int semicircles) {
        return precisionModel.makePrecise(semicircles * (180.0 / Math.pow(2, 31)));
    }
}
