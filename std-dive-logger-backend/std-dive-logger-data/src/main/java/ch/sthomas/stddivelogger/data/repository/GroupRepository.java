package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.entity.GroupEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    @Query(
            value =
                    """
                            SELECT *
                            FROM t_groups g
                            WHERE starts_with(group_name, :name)
                               OR g.group_name % :name
                            ORDER BY CASE
                                         WHEN starts_with(group_name, :name)
                                             THEN 1
                                         ELSE 0
                                         END DESC,
                                     similarity(g.group_name, :name) DESC,
                                     LENGTH(g.group_name);
                            """,
            countQuery =
                    "SELECT COUNT(*) FROM t_groups g WHERE starts_with(group_name, :name) OR g.group_name % :name",
            nativeQuery = true)
    List<GroupEntity> findByClosestMatchName(String name, Pageable pageable);

    @Modifying
    @Query(
            value =
                    "INSERT INTO t_group_member (fk_group_id, fk_user_id, role) VALUES (:groupId, :userId, :role)",
            nativeQuery = true)
    void joinGroup(long groupId, long userId, String role);

    Optional<GroupEntity> findByGroupNameIgnoreCase(String groupName);
}
