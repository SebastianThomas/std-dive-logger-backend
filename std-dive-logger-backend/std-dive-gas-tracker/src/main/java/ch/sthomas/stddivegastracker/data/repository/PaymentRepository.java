package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.PaymentEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByIdAndTeamId(Long id, Long teamId);

    /** Newest first: paid-at, then id (both descending). */
    List<PaymentEntity> findByTeamIdOrderByPaidAtDescIdDesc(Long teamId);
}
