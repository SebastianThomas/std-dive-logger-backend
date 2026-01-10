package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.model.entity.DiveProfileHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface DiveProfileHistoryEntityRepository
        extends JpaRepository<DiveProfileHistoryEntity, Long> {}
