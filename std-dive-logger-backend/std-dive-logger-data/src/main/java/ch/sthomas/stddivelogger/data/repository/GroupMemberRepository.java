package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.GroupMemberEntity;
import ch.sthomas.stddivelogger.model.user.GroupRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    Optional<GroupMemberEntity> findByGroup_IdAndUser_Id(Long groupId, Long userId);

    @Query(
            "SELECT g FROM UserEntity u JOIN GroupMemberEntity admin ON admin.role = :adminRole AND u.id = admin.user.id JOIN GroupMemberEntity g WHERE g.group.id = admin.group.id AND g.role = :searchedRole")
    List<GroupMemberEntity> findRequests(long adminId, GroupRole adminRole, GroupRole searchedRole);

    int countByUser_IdAndRole(Long userId, GroupRole role);

    boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);

    Page<GroupMemberEntity> findByUser_IdOrderById(Long userId, Pageable pageable);
}
