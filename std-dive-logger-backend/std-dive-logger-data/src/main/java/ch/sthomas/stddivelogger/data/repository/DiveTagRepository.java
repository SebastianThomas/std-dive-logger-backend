package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveTagEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DiveTagRepository extends JpaRepository<DiveTagEntity, Long> {

    @Modifying
    @Query("DELETE FROM DiveTagEntity t WHERE t.dive.id = :diveId")
    void deleteAllByDiveId(long diveId);
}
