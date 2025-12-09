package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.GroupRole;

import jakarta.persistence.*;

@Entity
@Table(name = "t_group_member")
public class GroupMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_group_member", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_group_id")
    private GroupEntity group;

    @ManyToOne
    @JoinColumn(name = "fk_user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private GroupRole role;

    // Constructors
    public GroupMemberEntity() {}

    public GroupMemberEntity(final GroupEntity group, final UserEntity user, final GroupRole role) {
        this.group = group;
        this.user = user;
        this.role = role;
    }

    public GroupRole getRole() {
        return role;
    }

    public UserEntity getUserEntity() {
        return user;
    }

    public GroupMemberEntity setRole(final GroupRole role) {
        this.role = role;
        return this;
    }
}
