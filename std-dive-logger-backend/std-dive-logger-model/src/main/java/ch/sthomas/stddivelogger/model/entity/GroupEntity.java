package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.Group;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "t_groups")
public class GroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_group_id")
    private Long id;

    @Column(name = "group_name", unique = true)
    private String groupName;

    @ManyToMany private List<UserEntity> members;

    public Group toRecord() {
        return new Group(id, groupName);
    }
}
