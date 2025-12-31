package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.geometry.Location;

import jakarta.persistence.*;

import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "t_dive_site")
@SqlResultSetMapping(
        name = "DiveSiteWithIdsMapping",
        entities = @EntityResult(entityClass = DiveSiteEntity.class),
        columns = {
            @ColumnResult(name = "dive_ids", type = Long[].class),
            @ColumnResult(name = "dive_numbers", type = Long[].class),
            @ColumnResult(name = "dive_identifiers", type = String[].class)
        })
public class DiveSiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_site_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location", nullable = false)
    private Point location;

    public DiveSiteEntity() {}

    public DiveSiteEntity(final String name, final Point location) {
        this.name = name;
        this.location = location;
    }

    public DiveSite toRecord() {
        return new DiveSite(id, name, new Location(location.getCoordinate()));
    }
}
