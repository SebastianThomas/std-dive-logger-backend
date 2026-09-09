package ch.sthomas.stddivelogger.analytics.maps;

import ch.sthomas.stddivelogger.analytics.job.AnalyticsJobQueue;
import ch.sthomas.stddivelogger.analytics.job.JobKind;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger kept separate so disabled environments never queue doomed import runs. */
@Component
@ConditionalOnProperty(name = "maps.import.kubernetes.enabled", havingValue = "true")
public class MapsImportScheduling {
    private final AnalyticsJobQueue queue;

    public MapsImportScheduling(final AnalyticsJobQueue queue) {
        this.queue = queue;
    }

    /** Refresh the administrative boundaries of every source at 02:00 UTC every Sunday. */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    public void importMapsBoundaries() {
        queue.enqueue(JobKind.MAPS_IMPORT, false);
    }
}
