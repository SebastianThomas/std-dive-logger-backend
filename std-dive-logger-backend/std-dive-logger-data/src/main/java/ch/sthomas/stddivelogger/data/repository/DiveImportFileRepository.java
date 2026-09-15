package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveImportFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveImportFileRepository extends JpaRepository<DiveImportFileEntity, Long> {

    List<DiveImportFileEntity> findByDiveIdOrderByIdAsc(long diveId);
}
