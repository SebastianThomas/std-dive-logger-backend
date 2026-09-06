package ch.sthomas.stddivelogger.data.location;

import ch.sthomas.stddivelogger.model.entity.DiveSiteTimezoneListener;

import net.iakovlev.timeshape.TimeZoneEngine;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Optional;

/** Shared offline site-zone lookup; geometry loads lazily and repeated coordinates are cached. */
@Service
public class LocationTimezoneResolver implements DiveSiteTimezoneListener.Resolver {
    private final java.util.concurrent.ConcurrentMap<String, Optional<ZoneId>> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public @Nullable String resolveZone(final double latitude, final double longitude) {
        return resolve(latitude, longitude).map(ZoneId::getId).orElse(null);
    }

    private volatile @Nullable TimeZoneEngine engine;

    public Optional<ZoneId> resolve(final double latitude, final double longitude) {
        return cache.computeIfAbsent(
                latitude + "," + longitude, ignored -> engine().query(latitude, longitude));
    }

    private TimeZoneEngine engine() {
        var loaded = engine;
        if (loaded == null) {
            synchronized (this) {
                loaded = engine;
                if (loaded == null) {
                    loaded = TimeZoneEngine.initialize();
                    engine = loaded;
                }
            }
        }
        return loaded;
    }
}
