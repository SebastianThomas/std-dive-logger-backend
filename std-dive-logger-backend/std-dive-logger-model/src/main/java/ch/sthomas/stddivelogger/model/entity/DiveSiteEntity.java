package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.DiveSiteType;
import ch.sthomas.stddivelogger.model.geometry.Location;

import jakarta.persistence.*;

import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;

import java.util.List;

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
@SuppressWarnings("NullAway.Init")
public class DiveSiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_site_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location", nullable = false)
    private Point location;

    @Column(name = "description")
    private @Nullable String description;

    @Column(name = "country_region")
    private @Nullable String countryRegion;

    @Column(name = "max_depth")
    private @Nullable Double maxDepth;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_type")
    private @Nullable DiveSiteType siteType;

    @OneToMany(mappedBy = "diveSite")
    @BatchSize(size = 30)
    private List<DiveSiteLinkEntity> links = List.of();

    public DiveSiteEntity() {}

    public DiveSiteEntity(final String name, final Point location) {
        this.name = name;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public void setDescription(@Nullable final String description) {
        this.description = description;
    }

    public void setCountryRegion(@Nullable final String countryRegion) {
        this.countryRegion = countryRegion;
    }

    public void setMaxDepth(@Nullable final Double maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setSiteType(@Nullable final DiveSiteType siteType) {
        this.siteType = siteType;
    }

    public DiveSite toRecord() {
        return new DiveSite(id, name, new Location(location.getCoordinate()));
    }

    public DiveSite toRecordWithLinks(final boolean canEdit) {
        final var loc = new Location(location.getCoordinate());
        return new DiveSite(
                id,
                name,
                loc.lat(),
                loc.lon(),
                description,
                countryRegion,
                maxDepth,
                siteType,
                links.stream().map(DiveSiteLinkEntity::toRecord).toList(),
                canEdit);
    }
}
