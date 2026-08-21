package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.CertificationAgencyEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificationAgencyRepository
        extends JpaRepository<CertificationAgencyEntity, Long> {

    List<CertificationAgencyEntity> findAllByOrderByNameAsc();

    Optional<CertificationAgencyEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query(
            "SELECT a FROM CertificationAgencyEntity a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY a.name ASC")
    List<CertificationAgencyEntity> findByPartialName(String query);
}
