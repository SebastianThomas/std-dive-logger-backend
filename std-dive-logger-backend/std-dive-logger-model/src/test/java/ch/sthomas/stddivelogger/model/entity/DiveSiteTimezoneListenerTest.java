package ch.sthomas.stddivelogger.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.concurrent.atomic.AtomicInteger;

class DiveSiteTimezoneListenerTest {
    @Test
    void readsReuseStoredZoneButWritesRecomputeIt() {
        final var calls = new AtomicInteger();
        final var listener =
                new DiveSiteTimezoneListener(
                        (lat, lon) -> {
                            calls.incrementAndGet();
                            return "Europe/Zurich";
                        });
        final var site =
                new DiveSiteEntity(
                        "Lake", new GeometryFactory().createPoint(new Coordinate(8.5, 47.3)));
        site.setZoneId("Europe/Paris");
        listener.resolveMissingZone(site);
        assertThat(calls.get()).isZero();
        assertThat(site.getZoneId()).isEqualTo("Europe/Paris");
        listener.resolve(site);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(site.getZoneId()).isEqualTo("Europe/Zurich");
        site.setZoneId(null);
        listener.resolveMissingZone(site);
        assertThat(calls.get()).isEqualTo(2);
    }
}
