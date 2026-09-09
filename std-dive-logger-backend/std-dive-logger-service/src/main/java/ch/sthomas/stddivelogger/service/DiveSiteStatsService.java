package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.DiveSiteStatsDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSiteStats;

import org.springframework.stereotype.Service;

@Service
public class DiveSiteStatsService {

    private final DiveDataService diveDataService;
    private final DiveSiteStatsDataService statsDataService;

    public DiveSiteStatsService(
            final DiveDataService diveDataService,
            final DiveSiteStatsDataService statsDataService) {
        this.diveDataService = diveDataService;
        this.statsDataService = statsDataService;
    }

    public DiveSiteStats getForSite(final long siteId) {
        diveDataService.findDiveSiteById(siteId).orElseThrow();
        return statsDataService.findForSite(siteId);
    }
}
