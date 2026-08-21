package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.NamedBuddy;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_buddy_name")
@SuppressWarnings("NullAway.Init")
public class DiveBuddyNameEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_buddy_name_id")
    private Long id;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private @Nullable BuddyRole role;

    public DiveBuddyNameEntity() {}

    public DiveBuddyNameEntity(final DiveEntity dive, final String name) {
        this(dive, name, null);
    }

    public DiveBuddyNameEntity(
            final DiveEntity dive, final String name, @Nullable final BuddyRole role) {
        this.dive = dive;
        this.name = name;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public DiveEntity getDive() {
        return dive;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public @Nullable BuddyRole getRole() {
        return role;
    }

    public void setRole(@Nullable final BuddyRole role) {
        this.role = role;
    }

    public NamedBuddy toRecord() {
        return new NamedBuddy(id, name, role);
    }
}
