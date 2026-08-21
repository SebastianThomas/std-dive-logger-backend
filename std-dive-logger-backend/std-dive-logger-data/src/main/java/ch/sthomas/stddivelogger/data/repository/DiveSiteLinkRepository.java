package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveSiteLinkEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DiveSiteLinkRepository extends JpaRepository<DiveSiteLinkEntity, Long> {
    @Modifying
    @Query("DELETE FROM DiveSiteLinkEntity l WHERE l.diveSite.id = :siteId")
    void deleteByDiveSite_Id(long siteId);
}
