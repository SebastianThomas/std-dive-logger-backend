package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.TeamEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, Long> {

    Optional<TeamEntity> findByInviteToken(String inviteToken);

    /**
     * Serialises membership changes of one team (last-admin guard); hold it for the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TeamEntity t where t.id = :teamId")
    Optional<TeamEntity> findForUpdate(Long teamId);

    /**
     * Same lock for every team whose invitation to this email will be bound to the user (they have
     * no row there yet), in id order (no lock-order cycles between two sign-ins).
     */
    @Query(
            value =
                    """
                    SELECT pk_team_id FROM t_team
                    WHERE pk_team_id IN (SELECT p.fk_team_id FROM t_team_member p
                                         WHERE p.invited_email = :email
                                           AND NOT EXISTS (SELECT 1 FROM t_team_member m
                                                           WHERE m.fk_team_id = p.fk_team_id
                                                             AND m.fk_user_id = :userId))
                    ORDER BY pk_team_id FOR UPDATE
                    """,
            nativeQuery = true)
    List<Long> lockTeamsToBind(String email, long userId);
}
