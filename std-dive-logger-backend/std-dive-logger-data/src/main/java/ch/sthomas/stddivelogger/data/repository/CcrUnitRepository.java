package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CcrUnitRepository extends JpaRepository<CcrUnitEntity, Long> {
    Optional<CcrUnitEntity> findByIdAndUser_Id(Long id, Long userId);

    Page<CcrUnitEntity> findByUser_Id(Long userId, Pageable pageable);

    Optional<CcrUnitEntity> findByUser_IdAndNameAndAdditionalNotes(
            Long userId, String name, String additionalNotes);

    /**
     * Distinct CCR unit names (across all users, to encourage consistent naming — this is what
     * makes the public unit search actually find matches between different users) containing the
     * query string (case-insensitive), ordered by frequency of appearance descending then
     * alphabetically.
     */
    @Query(
            """
            SELECT c.name
            FROM CcrUnitEntity c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
            GROUP BY c.name
            ORDER BY COUNT(c) DESC, c.name ASC
            """)
    List<String> findDistinctNames(String query);

    @Query(
            """
            SELECT DISTINCT c.user
            FROM CcrUnitEntity c
            WHERE c.isPublic = true
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<UserEntity> findUsersByPublicName(String query);
}
