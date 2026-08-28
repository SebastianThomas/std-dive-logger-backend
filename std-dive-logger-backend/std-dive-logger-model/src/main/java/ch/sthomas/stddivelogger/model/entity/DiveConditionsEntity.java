package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.conditions.Current;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_conditions")
@SuppressWarnings("NullAway.Init")
public class DiveConditionsEntity {
    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "water_type")
    @Enumerated(EnumType.STRING)
    private @Nullable WaterType waterType;

    @Column(name = "current_knots")
    private @Nullable Double currentKnots;

    @Column(name = "current_description")
    private @Nullable String currentDescription;

    @Column(name = "current_feeling")
    private @Nullable Integer currentFeeling;

    public DiveConditionsEntity() {}

    public DiveConditionsEntity(
            final DiveEntity dive,
            @Nullable final WaterType waterType,
            @Nullable final Current current) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.waterType = waterType;
        this.currentKnots = current != null ? current.knots() : null;
        this.currentDescription = current != null ? current.description() : null;
        this.currentFeeling = current != null ? current.feeling() : null;
    }

    public void update(@Nullable final WaterType waterType, @Nullable final Current current) {
        this.waterType = waterType;
        this.currentKnots = current != null ? current.knots() : null;
        this.currentDescription = current != null ? current.description() : null;
        this.currentFeeling = current != null ? current.feeling() : null;
    }

    public @Nullable WaterType getWaterType() {
        return waterType;
    }

    /** Sets just the water type, leaving current strength untouched - for the bulk backfill set. */
    public void setWaterType(@Nullable final WaterType waterType) {
        this.waterType = waterType;
    }

    public @Nullable Current toCurrentRecord() {
        if (currentKnots == null && currentDescription == null && currentFeeling == null) {
            return null;
        }
        return new Current(currentKnots, currentDescription, currentFeeling);
    }
}
