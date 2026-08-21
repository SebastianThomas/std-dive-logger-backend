package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveTripDefaultTeamEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveTripDefaultTeamRepository
        extends JpaRepository<DiveTripDefaultTeamEntity, Long> {
    List<DiveTripDefaultTeamEntity> findByTrip_Id(long tripId);

    @Modifying
    @Query("DELETE FROM DiveTripDefaultTeamEntity t WHERE t.trip.id = :tripId")
    void deleteByTrip_Id(long tripId);
}
