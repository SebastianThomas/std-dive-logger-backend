package ch.sthomas.stddivelogger.service.importer;

import net.iakovlev.timeshape.TimeZoneEngine;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Offline lat/lon -> real IANA timezone lookup (bundled boundary data, no network call) - used to
 * correct a dive computer export that only carries a plain wall-clock reading with no timezone of
 * its own (see the Shearwater XML/UDDF/DL7 readers) once a real dive-site location is known, which
 * for those formats is only at commit time - see {@code ImportService.correctForUnknownTimezone}.
 *
 * <p>{@code TimeZoneEngine.initialize()} parses ~20MB of boundary geometry and is only ever needed
 * for these three formats, so it's built once, lazily, on the first real lookup rather than paying
 * that cost on every app startup.
 */
@Service
public class LocationTimezoneResolver {
    private volatile @Nullable TimeZoneEngine engine;

    public Optional<ZoneId> resolve(final double latitude, final double longitude) {
        return engine().query(latitude, longitude);
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
