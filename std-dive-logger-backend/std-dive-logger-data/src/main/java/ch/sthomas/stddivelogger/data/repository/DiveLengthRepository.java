package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.data.repository.helper.ReadOnlyRepository;
import ch.sthomas.stddivelogger.model.entity.DiveLengthEntity;

import org.springframework.stereotype.Repository;

@Repository
public interface DiveLengthRepository extends ReadOnlyRepository<DiveLengthEntity, Long> {}
