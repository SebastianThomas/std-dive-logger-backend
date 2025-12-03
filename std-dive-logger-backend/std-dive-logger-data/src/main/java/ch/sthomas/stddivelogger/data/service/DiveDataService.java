package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.exception.DiveConstraintException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;

@Service
public class DiveDataService {

    private static final Logger logger = LoggerFactory.getLogger(DiveDataService.class);

    private final EntityManager entityManager;
    private final DiveRepository diveRepository;
    private final UserRepository userRepository;
    private final DiveSiteRepository diveSiteRepository;
    private final DiveComputerRepository diveComputerRepository;
    private final DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final StorageService storageService;

    public DiveDataService(
            final EntityManager entityManager,
            final DiveRepository diveRepository,
            final UserRepository userRepository,
            final DiveSiteRepository diveSiteRepository,
            final DiveComputerRepository diveComputerRepository,
            final DiveComputerManufacturerRepository diveComputerManufacturerRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            final StorageService storageService) {
        this.entityManager = entityManager;
        this.diveRepository = diveRepository;
        this.userRepository = userRepository;
        this.diveSiteRepository = diveSiteRepository;
        this.diveComputerRepository = diveComputerRepository;
        this.diveComputerManufacturerRepository = diveComputerManufacturerRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;

        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByUser(
            final User user, final int page, final int pageSize) {
        final var result =
                diveRepository.findByUser_IdOrderByNumberDesc(
                        user.id(), Pageable.ofSize(pageSize).withPage(page));
        return PagedResponse.of(result, d -> d.toSimplifiedRecord(storageService.baseUrl(), true));
    }

    @Transactional(readOnly = true)
    public Optional<Dive> findDiveById(final long id) {
        return diveRepository.findById(id).map(d -> d.toRecord(storageService.baseUrl(), true));
    }

    @Transactional(readOnly = true)
    public Optional<SimplifiedDive> findSimplifiedDiveById(final long id) {
        return diveRepository
                .findById(id)
                .map(d -> d.toSimplifiedRecord(storageService.baseUrl(), true));
    }

    @Transactional
    public Dive saveDive(
            final User user,
            final int number,
            final String diveIdentifier,
            final String previewImage,
            final long diveSiteId,
            final List<DiveProfileUpload> profiles,
            final List<String> namedBuddies)
            throws NoSuchElementException {
        final var userEntity = userRepository.findById(user.id()).orElseThrow();
        final var diveSite = diveSiteRepository.findById(diveSiteId).orElseThrow();

        final var profileEntities = profiles.stream().map(this::createDiveProfileEntity).toList();
        final var entity =
                new DiveEntity(
                        number,
                        diveIdentifier,
                        userEntity,
                        previewImage,
                        diveSite,
                        profileEntities,
                        namedBuddies);
        try {
            return diveRepository.save(entity).toRecord(storageService.baseUrl(), true);
        } catch (final DataIntegrityViolationException e) {
            throw new DiveConstraintException("Could not save dive", e);
        }
    }

    @Transactional
    public void saveBuddies(final long diveId, final List<String> buddies) {
        entityManager.flush();

        namedParameterJdbcTemplate.batchUpdate(
                "INSERT INTO t_dive_buddy_name (fk_dive_id, name) VALUES (:diveId, :buddyName)",
                buddies.stream()
                        .map(
                                buddy ->
                                        new MapSqlParameterSource()
                                                .addValue("diveId", diveId)
                                                .addValue("buddyName", buddy))
                        .toArray(MapSqlParameterSource[]::new));

        // TODO: Add clear here if dive is not reloaded with all buddies
        // entityManager.clear();
    }

    @Transactional
    protected DiveProfileEntity createDiveProfileEntity(final DiveProfileUpload diveProfileUpload) {
        final var computer =
                diveComputerRepository.findById(diveProfileUpload.diveComputerId()).orElseThrow();
        return new DiveProfileEntity(
                computer,
                diveProfileUpload.start(),
                diveProfileUpload.end(),
                diveProfileUpload.measurements().stream().map(DiveMeasurementEntity::new).toList());
    }

    @Transactional(readOnly = true)
    public Optional<DiveSite> findDiveSiteByName(final String diveSite) {
        return diveSiteRepository.findByNameIgnoreCase(diveSite).map(DiveSiteEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiveSite> findDiveSiteByNameContains(
            final String partialName, final int page, final int pageSize) {
        return PagedResponse.of(
                diveSiteRepository.findByClosestMatchName(
                        partialName, PageRequest.of(page, pageSize)),
                DiveSiteEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<DiveComputer> findDiveComputerByUserAndName(
            final long userId, final String customName) {
        return diveComputerRepository
                .findByCustomIdentifierAndUser_Id(customName, userId)
                .map(DiveComputerEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<DiveComputer> findDiveComputerByUserAndSerialNumber(
            final long userId, final String manufacturer, final String serialNumber) {
        return diveComputerRepository
                .findByUser_IdAndManufacturer_NameAndSerialNumber(
                        userId, manufacturer, serialNumber)
                .map(DiveComputerEntity::toRecord);
    }

    @Transactional
    public DiveComputer saveDiveComputer(
            @Nullable final String serialNumber,
            @NotNull final String customIdentifier,
            final String manufacturer,
            final long userId) {
        final var userEntity = userRepository.findById(userId).orElseThrow();
        final var manufacturerEntity =
                diveComputerManufacturerRepository
                        .findByNameIgnoreCase(manufacturer)
                        .orElseGet(
                                () ->
                                        diveComputerManufacturerRepository.save(
                                                new DiveComputerManufacturerEntity(manufacturer)));
        return diveComputerRepository
                .save(
                        new DiveComputerEntity(
                                serialNumber, customIdentifier, manufacturerEntity, userEntity))
                .toRecord();
    }

    @Transactional(readOnly = true)
    public long getDiveCount() {
        return diveRepository.count();
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserForDive(final long diveId) {
        return diveRepository
                .findById(diveId)
                .map(DiveEntity::getUserEntity)
                .map(UserEntity::toRecord);
    }

    @Transactional
    public Dive updateDive(@NotNull @Valid final Dive dive) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        final var diveSiteEntity =
                Optional.ofNullable(dive.site())
                        .map(DiveSite::id)
                        .flatMap(diveSiteRepository::findById)
                        .orElse(null);
        return diveRepository
                .save(existingDive.update(dive.number(), dive.customIdentifier(), diveSiteEntity))
                .toRecord(storageService.baseUrl(), true);
    }

    @Transactional
    public Dive updateDiveSetPreviewImage(
            @NotNull @Valid final Dive dive, final String previewImage) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        existingDive.setPreviewImage(previewImage);
        return diveRepository.save(existingDive).toRecord(storageService.baseUrl(), true);
    }

    @Transactional
    public Dive addProfilesToDive(final long baseDiveId, final long toAddDiveId) {
        final var baseDiveEntity = diveRepository.findById(baseDiveId).orElseThrow();
        final var toAddDiveEntity = diveRepository.findById(toAddDiveId).orElseThrow();
        baseDiveEntity.addProfiles(toAddDiveEntity.getProfiles());
        return diveRepository.save(baseDiveEntity).toRecord(storageService.baseUrl(), true);
    }

    @Transactional
    public void deleteDiveById(final long toAddDiveId) {
        diveRepository.deleteById(toAddDiveId);
    }

    @Transactional
    public List<Dive> findDivesByProfileIds(final List<Long> profileIds) {
        return diveRepository.findByProfileIds(profileIds).stream()
                .map(d -> d.toRecord(storageService.baseUrl(), true))
                .toList();
    }

    @Transactional
    public PagedResponse<SimplifiedDive> findByIdentifierContains(
            final long userId, final String identifier, final Pageable pageable) {
        return PagedResponse.of(
                diveRepository.findByIdentifier(userId, identifier, pageable),
                d -> d.toSimplifiedRecord(storageService.baseUrl(), true));
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> searchDives(
            final long userId, final String query, final Pageable pageable) {
        return PagedResponse.of(
                diveRepository.searchDives(userId, query, pageable),
                d -> d.toSimplifiedRecord(storageService.baseUrl(), true));
    }

    @Transactional
    public Dive moveProfiles(final Long targetDiveId, final List<Long> profileIds) {
        diveRepository.setDiveIdWhereProfileIdIn(targetDiveId, profileIds);
        return diveRepository
                .findById(targetDiveId)
                .map(d -> d.toRecord(storageService.baseUrl(), true))
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<DiveSite> findDiveSiteById(final long id) {
        return diveSiteRepository.findById(id).map(DiveSiteEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public List<DiveSite> findDiveSitesByLocation(final Coordinate coordinate) {
        return findDiveSiteByLocationDistanceWithin(coordinate, 0.005);
    }

    @Transactional(readOnly = true)
    public List<DiveSite> findDiveSiteByLocationDistanceWithin(
            final Coordinate coordinate, final double dist) {
        return diveSiteRepository.findByLocationNear(coordinate, dist).stream()
                .map(DiveSiteEntity::toRecord)
                .toList();
    }

    @Transactional
    public DiveSite saveDiveSite(final String name, final Location coordinate) {
        return diveSiteRepository.save(new DiveSiteEntity(name, coordinate.toPoint())).toRecord();
    }

    @Transactional(readOnly = true)
    public boolean hasReadAccess(@NotNull final User user, final long diveId) {
        return userRepository.isReader(diveId, user.id());
    }

    @Transactional(readOnly = true)
    public PagedResponse<User> findReaders(final long diveId, final Pageable pageable) {
        return PagedResponse.of(userRepository.findReaders(diveId, pageable), UserEntity::toRecord);
    }

    @Transactional
    public void saveReaders(final long diveId, final Collection<Long> userIds) {
        entityManager.flush();

        namedParameterJdbcTemplate.batchUpdate(
                "INSERT INTO t_dive_privileges (fk_dive_id, fk_user_id) VALUES (:diveId, :userId)",
                userIds.stream()
                        .map(
                                userId ->
                                        new MapSqlParameterSource()
                                                .addValue("diveId", diveId)
                                                .addValue("userId", userId))
                        .toArray(MapSqlParameterSource[]::new));

        // TODO: Add clear here if dive is not reloaded with privileges
        // entityManager.clear();
    }

    @Transactional
    public void removeReaders(final long diveId, final Collection<Long> userIdsSet) {
        if (userIdsSet.isEmpty()) {
            return;
        }
        entityManager.flush();

        namedParameterJdbcTemplate.update(
                "DELETE FROM t_dive_privileges WHERE fk_dive_id = :diveId AND fk_user_id IN (:userIds)",
                new MapSqlParameterSource()
                        .addValue("diveId", diveId)
                        .addValue("userIds", userIdsSet));
    }

    @Transactional
    public void saveGroupReader(final long diveId, final long groupId) {
        try {
            entityManager.flush();
            namedParameterJdbcTemplate.update(
                    "INSERT INTO t_dive_privileges_groups (fk_dive_id, fk_group_id) VALUES (:diveId, :groupId)",
                    new MapSqlParameterSource()
                            .addValue("diveId", diveId)
                            .addValue("groupId", groupId));
        } catch (final DataIntegrityViolationException e) {
            logger.error("Could not add group reader dive {} -> group {}", diveId, groupId, e);
            throw new IllegalArgumentException("Could not save group read privileges.");
        }
    }

    @Transactional
    public void removeGroupReader(final long diveId, final long groupId) {
        try {
            entityManager.flush();
            namedParameterJdbcTemplate.update(
                    "DELETE FROM t_dive_privileges_groups g WHERE g.fk_dive_id = :diveId AND g.fk_group_id = :groupId",
                    new MapSqlParameterSource()
                            .addValue("diveId", diveId)
                            .addValue("groupId", groupId));
        } catch (final DataIntegrityViolationException e) {
            logger.error("Could not remove group reader dive {} -> group {}", diveId, groupId, e);
            throw new IllegalArgumentException("Could not remove group read privileges.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findMaxDiveNumber(final User user) {
        return diveRepository.findMaxDiveNumberByUserId(user.id());
    }

    @Transactional(readOnly = true)
    public List<DiveSiteWithDives<DiveSite, List<Long>>> findDiveSitesByUser(final long userId) {
        return findDiveSiteEntitiesByUser(userId).stream()
                .map(
                        d ->
                                new DiveSiteWithDives<>(
                                        d.site().toRecord(), getLongListFromSqlObject(d.diveIds())))
                .toList();
    }

    private List<DiveSiteWithDives<DiveSiteEntity, List<Long>>> findDiveSiteEntitiesByUser(
            final long userId) {
        return diveSiteRepository.findSitesByDiveWithUserId(userId).stream()
                .map(
                        row -> {
                            logger.info(
                                    "DiveSite (should be entity?): {} {}",
                                    row[0].getClass(),
                                    row[0]);
                            logger.info("IDs: {}, {}", row[1].getClass(), row[1]);
                            final var site = (DiveSiteEntity) row[0];
                            final var diveIds = getLongListFromSqlObject(row[1]);
                            return new DiveSiteWithDives<>(site, diveIds);
                        })
                .toList();
    }

    private List<Long> getLongListFromSqlObject(final Object o) {
        return switch (o) {
            case final Array sqlArray -> {
                try {
                    yield Arrays.stream((Long[]) sqlArray.getArray()).toList();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            case final Long[] longArr -> Arrays.asList(longArr);
            case null -> Collections.emptyList();
            default -> {
                logger.warn("Unrecognized SQL object (tried to get as List<Long>): {}", o);
                yield Collections.emptyList();
            }
        };
    }
}
