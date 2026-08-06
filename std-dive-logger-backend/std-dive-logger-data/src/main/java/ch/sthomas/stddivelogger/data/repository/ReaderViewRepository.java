package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.entity.ReaderViewEntity;
import ch.sthomas.stddivelogger.model.entity.embedded.ReaderId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReaderViewRepository extends JpaRepository<ReaderViewEntity, ReaderId> {
    Page<ReaderViewEntity> findByUser_Id(Long userId, Pageable pageable);

    /**
     * Reader-inclusive counterpart to {@code DiveRepository#findBasicDiveInfoByUserIdAndDiveSiteId}
     * - every dive at this site the user can at least read, not just the ones they own.
     */
    @Query(
            "SELECT new ch.sthomas.stddivelogger.model.dive.BasicDiveInfo(r.dive.id, r.dive.number, r.dive.diveIdentifier)"
                    + " FROM ReaderViewEntity r WHERE r.user.id = :userId AND r.dive.diveSite.id = :siteId")
    List<BasicDiveInfo> findBasicDiveInfoByUserIdAndDiveSiteId(long userId, long siteId);
}
