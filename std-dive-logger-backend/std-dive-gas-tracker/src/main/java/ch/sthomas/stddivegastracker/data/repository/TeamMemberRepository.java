package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.TeamMemberEntity;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamSummary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {

    Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamMemberEntity> findByIdAndTeamId(Long id, Long teamId);

    List<TeamMemberEntity> findByTeamId(Long teamId);

    /** How many of the given ids are members of the team (any status). */
    long countByTeamIdAndIdIn(Long teamId, Collection<Long> ids);

    /** Is there an ACTIVE admin (bound to a user) of the team other than member {@code id}? */
    @Query(
            """
            select count(m) > 0 from TeamMemberEntity m
            where m.teamId = :teamId and m.role = :role and m.userId is not null
              and m.leftAt is null and m.id <> :id
            """)
    boolean existsOtherActive(Long teamId, Role role, Long id);

    /** The user's ACTIVE teams for {@code GET /v1/me}, sorted by name (case-insensitive), id. */
    @Query(
            """
            select new ch.sthomas.stddivegastracker.model.team.TeamSummary(t.id, t.name, m.role, m.id)
            from TeamMemberEntity m join TeamEntity t on t.id = m.teamId
            where m.userId = :userId and m.leftAt is null
            order by lower(t.name), t.id
            """)
    List<TeamSummary> findActiveTeamSummaries(Long userId);
}
