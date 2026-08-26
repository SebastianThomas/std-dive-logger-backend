package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.DiveBuddyDefaultRole;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_buddy_default_role")
@SuppressWarnings("NullAway.Init")
public class DiveBuddyDefaultRoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_user_id", nullable = false)
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "fk_buddy_user_id")
    private @Nullable UserEntity buddyUser;

    @Column(name = "buddy_name")
    private @Nullable String buddyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private BuddyRole role;

    public DiveBuddyDefaultRoleEntity() {}

    public DiveBuddyDefaultRoleEntity(
            final UserEntity user,
            @Nullable final UserEntity buddyUser,
            @Nullable final String buddyName,
            final BuddyRole role) {
        this.user = user;
        this.buddyUser = buddyUser;
        this.buddyName = buddyName;
        this.role = role;
    }

    public @Nullable String getBuddyName() {
        return buddyName;
    }

    public BuddyRole getRole() {
        return role;
    }

    public void setRole(final BuddyRole role) {
        this.role = role;
    }

    public DiveBuddyDefaultRole toRecord() {
        return new DiveBuddyDefaultRole(
                id,
                buddyUser != null ? buddyUser.toRecord().toFrontendModel() : null,
                buddyName,
                role);
    }
}
