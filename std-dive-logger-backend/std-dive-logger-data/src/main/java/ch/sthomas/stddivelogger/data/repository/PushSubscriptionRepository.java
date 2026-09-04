package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    List<PushSubscriptionEntity> findByUserId(long userId);

    boolean existsByUserId(long userId);

    @Modifying
    int deleteByEndpoint(String endpoint);
}
