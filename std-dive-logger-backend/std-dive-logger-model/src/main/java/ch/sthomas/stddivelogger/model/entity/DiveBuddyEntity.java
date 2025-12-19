package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.*;

import org.apache.commons.lang3.tuple.Pair;

@Entity
@Table(name = "t_dive_buddy")
public class DiveBuddyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_buddy_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_buddy_dive_id")
    private DiveEntity buddyDive;

    public DiveBuddyEntity() {}

    public Pair<DiveEntity, DiveEntity> getPair() {
        return Pair.of(dive, buddyDive);
    }
}
