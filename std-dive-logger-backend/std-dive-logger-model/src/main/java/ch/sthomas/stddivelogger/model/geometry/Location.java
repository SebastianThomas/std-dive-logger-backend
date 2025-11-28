package ch.sthomas.stddivelogger.model.geometry;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public record Location(double lat, double lon) {
    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    public Location(final Coordinate coordinate) {
        this(coordinate.y, coordinate.x);
    }

    public Point toPoint() {
        return geometryFactory.createPoint(toCoordinate());
    }

    public Coordinate toCoordinate() {
        return new Coordinate(lon, lat);
    }
}
