package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AccountRequestEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRequestRepository extends JpaRepository<AccountRequestEntity, String> {
    Optional<AccountRequestEntity> findAndDeleteById(String id);
}
