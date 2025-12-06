package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;
import ch.sthomas.stddivelogger.model.entity.embedded.AnalyticsDepthVarianceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalyticsDepthVarianceRepository
        extends JpaRepository<AnalyticsDepthVarianceEntity, AnalyticsDepthVarianceId> {
    @Query("SELECT MAX(a.profile.dive.id) FROM AnalyticsDepthVarianceEntity a")
    Optional<Long> findMaxDiveId();

    @Query("SELECT MAX(a.profile.dive.id) FROM AnalyticsDepthVarianceEntity a WHERE a.id.version = :version")
    Optional<Long> findMaxDiveIdByVersion(long version);
}
