package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_buddy_name")
public class DiveBuddyNameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_buddy_name_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "name", nullable = false)
    private String name;

    public DiveBuddyNameEntity() {}

    public DiveBuddyNameEntity(final DiveEntity dive, final String name) {
        this.dive = dive;
        this.name = name;
    }

    public DiveEntity getDive() {
        return dive;
    }

    public String getName() {
        return name;
    }
}
