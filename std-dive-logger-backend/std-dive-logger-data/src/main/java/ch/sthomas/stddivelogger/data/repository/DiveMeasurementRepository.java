package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiveMeasurementRepository extends JpaRepository<DiveMeasurementEntity, Long> {
    List<DiveMeasurementEntity> findAllByProfile_IdOrderByTimeAsc(Long profileId);

    @Query(
            "SELECT MAX(m.depth) FROM DiveMeasurementEntity m JOIN DiveProfileEntity p ON m.profile.id = p.id JOIN DiveEntity d ON p.dive.id = d.id AND d.user.id = :userId")
    Optional<Double> findMaxDepthByUserId(long userId);

    @Query(
            """
        SELECT MAX(m.temperatureCelsius)
        FROM DiveMeasurementEntity m
        JOIN DiveProfileEntity p
            ON m.profile.id = p.id
        JOIN DiveEntity d
            ON p.dive.id = d.id AND d.user.id = :userId
        """)
    Optional<Double> findMaxTemperatureCelsiusByUserId(long userId);

    @Query(
            """
                    SELECT MIN(m.temperatureCelsius)
                    FROM DiveMeasurementEntity m
                    JOIN DiveProfileEntity p
                        ON m.profile.id = p.id
                    JOIN DiveEntity d
                        ON p.dive.id = d.id AND d.user.id = :userId
                    """)
    Optional<Double> findMinTemperatureCelsiusByUserId(long userId);

    /** Max depth across a specific set of dives (used for tag-combination stats). */
    @Query("""
            SELECT MAX(m.depth)
            FROM DiveMeasurementEntity m
            JOIN DiveProfileEntity p ON m.profile.id = p.id
            WHERE p.dive.id IN :diveIds
            """)
    Optional<Double> findMaxDepthByDiveIds(Collection<Long> diveIds);

    /** Max depth for dives carrying a specific tag. */
    @Query("""
            SELECT MAX(m.depth)
            FROM DiveMeasurementEntity m
            JOIN DiveProfileEntity p ON m.profile.id = p.id
            JOIN DiveEntity d ON p.dive.id = d.id AND d.user.id = :userId
            JOIN d.tags dt ON dt.tag.id = :tagId AND dt.dismissed = false
            """)
    Optional<Double> findMaxDepthByUserIdAndTagId(long userId, long tagId);
}
