package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuitRepository extends JpaRepository<SuitEntity, Long> {
    Optional<SuitEntity> findByIdAndUser_Id(Long id, Long userId);

    Long user(UserEntity user);

    Page<SuitEntity> findByUser_Id(Long userId, Pageable pageable);

    Optional<SuitEntity> findByUser_IdAndTypeAndThicknessMMAndAdditionalNotes(
            Long userId, SuitType type, @Nullable Double thicknessMM, String additionalNotes);
}
