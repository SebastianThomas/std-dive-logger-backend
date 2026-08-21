package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveTripMemberEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveTripMemberRepository extends JpaRepository<DiveTripMemberEntity, Long> {
    List<DiveTripMemberEntity> findByTrip_Id(long tripId);

    List<DiveTripMemberEntity> findByMemberDive_Id(long diveId);

    Optional<DiveTripMemberEntity> findByTrip_IdAndMemberDive_Id(long tripId, long diveId);

    Optional<DiveTripMemberEntity> findByTrip_IdAndMemberTrip_Id(long tripId, long memberTripId);

    boolean existsByTrip_IdAndMemberDive_Id(long tripId, long diveId);

    boolean existsByTrip_IdAndMemberTrip_Id(long tripId, long memberTripId);
}
