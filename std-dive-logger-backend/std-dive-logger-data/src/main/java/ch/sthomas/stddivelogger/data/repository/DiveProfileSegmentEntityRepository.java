package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveProfileSegmentEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiveProfileSegmentEntityRepository
        extends JpaRepository<DiveProfileSegmentEntity, Long> {}
