package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.GroupEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    @Query(
            value =
                    "SELECT * FROM t_groups g INNER JOIN t_group_member m ON g.pk_group_id = m.fk_group_id WHERE g.group_name % :name ORDER BY similarity(g.group_name, :name) DESC, LENGTH(g.group_name) ASC",
            countQuery = "SELECT * FROM t_groups g WHERE g.group_name % :name",
            nativeQuery = true)
    List<GroupEntity> findByClosestMatchName(String name, Pageable pageable);
}
