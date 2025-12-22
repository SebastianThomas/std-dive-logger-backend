package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CylinderSizeRepository extends JpaRepository<CylinderSizeEntity, Long> {
    Optional<CylinderSizeEntity> findByUnitAndValue(CylinderSizeUnit unit, Double value);
}
