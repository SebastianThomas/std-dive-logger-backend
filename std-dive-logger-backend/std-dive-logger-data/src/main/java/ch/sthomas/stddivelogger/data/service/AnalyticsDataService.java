package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceResponse;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileSegmentEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class AnalyticsDataService {
    private final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository;
    private final DiveRepository diveRepository;
    private final StorageService storageService;
    private final DiveProfileRepository diveProfileRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final DiveProfileSegmentRepository diveProfileSegmentRepository;

    public AnalyticsDataService(
            final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository,
            final DiveRepository diveRepository,
            final StorageService storageService,
            final DiveProfileRepository diveProfileRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final DiveProfileSegmentRepository diveProfileSegmentRepository) {
        this.analyticsDepthVarianceEntityRepository = analyticsDepthVarianceEntityRepository;
        this.diveRepository = diveRepository;
        this.storageService = storageService;
        this.diveProfileRepository = diveProfileRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveProfileSegmentRepository = diveProfileSegmentRepository;
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
        return toSegmentWithId(entity);
    }

    private DiveProfileSegmentWithId toSegmentWithId(final DiveProfileSegmentEntity entity) {
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
                        profile.toRecord(), firstIdx, entity.getType(), measurements),
                entity.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLatestAnalyticsDepthVarianceDiveId(final long version) {
        return analyticsDepthVarianceEntityRepository.findMaxDiveIdByVersion(version);
    }

    @Transactional(readOnly = true)
    public PagedResponse<Dive> findAllDivesSince(
            final Optional<Long> lastId, final Pageable pageable) {
        final var result =
                diveRepository.findByIdGreaterThanOrderByIdAsc(lastId.orElse(-1L), pageable);
        return PagedResponse.of(result, d -> d.toRecord(storageService.baseUrl(), false));
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
                                                        segmentEntitiesById.get(
                                                                a.segmentWithId().id())))
                                .toList())
                .stream()
                .map(
                        e ->
                                new AnalyticsDepthVariance(
                                        segmentsById.get(e.getSegmentId()), e.toStats()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiveProfileSegmentWithId> findSegmentsByDiveId(final User user, final long id) {
        return diveProfileSegmentRepository.findByReaderAndDiveId(user.id(), id).stream()
                .map(this::toSegmentWithId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDepthVarianceResponse> findDepthVarianceAnalyticsByDiveId(
            final long userId, final long diveId) {
        return analyticsDepthVarianceEntityRepository.findByReaderAndDiveId(userId, diveId).stream()
                .map(AnalyticsDepthVarianceEntity::toResponse)
                .toList();
    }
}
