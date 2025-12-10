package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AccountRequestEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AccountRequestRepository extends JpaRepository<AccountRequestEntity, String> {
    @Query(
            value =
                    """
                            DELETE FROM t_account_request
                            WHERE pk_account_request_id = :id
                            RETURNING t_account_request.*
                            """,
            nativeQuery = true)
    @Modifying
    Optional<AccountRequestEntity> findAndDeleteById(String id);

    void deleteAllByValidUntilBefore(OffsetDateTime validUntilBefore);
}
