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

    // Fallback for when the exact manufacturer-name match above misses but this is really the
    // same physical computer, reported under a differently-spelled manufacturer name by a
    // different import path (e.g. "Shearwater" vs "Shearwater Research, Inc" for the same real
    // company, from the native-XML reader's hardcoded short name vs that company's own UDDF
    // export). Serial number must still match exactly - only the manufacturer name is fuzzy:
    // either name (trimmed, case-insensitive) contained in the other, or a close typo/
    // capitalization match via pg_trgm similarity (same `%`/similarity() operators already used
    // by findAllByCustomIdentifierAndUser_Id above, backed by
    // idx_computer_manufacturer_name_trgm).
    @Query(
            value =
                    """
                    SELECT dc.* FROM t_dive_computer dc
                    JOIN t_computer_manufacturer m ON m.pk_manufacturer_id = dc.fk_manufacturer_id
                    WHERE dc.fk_user_id = :userId
                      AND dc.serial_number = :serialNumber
                      AND (
                        position(lower(trim(m.name)) IN lower(trim(:manufacturerName))) > 0
                        OR position(lower(trim(:manufacturerName)) IN lower(trim(m.name))) > 0
                        OR m.name % :manufacturerName
                      )
                    ORDER BY similarity(m.name, :manufacturerName) DESC
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<DiveComputerEntity> findByUser_IdAndSerialNumberAndManufacturer_NameFuzzy(
            long userId, String serialNumber, String manufacturerName);

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

    // Used when deleting a CCR unit - unlinks any computer permanently paired with it (see
    // DiveComputerEntity.ccrUnit) rather than blocking or cascading the unit's own delete.
    @Query("UPDATE DiveComputerEntity d SET d.ccrUnit = NULL WHERE d.ccrUnit.id = :ccrUnitId")
    @Modifying(clearAutomatically = true)
    void clearCcrUnitFromComputers(long ccrUnitId);
}
