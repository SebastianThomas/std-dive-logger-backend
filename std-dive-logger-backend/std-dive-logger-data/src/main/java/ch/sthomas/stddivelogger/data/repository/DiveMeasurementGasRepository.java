package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.gas.DiveMeasurementGasEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiveMeasurementGasRepository
        extends JpaRepository<DiveMeasurementGasEntity, Long> {

    @Modifying
    @Query(
            """
            DELETE FROM DiveMeasurementGasEntity g WHERE g.measurement.profile.dive.id = :diveId
            """)
    void deleteAllByDiveId(long diveId);

    List<DiveMeasurementGasEntity> findAllByMeasurement_Profile_IdOrderByMeasurement_TimeAsc(
            long profileId);
}
