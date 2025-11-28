package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.geometry.Location;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import org.locationtech.jts.geom.Coordinate;

public record DiveSite(
        long id,
        String name,
        @Schema(
                        minimum = "-90",
                        maximum = "90",
                        example = "47.265",
                        description = "specifies the north–south position")
                double latitude,
        @Schema(
                        minimum = "-180",
                        maximum = "180",
                        example = "11.392",
                        description = "specifies the east–west position")
                double longitude) {
    @JsonIgnore
    public Coordinate getCoordinate() {
        return new Coordinate(longitude, latitude);
    }

    public DiveSite(final long id, final String name, final Location location) {
        this(id, name, location.lat(), location.lon());
    }
}
