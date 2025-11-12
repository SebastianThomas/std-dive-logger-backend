package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveSite;

import jakarta.persistence.*;

import org.locationtech.jts.geom.Coordinate;

@Entity
@Table(name = "t_dive_site")
public class DiveSiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_site_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location", nullable = false)
    private Coordinate location;

    public DiveSite toRecord() {
        return new DiveSite(id, name, location);
    }
}
