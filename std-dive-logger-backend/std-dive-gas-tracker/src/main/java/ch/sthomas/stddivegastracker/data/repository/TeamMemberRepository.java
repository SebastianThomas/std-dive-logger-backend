package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.TeamMemberEntity;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamSummary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {

    Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, Long userId);

    /** The PENDING (or LEFT-while-pending) member invited with this normalised email. */
    Optional<TeamMemberEntity> findByTeamIdAndInvitedEmail(Long teamId, String invitedEmail);

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

    /**
     * Is there an invitation to this email in a team where the user has no row yet? One that
     * duplicates an existing membership is never bound, so it must not trigger any work either.
     */
    @Query(
            """
            select count(p) > 0 from TeamMemberEntity p
            where p.invitedEmail = :email
              and not exists (select 1 from TeamMemberEntity m
                              where m.teamId = p.teamId and m.userId = :userId)
            """)
    boolean existsBindable(String email, Long userId);

    /**
     * Binds the invitations of this email to the user - only in teams where the user has no row
     * yet. A duplicate invitation (the user is already a member of that team) is left PENDING:
     * nothing is deleted here, so sign-in can never fail on rows that reference it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE t_team_member p SET fk_user_id = :userId, invited_email = NULL
                    WHERE p.invited_email = :email
                      AND NOT EXISTS (SELECT 1 FROM t_team_member m
                                      WHERE m.fk_team_id = p.fk_team_id AND m.fk_user_id = :userId)
                    """,
            nativeQuery = true)
    int bindPending(String email, long userId);
}
