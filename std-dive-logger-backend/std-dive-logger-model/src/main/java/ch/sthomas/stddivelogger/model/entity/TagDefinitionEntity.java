package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.AutoDetectRule;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

@Entity
@Table(
        name = "t_tag_definitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_user_id", "name"}))
public class TagDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_tag_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_id")
    @Nullable
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_detect_rule")
    @Nullable
    private AutoDetectRule autoDetectRule;

    public TagDefinitionEntity() {}

    public TagDefinitionEntity(final String name, @Nullable final UserEntity user,
                               @Nullable final AutoDetectRule autoDetectRule) {
        this.name = name;
        this.user = user;
        this.autoDetectRule = autoDetectRule;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public AutoDetectRule getAutoDetectRule() {
        return autoDetectRule;
    }

    @Nullable
    public UserEntity getUser() {
        return user;
    }

    public TagDefinition toRecord() {
        return toRecord(0L);
    }

    public TagDefinition toRecord(final long diveCount) {
        return new TagDefinition(
                id,
                name,
                autoDetectRule,
                user != null ? user.getId() : null,
                diveCount);
    }
}
