package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.geometry.Location;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;

import java.util.List;

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
                double longitude,
        @Nullable String description,
        @Nullable String countryRegion,
        @Nullable Double maxDepth,
        @Nullable DiveSiteType type,
        @Schema(
                        description =
                                "The site's water type (SALT/FRESH/BRACKISH). A physical property of"
                                        + " the place; an individual dive may override it.")
                @Nullable WaterType waterType,
        List<DiveSiteLink> links,
        @Schema(
                        description =
                                "Whether the requesting user is allowed to edit this site's metadata"
                                        + " - true once they've logged at least one dive here.")
                boolean canEdit) {
    @JsonIgnore
    public Coordinate getCoordinate() {
        return new Coordinate(longitude, latitude);
    }

    public DiveSite(final long id, final String name, final Location location) {
        this(
                id,
                name,
                location.lat(),
                location.lon(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false);
    }

    public DiveSite(
            final long id, final String name, final double latitude, final double longitude) {
        this(id, name, latitude, longitude, null, null, null, null, null, List.of(), false);
    }

    public DiveSite withCanEdit(final boolean canEdit) {
        return new DiveSite(
                id,
                name,
                latitude,
                longitude,
                description,
                countryRegion,
                maxDepth,
                type,
                waterType,
                links,
                canEdit);
    }
}
