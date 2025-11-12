package ch.sthomas.stddivelogger.model.dive;

import org.locationtech.jts.geom.Coordinate;

public record DiveSite(long id, String name, Coordinate location) {}
