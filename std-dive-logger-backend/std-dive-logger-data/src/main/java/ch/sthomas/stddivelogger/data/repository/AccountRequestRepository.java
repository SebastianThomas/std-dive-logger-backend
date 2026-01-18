package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AccountRequestEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface AccountRequestRepository extends JpaRepository<AccountRequestEntity, String> {

    int deleteAllByValidUntilBefore(OffsetDateTime validUntilBefore);
}
