package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;

import jakarta.persistence.*;

@Entity
@Table(name = "t_suits")
public class SuitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_suit_id")
    private Long id;

    @Column(name = "type", nullable = false)
    private SuitType type;

    @Column(name = "thickness_mm")
    private Double thicknessMM;

    @Column(name = "additional_notes", nullable = false)
    private String additionalNotes;

    public SuitEntity() {}

    public SuitEntity(final Suit suit) {
        if (suit.id() != null) {
            this.id = suit.id();
        }
        this.type = suit.type();
        this.thicknessMM = suit.thickness();
        this.additionalNotes = suit.notes();
    }

    public Suit toRecord() {
        return new Suit(id, type, thicknessMM, additionalNotes);
    }

    public SuitType getType() {
        return type;
    }
}
