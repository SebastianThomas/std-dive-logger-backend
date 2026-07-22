package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;

import jakarta.persistence.*;

@Entity
@Table(name = "t_ccr_units")
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

    public CcrUnitEntity() {}

    public CcrUnitEntity(final UserEntity user, final CcrUnit ccrUnit) {
        if (ccrUnit.id() != null) {
            this.id = ccrUnit.id();
        }
        this.user = user;
        this.name = ccrUnit.name();
        this.additionalNotes = ccrUnit.notes();
    }

    public CcrUnit toRecord() {
        return new CcrUnit(id, user.getId(), name, additionalNotes);
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
}
