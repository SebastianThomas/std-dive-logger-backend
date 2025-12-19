package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.ReaderViewEntity;
import ch.sthomas.stddivelogger.model.entity.embedded.ReaderId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReaderViewRepository extends JpaRepository<ReaderViewEntity, ReaderId> {
    Page<ReaderViewEntity> findByUser_IdOrderBy(Long userId, Pageable pageable);
}
