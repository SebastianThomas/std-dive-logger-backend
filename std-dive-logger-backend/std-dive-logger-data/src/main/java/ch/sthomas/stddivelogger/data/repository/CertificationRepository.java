package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.CertificationEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificationRepository extends JpaRepository<CertificationEntity, Long> {

    List<CertificationEntity> findByUser_IdOrderByCertDateDesc(long userId);

    Optional<CertificationEntity> findByIdAndUser_Id(long id, long userId);
}
