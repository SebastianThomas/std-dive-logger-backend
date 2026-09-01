package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface DiveProfileRepository extends JpaRepository<DiveProfileEntity, Long> {
    @Query(
            value =
                    "UPDATE t_dive_profiles SET fk_dive_id = :baseDiveId WHERE fk_dive_id = :toAddDiveId",
            nativeQuery = true)
    @Modifying
    void setDiveWhereDiveIs(long baseDiveId, long toAddDiveId);

    /**
     * Whether a profile already exists for this computer starting at this exact instant. Mirrors
     * the {@code UNIQUE (fk_dive_computer, dive_profile_start)} constraint on {@code
     * t_dive_profiles} - used to reject a colliding manual-dive entry with a clear message instead
     * of letting the insert fail as a 500 (every manual dive of a user shares one synthetic
     * "Manual" computer, so two manual dives entered with the same minute-precision start would
     * otherwise violate it).
     */
    boolean existsByComputer_IdAndProfileStart(long computerId, OffsetDateTime profileStart);
}
