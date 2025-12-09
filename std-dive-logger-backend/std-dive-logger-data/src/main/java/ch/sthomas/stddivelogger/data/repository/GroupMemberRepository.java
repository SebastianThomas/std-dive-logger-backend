package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.GroupMemberEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    Optional<GroupMemberEntity> findByGroup_IdAndUser_Id(Long groupId, Long userId);
}
