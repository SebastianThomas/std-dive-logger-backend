package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DiveProfileRepository extends JpaRepository<DiveProfileEntity, Long> {
    @Query(
            value =
                    "UPDATE t_dive_profiles SET fk_dive_id = :baseDiveId WHERE fk_dive_id = :toAddDiveId",
            nativeQuery = true)
    @Modifying
    void setDiveWhereDiveIs(long baseDiveId, long toAddDiveId);
}
