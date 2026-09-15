package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveProfileImportFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveProfileImportFileRepository
        extends JpaRepository<DiveProfileImportFileEntity, Long> {

    List<DiveProfileImportFileEntity> findByProfileIdOrderByIdAsc(long profileId);

    @Query(
            "SELECT l FROM DiveProfileImportFileEntity l WHERE l.profileId IN"
                    + " (SELECT p.id FROM DiveProfileEntity p WHERE p.dive.id = :diveId)"
                    + " ORDER BY l.id")
    List<DiveProfileImportFileEntity> findByDiveId(long diveId);
}
