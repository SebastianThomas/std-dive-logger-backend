package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiveComputerManufacturerRepository
        extends JpaRepository<DiveComputerManufacturerEntity, Long> {
    Optional<DiveComputerManufacturerEntity> findByNameIgnoreCase(String name);
}
