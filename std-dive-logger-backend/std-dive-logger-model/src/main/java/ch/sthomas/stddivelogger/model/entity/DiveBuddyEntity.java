package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_buddy")
public class DiveBuddyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_buddy_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pk_dive_id")
    private DiveEntity dive;

    @ManyToOne
    @JoinColumn(name = "pk_user_id")
    private UserEntity user;

    public User getUser() {
        return user.toRecord();
    }
}
