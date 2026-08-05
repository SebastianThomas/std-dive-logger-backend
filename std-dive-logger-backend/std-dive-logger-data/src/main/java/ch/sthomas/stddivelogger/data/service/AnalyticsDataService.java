package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.DivesToRecompute;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceResponse;
import ch.sthomas.stddivelogger.model.analytics.DiveGasCalculator;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileGasResponse;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileRateCalculator;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileRatesResponse;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmenter;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;
import ch.sthomas.stddivelogger.model.entity.AnalyticsJobStateEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileSegmentEntity;
import ch.sthomas.stddivelogger.model.entity.gas.DiveMeasurementGasEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AnalyticsDataService {
    // Fetching one extra id lets us tell "there are more dives needing recompute than this
    // batch" without a separate COUNT query.
    private static final int RECOMPUTE_OVERFETCH = 1;

    private final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository;
    private final AnalyticsJobStateRepository analyticsJobStateRepository;
    private final DiveRepository diveRepository;
    private final StorageService storageService;
    private final DiveProfileRepository diveProfileRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final DiveProfileSegmentRepository diveProfileSegmentRepository;
    private final DiveMeasurementGasRepository diveMeasurementGasRepository;

    public AnalyticsDataService(
            final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository,
            final AnalyticsJobStateRepository analyticsJobStateRepository,
            final DiveRepository diveRepository,
            final StorageService storageService,
            final DiveProfileRepository diveProfileRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final DiveProfileSegmentRepository diveProfileSegmentRepository,
            final DiveMeasurementGasRepository diveMeasurementGasRepository) {
        this.analyticsDepthVarianceEntityRepository = analyticsDepthVarianceEntityRepository;
        this.analyticsJobStateRepository = analyticsJobStateRepository;
        this.diveRepository = diveRepository;
        this.storageService = storageService;
        this.diveProfileRepository = diveProfileRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveProfileSegmentRepository = diveProfileSegmentRepository;
        this.diveMeasurementGasRepository = diveMeasurementGasRepository;
    }

    @Transactional
    public DiveProfileSegmentWithId saveSegment(final DiveProfileSegment segment) {
        final var entity =
                diveProfileSegmentRepository.save(
                        new DiveProfileSegmentEntity(
                                segment,
                                diveProfileRepository
                                        .findById(segment.profile().id())
                                        .orElseThrow()));
        return toSegmentWithId(entity, true);
    }

    private DiveProfileSegmentWithId toSegmentWithId(
            final DiveProfileSegmentEntity entity, final boolean includeMeasurements) {
        final var profile = entity.getProfile();
        final var firstIdx = entity.getFirstMeasurementIdx();
        final var lastIdx = entity.getLastMeasurementIdx();
        final var allMeasurements =
                diveMeasurementRepository.findAllByProfile_IdOrderByTimeAsc(profile.getId());
        final var measurements =
                IntStream.rangeClosed(firstIdx, lastIdx)
                        .mapToObj(allMeasurements::get)
                        .map(DiveMeasurementEntity::toRecordWithId)
                        .toList();
        return new DiveProfileSegmentWithId(
                new DiveProfileSegment(
                        profile.toRecord(false),
                        firstIdx,
                        entity.getType(),
                        includeMeasurements ? measurements : null),
                entity.getId());
    }

    @Transactional(readOnly = true)
    public DivesToRecompute findDivesNeedingRecompute(
            final String module, final String jobName, final long version, final int limit) {
        final var ids =
                analyticsJobStateRepository.findDiveIdsNeedingRecompute(
                        module, jobName, version, PageRequest.of(0, limit + RECOMPUTE_OVERFETCH));
        final var hasMore = ids.size() > limit;
        final var pageIds = hasMore ? ids.subList(0, limit) : ids;
        final var dives =
                diveRepository.findAllById(pageIds).stream()
                        .map(d -> d.toRecord(storageService.baseUrl(), false))
                        .toList();
        return new DivesToRecompute(dives, hasMore);
    }

    @Transactional
    public void deleteExistingSegmentsAndAnalytics(final long diveId) {
        // Depth-variance rows for these segments cascade-delete at the DB level.
        diveProfileSegmentRepository.deleteAllByDiveId(diveId);
        diveProfileSegmentRepository.flush();
        diveMeasurementGasRepository.deleteAllByDiveId(diveId);
        diveMeasurementGasRepository.flush();
    }

    /**
     * Marks a dive as needing a full analytics recompute - for use whenever something *other* than
     * the scheduled recompute job itself changes a dive's measurements after the fact (e.g.
     * trimming a profile). Clearing the stored job-state rows makes {@link
     * #findDivesNeedingRecompute} pick this dive back up on its next sweep (it runs every minute),
     * the same as a version bump does for every dive at once.
     */
    @Transactional
    public void invalidateAnalyticsForDive(final long diveId) {
        deleteExistingSegmentsAndAnalytics(diveId);
        analyticsJobStateRepository.deleteByDive_Id(diveId);
    }

    @Transactional
    public void recordJobState(
            final long diveId,
            final String module,
            final String jobName,
            final long version,
            final Instant computedAt) {
        final var existing =
                analyticsJobStateRepository.findByDive_IdAndModuleAndJobName(
                        diveId, module, jobName);
        if (existing.isPresent()) {
            final var entity = existing.get();
            entity.setVersion(version);
            entity.setComputedAt(computedAt);
            analyticsJobStateRepository.save(entity);
            return;
        }
        final var dive = diveRepository.findById(diveId).orElseThrow();
        analyticsJobStateRepository.save(
                new AnalyticsJobStateEntity(dive, module, jobName, version, computedAt));
    }

    @Transactional
    public List<AnalyticsDepthVariance> saveAll(final List<AnalyticsDepthVariance> analytics) {
        final var segmentsById =
                analytics.stream()
                        .map(AnalyticsDepthVariance::segmentWithId)
                        .collect(
                                Collectors.toMap(
                                        DiveProfileSegmentWithId::id, Function.identity()));
        final var segmentEntitiesById =
                segmentsById.keySet().stream()
                        .map(diveProfileSegmentRepository::findById)
                        .map(Optional::orElseThrow)
                        .collect(
                                Collectors.toMap(
                                        DiveProfileSegmentEntity::getId, Function.identity()));
        return analyticsDepthVarianceEntityRepository
                .saveAll(
                        analytics.stream()
                                .map(
                                        a ->
                                                new AnalyticsDepthVarianceEntity(
                                                        a,
                                                        // present by construction: same key set
                                                        // as segmentsById, built just above.
                                                        Objects.requireNonNull(
                                                                segmentEntitiesById.get(
                                                                        a.segmentWithId().id()))))
                                .toList())
                .stream()
                .map(
                        e ->
                                new AnalyticsDepthVariance(
                                        // present by construction: every saved entity's segment
                                        // id originates from a key of segmentsById above.
                                        Objects.requireNonNull(segmentsById.get(e.getSegmentId())),
                                        e.toStats()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiveProfileSegmentWithId> findSegmentsByDiveId(
            final User user, final long id, final boolean includeMeasurements) {
        final var segments = diveProfileSegmentRepository.findByReaderAndDiveId(user.id(), id);
        if (segments.isEmpty()) {
            throw new NoSuchElementException(
                    "There are no segments computed for dive "
                            + id
                            + ", maybe check again later if this dive is new.");
        }
        return segments.stream().map(s -> toSegmentWithId(s, includeMeasurements)).toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDepthVarianceResponse> findDepthVarianceAnalyticsByDiveId(
            final long userId, final long diveId) {
        return analyticsDepthVarianceEntityRepository.findByReaderAndDiveId(userId, diveId).stream()
                .map(AnalyticsDepthVarianceEntity::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiveProfileRatesResponse> findRatesByDiveId(final User user, final long diveId) {
        final var segments = diveProfileSegmentRepository.findByReaderAndDiveId(user.id(), diveId);
        if (segments.isEmpty()) {
            throw new NoSuchElementException(
                    "There are no segments computed for dive "
                            + diveId
                            + ", maybe check again later if this dive is new.");
        }
        final var profileIds =
                segments.stream().map(s -> s.getProfile().getId()).distinct().toList();
        return profileIds.stream().map(this::ratesForProfile).toList();
    }

    private DiveProfileRatesResponse ratesForProfile(final long profileId) {
        final var measurements =
                diveMeasurementRepository.findAllByProfile_IdOrderByTimeAsc(profileId).stream()
                        .map(DiveMeasurementEntity::toRecordWithId)
                        .toList();
        // smoothedRates() itself is now robust to a corrupted depth in the input (see
        // DiveProfileRateCalculator) and always returns finite values - this is a second,
        // independent check at the API boundary so a bad *source* measurement (e.g. a NaN depth
        // already sitting in the DB from an old buggy import) is dropped from the response
        // outright rather than handed to the client as a data point with a garbage depth.
        final var rates =
                DiveProfileRateCalculator.smoothedForDisplay(
                        DiveProfileSegmenter.smoothedRates(measurements));
        final var ratePoints =
                IntStream.range(0, measurements.size())
                        .filter(i -> Double.isFinite(measurements.get(i).measurement().depth()))
                        .mapToObj(
                                i ->
                                        new DiveProfileRatesResponse.RatePoint(
                                                measurements.get(i).measurement().time(),
                                                measurements.get(i).measurement().depth(),
                                                rates[i]))
                        .toList();
        return new DiveProfileRatesResponse(profileId, ratePoints);
    }

    /**
     * Persists the backend's own computed PO2/FO2 (see {@link
     * ch.sthomas.stddivelogger.model.analytics.DiveGasCalculator}) for a dive - called once per
     * recompute pass, after any stale rows for the dive have already been cleared by {@link
     * #deleteExistingSegmentsAndAnalytics}.
     */
    @Transactional
    public void saveGasResults(final List<DiveGasCalculator.GasResult> results) {
        if (results.isEmpty()) {
            return;
        }
        final var measurementIds = results.stream().map(r -> r.measurementId()).toList();
        final var measurementsById =
                diveMeasurementRepository.findAllById(measurementIds).stream()
                        .collect(
                                Collectors.toMap(
                                        DiveMeasurementEntity::getId, Function.identity()));
        final var entities =
                results.stream()
                        .map(
                                r -> {
                                    final var measurement = measurementsById.get(r.measurementId());
                                    if (measurement == null) {
                                        return null;
                                    }
                                    return new DiveMeasurementGasEntity(
                                            measurement, r.po2(), r.fo2());
                                })
                        .filter(Objects::nonNull)
                        .toList();
        diveMeasurementGasRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<DiveProfileGasResponse> findGasByDiveId(final User user, final long diveId) {
        final var segments = diveProfileSegmentRepository.findByReaderAndDiveId(user.id(), diveId);
        if (segments.isEmpty()) {
            throw new NoSuchElementException(
                    "There are no segments computed for dive "
                            + diveId
                            + ", maybe check again later if this dive is new.");
        }
        final var profileIds =
                segments.stream().map(s -> s.getProfile().getId()).distinct().toList();
        return profileIds.stream().map(this::gasForProfile).toList();
    }

    private DiveProfileGasResponse gasForProfile(final long profileId) {
        final var points =
                diveMeasurementGasRepository
                        .findAllByMeasurement_Profile_IdOrderByMeasurement_TimeAsc(profileId)
                        .stream()
                        .map(
                                g ->
                                        new DiveProfileGasResponse.GasPoint(
                                                g.getMeasurement().getTime().toInstant(),
                                                g.getCalculatedPo2(),
                                                g.getCalculatedFo2()))
                        .toList();
        return new DiveProfileGasResponse(profileId, points);
    }
}
