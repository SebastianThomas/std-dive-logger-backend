package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.*;

import jakarta.persistence.*;

import java.util.List;
import java.util.Set;

@Entity
@Table(name = "t_groups")
@SuppressWarnings("NullAway.Init")
public class GroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_group_id")
    private Long id;

    @Column(name = "group_name", unique = true)
    private String groupName;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<GroupMemberEntity> members;

    public GroupEntity() {}

    public GroupEntity(final String groupName, final UserEntity adminUser) {
        this.groupName = groupName;
        this.members = Set.of(new GroupMemberEntity(this, adminUser, GroupRole.ADMIN));
    }

    public GroupEntity(final String groupName, final Set<GroupMemberEntity> members) {
        this.groupName = groupName;
        this.members = members;
    }

    public Group toRecord() {
        return new Group(id, groupName);
    }

    public GroupWithMembers toRecordWithMembers() {
        return new GroupWithMembers(
                id, groupName, getMembers(GroupRole.ADMIN), getMembers(GroupRole.MEMBER));
    }

    private List<FrontendUser> getMembers(final GroupRole role) {
        return members.stream()
                .filter(g -> g.getRole() == role)
                .map(GroupMemberEntity::getUserEntity)
                .map(UserEntity::toRecord)
                .map(User::toFrontendModel)
                .toList();
    }
}
