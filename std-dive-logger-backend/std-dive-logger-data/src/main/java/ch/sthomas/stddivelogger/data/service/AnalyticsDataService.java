package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.AnalyticsDepthVarianceRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AnalyticsDataService {
    private final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository;
    private final DiveRepository diveRepository;
    private final StorageService storageService;

    public AnalyticsDataService(
            final AnalyticsDepthVarianceRepository analyticsDepthVarianceEntityRepository,
            final DiveRepository diveRepository,
            @Qualifier("r2StorageService") StorageService storageService) {
        this.analyticsDepthVarianceEntityRepository = analyticsDepthVarianceEntityRepository;
        this.diveRepository = diveRepository;
        this.storageService = storageService;
    }

    public Optional<Long> findLatestAnalyticsDepthVarianceDiveId() {
        return analyticsDepthVarianceEntityRepository.findMaxDiveId();
    }

    public PagedResponse<Dive> findAllDivesSince(
            final Optional<Long> lastId, final Pageable pageable) {
        final var result =
                lastId.map(id -> diveRepository.findByIdGreaterThan(id, pageable))
                        .orElseGet(() -> diveRepository.findAll(pageable));
        return PagedResponse.of(result, d -> d.toRecord(storageService.baseUrl(), false));
    }

    public AnalyticsDepthVariance save(final AnalyticsDepthVariance depthAnalytics) {
        return analyticsDepthVarianceEntityRepository
                .save(new AnalyticsDepthVarianceEntity(depthAnalytics))
                .toRecord();
    }
}
