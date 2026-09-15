package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveImportFileFieldEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DiveImportFileFieldRepository
        extends JpaRepository<DiveImportFileFieldEntity, Long> {

    List<DiveImportFileFieldEntity> findByDiveImportFileId(long diveImportFileId);

    List<DiveImportFileFieldEntity> findByDiveImportFileIdIn(Collection<Long> diveImportFileIds);
}
