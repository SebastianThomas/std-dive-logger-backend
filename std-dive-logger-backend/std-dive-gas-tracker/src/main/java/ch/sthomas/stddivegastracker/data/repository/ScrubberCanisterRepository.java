package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.ScrubberCanisterEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScrubberCanisterRepository extends JpaRepository<ScrubberCanisterEntity, Long> {

    /** Newest first; the children load in batches, so not one query per canister. */
    List<ScrubberCanisterEntity> findByTeamIdOrderByOpenedAtDescIdDesc(Long teamId);

    Optional<ScrubberCanisterEntity> findByIdAndTeamId(Long id, Long teamId);
}
