package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiveComputerRepository extends JpaRepository<DiveComputerEntity, Long> {
    Optional<DiveComputerEntity> findByCustomIdentifierAndUser_Id(
            String customIdentifier, Long userId);

    Optional<DiveComputerEntity> findByUser_IdAndManufacturer_NameAndSerialNumber(
            Long userId, String manufacturerName, String serialNumber);

    Long user(UserEntity user);

    Page<DiveComputerEntity> findByUser_Id(Long userId, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM t_dive_computer WHERE custom_identifier % :customName ORDER BY similarity(custom_identifier, :customName) DESC, LENGTH(custom_identifier) ASC",
            countQuery = "SELECT * FROM t_dive_computer WHERE custom_identifier % :customName",
            nativeQuery = true)
    Page<DiveComputerEntity> findAllByCustomIdentifierAndUser_Id(
            long userId, String customName, Pageable pageable);

    Optional<DiveComputerEntity> findByIdAndUser_Id(Long id, Long userId);

    @Query("DELETE FROM DiveComputerEntity c WHERE c.user.id = :userId AND SIZE(c.profiles) = 0")
    @Modifying
    int deleteAllByUser_IdAndProfilesIsEmpty(Long userId);
}
