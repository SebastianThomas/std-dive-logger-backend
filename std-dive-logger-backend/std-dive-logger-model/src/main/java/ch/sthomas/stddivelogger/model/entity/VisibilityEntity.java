package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_visibility")
@SuppressWarnings("NullAway.Init")
public class VisibilityEntity {
    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "visibility_meters")
    private @Nullable Double visibilityMeters;

    @Column(name = "visibility_feeling")
    @Enumerated(EnumType.STRING)
    private @Nullable VisibilityFeeling feeling;

    @Column(name = "visibility_description")
    private @Nullable String description;

    public VisibilityEntity() {}

    public VisibilityEntity(final DiveEntity dive, final Visibility visibility) {
        this(dive, visibility.meters(), visibility.feeling(), visibility.description());
    }

    public VisibilityEntity(
            final DiveEntity dive,
            @Nullable final Double visibilityMeters,
            @Nullable final VisibilityFeeling feeling,
            @Nullable final String description) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.visibilityMeters = visibilityMeters;
        this.feeling = feeling;
        this.description = description;
    }

    public void update(final Visibility visibility) {
        this.visibilityMeters = visibility.meters();
        this.feeling = visibility.feeling();
        this.description = visibility.description();
    }

    public Visibility toRecord() {
        return new Visibility(visibilityMeters, description, feeling);
    }
}
