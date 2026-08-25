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

    // Ranks agencies the given user already holds more certifications with above the rest, so a
    // recognizable/likely agency surfaces first even before typing anything - ties fall back to
    // the plain alphabetical order.
    @Query(
            """
            SELECT a FROM CertificationAgencyEntity a
            LEFT JOIN CertificationEntity c ON c.agency = a AND c.user.id = :userId
            GROUP BY a
            ORDER BY COUNT(c) DESC, a.name ASC
            """)
    List<CertificationAgencyEntity> findAllOrderedByUserCertCount(long userId);

    @Query(
            """
            SELECT a FROM CertificationAgencyEntity a
            LEFT JOIN CertificationEntity c ON c.agency = a AND c.user.id = :userId
            WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%'))
            GROUP BY a
            ORDER BY COUNT(c) DESC, a.name ASC
            """)
    List<CertificationAgencyEntity> findByPartialNameOrderedByUserCertCount(
            long userId, String query);
}
