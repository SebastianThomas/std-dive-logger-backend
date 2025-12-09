package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

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

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMemberEntity> groups;

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

    public UserEntity(
            final String email, final String password, final String name, final boolean verified) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.verified = verified;
    }

    public UserEntity(final long id, final String email, final String password, final String name) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public User toRecord() {
        return new User(id, email, password, name, verified, createdAt, updatedAt);
    }
}
