package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "t_users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_user_id", nullable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @JoinTable(
            name = "t_group_member",
            joinColumns = {@JoinColumn(name = "fk_user_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_group_id")})
    @ManyToMany
    private Set<GroupEntity> groups;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserEntity() {}

    public UserEntity(final String email, final String password, final String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public UserEntity(final long id, final String email, final String password, final String name) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public User toRecord() {
        return new User(id, email, password, name, createdAt, updatedAt);
    }

    public List<Group> getGroupsWithoutMembers() {
        return groups.stream().map(GroupEntity::toRecord).toList();
    }

    public List<GroupWithMembers> getGroupsWithMembers() {
        return groups.stream().map(GroupEntity::toRecordWithMembers).toList();
    }
}
