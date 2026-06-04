package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.TagDefinitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagDefinitionRepository extends JpaRepository<TagDefinitionEntity, Long> {

    /** Returns all system defaults (user IS NULL) plus the given user's own tags. */
    @Query("SELECT t FROM TagDefinitionEntity t WHERE t.user IS NULL OR t.user.id = :userId")
    List<TagDefinitionEntity> findAllVisibleToUser(long userId);

    /** Returns only auto-detect tags visible to the given user. */
    @Query("SELECT t FROM TagDefinitionEntity t WHERE t.autoDetectRule IS NOT NULL AND (t.user IS NULL OR t.user.id = :userId)")
    List<TagDefinitionEntity> findAutoDetectTagsForUser(long userId);

    Optional<TagDefinitionEntity> findByIdAndUser_Id(Long id, Long userId);

    /** Checks whether a name is already taken for this user (or globally). */
    boolean existsByNameIgnoreCaseAndUser_Id(String name, Long userId);

    boolean existsByNameIgnoreCaseAndUserIsNull(String name);

    @Query("SELECT t FROM TagDefinitionEntity t WHERE (t.user IS NULL OR t.user.id = :userId) AND LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<TagDefinitionEntity> findByPartialNameVisibleToUser(String query, long userId);
}
