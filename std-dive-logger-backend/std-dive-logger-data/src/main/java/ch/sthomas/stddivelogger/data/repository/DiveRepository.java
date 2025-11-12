package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveRepository extends JpaRepository<DiveEntity, Integer> {
    List<DiveEntity> findByUser_Id(Long userId);
}
