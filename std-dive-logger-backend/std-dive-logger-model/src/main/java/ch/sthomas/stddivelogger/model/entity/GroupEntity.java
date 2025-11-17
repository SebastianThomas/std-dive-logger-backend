package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "t_groups")
public class GroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_group_id")
    private Long id;

    @Column(name = "group_name", unique = true)
    private String groupName;

    @ManyToMany private Set<UserEntity> members;

    public GroupEntity() {}

    public GroupEntity(final String groupName, final Set<UserEntity> members) {
        this.groupName = groupName;
        this.members = members;
    }

    public GroupWithMembers toRecord() {
        return new GroupWithMembers(id, groupName, null);
    }

    public GroupWithMembers toRecordWithMembers() {
        return new GroupWithMembers(
                id,
                groupName,
                members.stream().map(UserEntity::toRecord).map(User::toFrontendModel).toList());
    }
}
