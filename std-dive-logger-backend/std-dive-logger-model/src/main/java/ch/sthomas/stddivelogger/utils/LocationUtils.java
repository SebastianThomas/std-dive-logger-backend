package ch.sthomas.stddivelogger.utils;

import org.locationtech.jts.geom.Coordinate;

public class LocationUtils {
    public static final double MIN_DIVE_SITE_DIST = 0.005;

    private LocationUtils() {}

    public static boolean isClose(final Coordinate a, final Coordinate b) {
        return a.distance(b) < MIN_DIVE_SITE_DIST;
    }
}
