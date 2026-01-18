package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "t_suits")
public class SuitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_suit_id")
    private Long id;

    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "fk_user_id")
    private UserEntity user;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SuitType type;

    @Column(name = "thickness_mm")
    private Double thicknessMM;

    @Column(name = "additional_notes", nullable = false)
    private String additionalNotes;

    public SuitEntity() {}

    public SuitEntity(final UserEntity user, final Suit suit) {
        if (suit.id() != null) {
            this.id = suit.id();
        }
        this.user = user;
        this.type = suit.type();
        this.thicknessMM = suit.thickness();
        this.additionalNotes = suit.notes();
    }

    public Suit toRecord() {
        return new Suit(id, user.getId(), type, thicknessMM, additionalNotes);
    }

    public SuitType getType() {
        return type;
    }

    public void setType(final @NotNull SuitType type) {
        this.type = type;
    }

    public void setThicknessMM(final Double thicknessMM) {
        this.thicknessMM = thicknessMM;
    }

    public void setAdditionalNotes(final String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }
}
