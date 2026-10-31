package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.TeamEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
