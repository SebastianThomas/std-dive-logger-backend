package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveMeasurementRepository extends JpaRepository<DiveMeasurementEntity, Long> {
    List<DiveMeasurementEntity> findAllByProfile_IdOrderByTimeAsc(Long profileId);
}
