package ch.sthomas.stddivelogger.service.importer.fit;

import static java.time.Duration.*;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResultStreaming;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
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
import ch.sthomas.stddivelogger.model.exception.MissingValueException;
import ch.sthomas.stddivelogger.model.exception.MissingValueField;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;

import com.garmin.fit.*;
import com.google.common.base.CaseFormat;

import jakarta.annotation.Nullable;

import org.jspecify.annotations.NonNull;
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
import java.util.stream.Stream;

@Service
public class FitReaderService extends BaseReaderService {
    private static final Instant garminEpochOffset = Instant.ofEpochMilli(DateTime.OFFSET);
    private static final Logger logger = LoggerFactory.getLogger(FitReaderService.class);
    private final DiveService diveService;

    public FitReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    public UploadDiveResultStreaming readFitAndSaveDive(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream) {
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
        final var diveSite = getOrSaveLocation(body, session);
        final var gases = getGases(messages);

        final var computer = getComputer(messages, computers);
        final var profile =
                getDiveProfile(
                        messages.getRecordMesgs(),
                        messages.getEventMesgs(),
                        gases,
                        computer,
                        summary);
        final var buddies = List.<String>of();
        final var diveName = getDiveName(body, filename);
        final var result =
                diveService.saveDive(
                        user,
                        diveNumber,
                        diveName,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        diveSite.id(),
                        List.of(profile),
                        buddies);
        if (result.isException()) {
            return new UploadDiveResultStreaming(
                    Stream.of(), Stream.of(result.dbException().externalMessage()));
        }
        return new UploadDiveResultStreaming(Stream.of(result.value()), Stream.of());
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

    private static @NonNull List<Gas> getGases(final FitMessages messages) {
        return messages.getDiveGasMesgs().stream()
                .map(
                        gasMsg ->
                                new Gas(
                                        gasMsg.getOxygenContent() / 100.0,
                                        gasMsg.getHeliumContent() / 100.0))
                .toList();
    }

    private DiveSite getOrSaveLocation(final UploadDiveBody body, final SessionMesg session) {
        if (body.diveSiteId() != null) {
            return diveService.getSiteById(body.diveSiteId()).orElseThrow();
        }
        if (session.getStartPositionLong() == null || session.getStartPositionLat() == null) {
            throw new MissingValueException(MissingValueField.DIVE_SITE);
        }
        final var startCoordinateLon = semicirclesToDegrees(session.getStartPositionLong());
        final var startCoordinateLat = semicirclesToDegrees(session.getStartPositionLat());
        return diveService.getOrCreateDiveSite(
                MessageFormat.format("unnamed-{0}-{1}", startCoordinateLat, startCoordinateLon),
                new Location(startCoordinateLat, startCoordinateLon));
    }

    private DiveProfileUpload getDiveProfile(
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
            final var gas = gases.get(currentGasIndex);
            measurements.add(
                    new DiveMeasurement(
                            time,
                            new Temperature(
                                    record.getTemperature(), Temperature.TemperatureUnit.CELSIUS),
                            record.getField(RecordMesg.DepthFieldNum).getDoubleValue(),
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
                                    .orElse(null)));
            i++;
        }
        return new DiveProfileUpload(
                computer.id(),
                summary.map(DiveProfileSummary::start).orElseThrow(),
                summary.map(DiveProfileSummary::end).orElseThrow(),
                measurements);
    }

    @Nullable
    private static Double getRMV(final RecordMesg record, final Gas gas) {
        return Optional.ofNullable(record.getVolumeSac())
                .or(() -> Optional.ofNullable(record.getRmv()))
                .map(Float::doubleValue)
                .or(
                        () ->
                                Optional.ofNullable(gas.size())
                                        .map(CylinderSize::liters)
                                        .flatMap(
                                                l ->
                                                        // Assertion: Pressure = Bar
                                                        Optional.ofNullable(record.getPressureSac())
                                                                .map(p -> p * l)))
                .orElse(null);
    }

    private Optional<DiveProfileSummary> getSummary(final List<DiveSummaryMesg> summaryMessages) {
        if (summaryMessages.size() <= 1) {
            return Optional.empty();
        }
        // TODO: Use field scale instead of magic constants
        final var first = summaryMessages.getFirst();
        final var last = summaryMessages.getLast();
        return Optional.of(
                new DiveProfileSummary(
                        toInstant(first.getTimestamp()),
                        toInstant(last.getTimestamp()),
                        last.getAvgDepth() / 1000,
                        last.getMaxDepth() / 1000,
                        ofSeconds(last.getSurfaceInterval()),
                        ofMillis(last.getBottomTime().longValue()),
                        ofMillis(last.getDescentTime().longValue()),
                        ofMillis(last.getAscentTime().longValue()),
                        Optional.ofNullable(last.getAvgAscentRate())
                                .map(f -> f / 1000.0)
                                .orElse(null),
                        Optional.ofNullable(last.getStartN2())
                                .map(Integer::doubleValue)
                                .orElse(null),
                        Optional.ofNullable(last.getEndN2()).map(Integer::doubleValue).orElse(null),
                        Optional.ofNullable(last.getO2Toxicity()).map(f -> f / 100.0).orElse(null),
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

    private DiveComputer getComputer(
            final User user,
            final String manufacturer,
            final Long serialNumber,
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

    private List<DecoStop> getDeco(final RecordMesg record) {
        if (Objects.requireNonNullElse(record.getNextStopDepth(), 0.0f) > 0
                && Objects.requireNonNullElse(record.getNextStopTime(), 0L) > 0) {
            return List.of(
                    new DecoStop(
                            "mandatory",
                            record.getNextStopDepth() / 1000.0,
                            record.getNextStopTime()));
        }
        return List.of();
    }

    private Optional<Long> getDiveNumber(final DiveSummaryMesg summary) {
        return Optional.ofNullable(summary.getDiveNumber());
    }

    private Optional<Field> findMessageByName(final List<Field> fields, final String name) {
        return fields.stream().filter(f -> name.equals(f.getName())).findFirst();
    }

    private Optional<FileIdMesg> findMessagesByName(
            final List<FileIdMesg> fields, final String name) {
        return fields.stream().filter(f -> name.equals(f.getName())).findFirst();
    }

    static Instant toInstant(final DateTime garminTime) {
        final var base = garminEpochOffset.plusSeconds(garminTime.getTimestamp());
        return base.plusMillis((long) (garminTime.getFractionalTimestamp() * 1000));
    }

    private static final PrecisionModel precisionModel = new PrecisionModel(10000);

    static double semicirclesToDegrees(final int semicircles) {
        return precisionModel.makePrecise(semicircles * (180.0 / Math.pow(2, 31)));
    }

    static List<String> getMessageNames(final List<FieldBase> fields) {
        return fields.stream().map(FieldBase::getName).toList();
    }
}
