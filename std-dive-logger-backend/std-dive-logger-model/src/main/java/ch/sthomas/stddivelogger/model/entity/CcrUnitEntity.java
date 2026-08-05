package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_ccr_units")
@SuppressWarnings("NullAway.Init")
public class CcrUnitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_ccr_unit_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_user_id")
    private UserEntity user;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "additional_notes", nullable = false)
    private String additionalNotes;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "default_base_configuration")
    @Enumerated(EnumType.STRING)
    private @Nullable BaseConfiguration defaultBaseConfiguration;

    public CcrUnitEntity() {}

    public CcrUnitEntity(final UserEntity user, final CcrUnit ccrUnit) {
        if (ccrUnit.id() != null) {
            this.id = ccrUnit.id();
        }
        this.user = user;
        this.name = ccrUnit.name();
        this.additionalNotes = ccrUnit.notes();
        this.isPublic = ccrUnit.isPublic();
        this.defaultBaseConfiguration = ccrUnit.defaultBaseConfiguration();
    }

    public CcrUnit toRecord() {
        return new CcrUnit(
                id, user.getId(), name, additionalNotes, isPublic, defaultBaseConfiguration);
    }

    public @Nullable BaseConfiguration getDefaultBaseConfiguration() {
        return defaultBaseConfiguration;
    }

    public void setDefaultBaseConfiguration(
            final @Nullable BaseConfiguration defaultBaseConfiguration) {
        this.defaultBaseConfiguration = defaultBaseConfiguration;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setAdditionalNotes(final String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }

    public void setPublic(final boolean isPublic) {
        this.isPublic = isPublic;
    }

    public UserEntity getUser() {
        return user;
    }

    public Long getId() {
        return id;
    }
}
