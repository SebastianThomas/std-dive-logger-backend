package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.GasContentUnit;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GasRepository extends JpaRepository<GasEntity, Long> {
    Optional<GasEntity> findByGasMix_IdAndDescriptionAndContentUnitAndContentValue(
            Long gasMixId, String description, GasContentUnit contentUnit, Double contentValue);
}
