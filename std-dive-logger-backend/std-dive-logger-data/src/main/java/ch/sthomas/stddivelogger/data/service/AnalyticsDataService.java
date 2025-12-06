package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.AnalyticsDepthVarianceRepository;
import ch.sthomas.stddivelogger.data.repository.DiveProfileRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AnalyticsDataService {
    private final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository;
    private final DiveRepository diveRepository;
    private final StorageService storageService;
    private final DiveProfileRepository diveProfileRepository;

    public AnalyticsDataService(
            final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository,
            final DiveRepository diveRepository,
            final StorageService storageService,
            DiveProfileRepository diveProfileRepository) {
        this.analyticsDepthVarianceEntityRepository = analyticsDepthVarianceEntityRepository;
        this.diveRepository = diveRepository;
        this.storageService = storageService;
        this.diveProfileRepository = diveProfileRepository;
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
        final var requiredProfiles =
                analytics.stream()
                        .map(AnalyticsDepthVariance::profile)
                        .map(DiveProfile::id)
                        .map(p -> Pair.of(p, diveProfileRepository.findById(p).orElseThrow()))
                        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        return analyticsDepthVarianceEntityRepository
                .saveAll(
                        analytics.stream()
                                .map(
                                        a ->
                                                new AnalyticsDepthVarianceEntity(
                                                        a, requiredProfiles.get(a.profile().id())))
                                .toList())
                .stream()
                .map(AnalyticsDepthVarianceEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDepthVariance> findDepthVarianceAnalyticsByDiveId(
            final long userId, final long diveId) {
        return analyticsDepthVarianceEntityRepository.findByReaderAndDiveId(userId, diveId).stream()
                .map(AnalyticsDepthVarianceEntity::toRecord)
                .toList();
    }
}
