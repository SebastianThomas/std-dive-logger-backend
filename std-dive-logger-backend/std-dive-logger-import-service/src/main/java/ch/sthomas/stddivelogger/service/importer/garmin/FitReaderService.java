package ch.sthomas.stddivelogger.service.importer.garmin;

import static java.time.Duration.*;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import com.garmin.fit.*;
import com.google.common.base.CaseFormat;

import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class FitReaderService {
    private static final Instant garminEpochOffset = Instant.ofEpochMilli(DateTime.OFFSET);
    private static final Logger logger = LoggerFactory.getLogger(FitReaderService.class);
    private final DiveService diveService;

    public FitReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    public Dive readFitAndSaveDive(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream) {
        final var messages = new FitDecoder().decode(inputStream);
        final var summaryMessages = messages.getDiveSummaryMesgs();

        final var computers = saveComputers(user, messages);
        final var bodyWithNumber =
                body.withDiveNumber(
                        () ->
                                getDiveNumber(summaryMessages)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "No dive number provided.")));
        final var summary = getSummary(summaryMessages);

        if (messages.getSessionMesgs().size() != 1) {
            throw new IllegalArgumentException("Only one-session dive logs supported.");
        }
        final var session = messages.getSessionMesgs().getFirst();
        final var startCoordinateLon = semicirclesToDegrees(session.getStartPositionLong());
        final var startCoordinateLat = semicirclesToDegrees(session.getStartPositionLat());
        final var diveSite =
                diveService.createDiveSite(
                        MessageFormat.format(
                                "unnamed-{0}-{1}", startCoordinateLat, startCoordinateLon),
                        new Location(startCoordinateLat, startCoordinateLon));

        final var gases =
                messages.getDiveGasMesgs().stream()
                        .map(
                                gasMsg ->
                                        new Gas(
                                                gasMsg.getOxygenContent(),
                                                gasMsg.getHeliumContent()))
                        .toList();

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
        if (computerIds.length > 1 || computerIds[0] != 0) {
            throw new IllegalArgumentException(
                    "Fit file contains multiple computers, unsupported at the moment.");
        }
        final var computer = computers.getFirst();
        final var events = messages.getEventMesgs();
        final var profile =
                getDiveProfile(messages.getRecordMesgs(), events, gases, computer, summary);
        final var buddies = List.<String>of();
        return diveService.saveDive(user, bodyWithNumber, diveSite.id(), List.of(profile), buddies);
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
        for (final var record : records) {
            final var time = toInstant(record.getTimestamp());
            var nextEvent = events.size() > eventIndex + 1 ? events.get(eventIndex + 1) : null;
            // Process events before and at now
            while (nextEvent != null && !time.isBefore(toInstant(nextEvent.getTimestamp()))) {
                // Process Event
                switch (nextEvent.getEvent()) {
                    case Event.DIVE_GAS_SWITCHED ->
                            currentGasIndex = Math.toIntExact(nextEvent.getData());
                    default ->
                            logger.debug(
                                    "Time {}: Ignoring Event {} (event: {})",
                                    toInstant(nextEvent.getTimestamp()),
                                    nextEvent.getEvent().name(),
                                    nextEvent);
                }
                eventIndex += 1;
                nextEvent = events.size() > eventIndex + 1 ? events.get(eventIndex + 1) : null;
            }
            final var deco = getDeco(record);
            measurements.add(
                    new DiveMeasurement(
                            time,
                            new Temperature(
                                    record.getTemperature(), Temperature.TemperatureUnit.CELSIUS),
                            record.getDepth() / 1000.0,
                            ofSeconds(record.getNdlTime()),
                            deco,
                            gases.get(currentGasIndex)));
        }
        return new DiveProfileUpload(
                computer.id(),
                summary.map(DiveProfileSummary::start).orElseThrow(),
                summary.map(DiveProfileSummary::end).orElseThrow(),
                measurements);
    }

    private Optional<DiveProfileSummary> getSummary(final List<DiveSummaryMesg> summaryMessages) {
        if (summaryMessages.size() <= 1) {
            return Optional.empty();
        }
        // TODO: RMV/SAC
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
                        ofMillis((long) (float) last.getBottomTime()),
                        ofMillis((long) (float) last.getDescentTime()),
                        ofMillis((long) (float) last.getAscentTime()),
                        last.getAvgAscentRate() / 1000,
                        last.getStartN2(),
                        last.getEndN2(),
                        last.getO2Toxicity() / 100.0,
                        last.getStartCns() / 100.0,
                        last.getEndCns() / 100.0));
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
                .toList();
    }

    private DiveComputer getComputer(
            final User user,
            final String manufacturer,
            final long serialNumber,
            final String product,
            final Instant timesCreated) {
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
