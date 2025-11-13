package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiveComputerRepository extends JpaRepository<DiveComputerEntity, Long> {
    Optional<DiveComputerEntity> findByCustomIdentifierAndUser_Id(
            String customIdentifier, Long userId);
}
