package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CcrUnitRepository extends JpaRepository<CcrUnitEntity, Long> {
    Optional<CcrUnitEntity> findByIdAndUser_Id(Long id, Long userId);

    Page<CcrUnitEntity> findByUser_Id(Long userId, Pageable pageable);

    Optional<CcrUnitEntity> findByUser_IdAndNameAndAdditionalNotes(
            Long userId, String name, String additionalNotes);
}
