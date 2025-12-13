package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasMixEntity;
import ch.sthomas.stddivelogger.model.exception.DiveConstraintException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;

import com.google.common.collect.MoreCollectors;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DiveDataService {

    private static final Logger logger = LoggerFactory.getLogger(DiveDataService.class);

    public static final double MIN_DIVE_SITE_DIST = 0.005;

    private final EntityManager entityManager;
    private final DiveRepository diveRepository;
    private final UserRepository userRepository;
    private final DiveSiteRepository diveSiteRepository;
    private final DiveComputerRepository diveComputerRepository;
    private final DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final StorageService storageService;
    private final DiveBuddyNameRepository diveBuddyNameRepository;
    private final CylinderSizeRepository cylinderSizeRepository;
    private final GasMixRepository gasMixRepository;
    private final GasRepository gasRepository;

    public DiveDataService(
            final EntityManager entityManager,
            final DiveRepository diveRepository,
            final UserRepository userRepository,
            final DiveSiteRepository diveSiteRepository,
            final DiveComputerRepository diveComputerRepository,
            final DiveComputerManufacturerRepository diveComputerManufacturerRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            final StorageService storageService,
            final DiveBuddyNameRepository diveBuddyNameRepository,
            final CylinderSizeRepository cylinderSizeRepository,
            GasMixRepository gasMixRepository,
            GasRepository gasRepository) {
        this.entityManager = entityManager;
        this.diveRepository = diveRepository;
        this.userRepository = userRepository;
        this.diveSiteRepository = diveSiteRepository;
        this.diveComputerRepository = diveComputerRepository;
        this.diveComputerManufacturerRepository = diveComputerManufacturerRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;

        this.storageService = storageService;
        this.diveBuddyNameRepository = diveBuddyNameRepository;
        this.cylinderSizeRepository = cylinderSizeRepository;
        this.gasMixRepository = gasMixRepository;
        this.gasRepository = gasRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByUser(
            final User user, @NotNull final DiveSort diveSort, final int page, final int pageSize) {
        final var result =
                diveRepository.findByUser_Id(
                        user.id(), PageRequest.of(page, pageSize, toSort(diveSort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByUserAndComputer(
            final User user,
            final DiveComputer computer,
            final DiveSort diveSort,
            final int page,
            final int pageSize) {
        final var result =
                diveRepository.findByUser_IdAndComputer(
                        user.id(), computer.id(), PageRequest.of(page, pageSize, toSort(diveSort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public Optional<Dive> findDiveById(final long id) {
        return diveRepository.findById(id).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<SimplifiedDive> findDivesByIds(final User user, final List<Long> ids) {
        return diveRepository.findAllByIdAndIsReader(user.id(), ids).stream()
                .map(this::toSimplifiedRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SimplifiedDive> findSimplifiedDiveById(final long id) {
        return diveRepository.findById(id).map(this::toSimplifiedRecord);
    }

    @Transactional
    public Dive saveDive(
            final User user,
            final int number,
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
                        null,
                        userEntity,
                        previewImage,
                        diveSite,
                        profileEntities,
                        namedBuddies);
        try {
            return toRecord(diveRepository.save(entity));
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
                        .distinct()
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
                diveProfileUpload.measurements().stream()
                        .map(m -> new DiveMeasurementEntity(m, toEntity(m.gas())))
                        .toList());
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
    public Optional<DiveComputer> findDiveComputerByUserAndId(
            final long userId, final long computerId) {
        return diveComputerRepository.findByIdAndUser_Id(computerId, userId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiveComputer> findDiveComputersByUser(
            final long userId, final int page, final int pageSize) {
        return PagedResponse.of(
                diveComputerRepository.findByUser_Id(userId, PageRequest.of(page, pageSize)),
                DiveComputerEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiveComputer> findDiveComputersByUserAndName(
            final long userId, final String customName, final int page, final int pageSize) {
        return PagedResponse.of(
                diveComputerRepository.findAllByCustomIdentifierAndUser_Id(
                        userId, customName, PageRequest.of(page, pageSize)),
                DiveComputerEntity::toRecord);
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
    public Dive updateDive(final @NotNull @Valid UpdateDiveBody dive) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        final var diveSiteEntity =
                Optional.ofNullable(dive.siteId())
                        .flatMap(diveSiteRepository::findById)
                        .orElse(null);
        final var namedBuddies =
                existingDive.getNamedBuddies().stream()
                        .collect(
                                Collectors.toMap(
                                        DiveBuddyNameEntity::getName, Function.identity()));
        final var newBuddies = getNewNamedBuddies(dive, namedBuddies, existingDive);
        return toRecord(
                diveRepository.save(
                        existingDive.update(
                                dive.number(),
                                dive.customIdentifier(),
                                diveSiteEntity,
                                newBuddies)));
    }

    private ArrayList<DiveBuddyNameEntity> getNewNamedBuddies(
            final UpdateDiveBody dive,
            final Map<String, DiveBuddyNameEntity> namedBuddies,
            final DiveEntity existingDive) {
        if (dive.namedBuddies() == null) {
            return null;
        }
        return dive.namedBuddies().stream()
                .map(n -> getOldOrNewBuddy(dive, namedBuddies, existingDive, n))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private @NonNull DiveBuddyNameEntity getOldOrNewBuddy(
            final UpdateDiveBody dive,
            final Map<String, DiveBuddyNameEntity> namedBuddies,
            final DiveEntity existingDive,
            final String n) {
        return Optional.ofNullable(namedBuddies.get(n))
                .or(() -> diveBuddyNameRepository.findByDive_IdAndName(dive.id(), n))
                .orElseGet(
                        () ->
                                diveBuddyNameRepository.save(
                                        new DiveBuddyNameEntity(existingDive, n)));
    }

    @Transactional
    public Dive updateDiveSetPreviewImage(
            @NotNull @Valid final Dive dive, final String previewImage) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        existingDive.setPreviewImage(previewImage);
        return toRecord(diveRepository.save(existingDive));
    }

    @Transactional
    public Dive addProfilesToDive(final long baseDiveId, final long toAddDiveId) {
        final var baseDiveEntity = diveRepository.findById(baseDiveId).orElseThrow();
        final var toAddDiveEntity = diveRepository.findById(toAddDiveId).orElseThrow();
        baseDiveEntity.addProfiles(toAddDiveEntity.getProfiles());
        return toRecord(diveRepository.save(baseDiveEntity));
    }

    @Transactional
    public void deleteDiveById(final long toAddDiveId) {
        diveRepository.deleteById(toAddDiveId);
    }

    @Transactional
    public List<Dive> findDivesByProfileIds(final List<Long> profileIds) {
        return diveRepository.findByProfileIds(profileIds).stream().map(this::toRecord).toList();
    }

    @Transactional
    public PagedResponse<SimplifiedDive> findByIdentifierContains(
            final long userId, final String identifier, final Pageable pageable) {
        return PagedResponse.of(
                diveRepository.findByIdentifier(userId, identifier, pageable),
                this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> searchDives(
            final long userId, final String query, final Pageable pageable) {
        return PagedResponse.of(
                diveRepository.searchDives(userId, query, pageable), this::toSimplifiedRecord);
    }

    @Transactional
    public Dive moveProfiles(final Long targetDiveId, final List<Long> profileIds) {
        diveRepository.setDiveIdWhereProfileIdIn(targetDiveId, profileIds);
        return diveRepository.findById(targetDiveId).map(this::toRecord).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<DiveSite> findDiveSiteById(final long id) {
        return diveSiteRepository.findById(id).map(DiveSiteEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public List<DiveSite> findDiveSitesByLocation(final Location coordinate) {
        return findDiveSiteByLocationDistanceWithin(coordinate, MIN_DIVE_SITE_DIST);
    }

    @Transactional(readOnly = true)
    public List<DiveSite> findDiveSiteByLocationDistanceWithin(
            final Location coordinate, final double dist) {
        return diveSiteRepository.findByLocationNear(coordinate.toPoint(), dist).stream()
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
    public List<DiveSiteWithDives<DiveSite, List<Long>>> findDiveSitesByUser(
            final long userId, final boolean onlyOwn) {
        return findDiveSiteEntitiesByUser(userId, onlyOwn).stream()
                .map(d -> new DiveSiteWithDives<>(d.site().toRecord(), d.diveIds()))
                .toList();
    }

    @Transactional
    public GasEntity toEntity(final Gas gas) {
        final var size = Optional.ofNullable(gas.size()).map(this::toEntity);
        final var mix = toEntity(gas.o2(), gas.n2(), gas.he());
        final var entity = new GasEntity(gas, mix, size.orElse(null));
        return gasRepository.findAll(Example.of(entity)).stream()
                .collect(MoreCollectors.toOptional())
                .orElseGet(() -> gasRepository.save(entity));
    }

    @Transactional
    public CylinderSizeEntity toEntity(final CylinderSize cylinderSize) {
        return cylinderSizeRepository
                .findByUnitAndValue(cylinderSize.unit(), cylinderSize.value())
                .orElseGet(() -> cylinderSizeRepository.save(new CylinderSizeEntity(cylinderSize)));
    }

    @Transactional
    public GasMixEntity toEntity(final double o2, final double n2, final double he) {
        return gasMixRepository
                .findByO2AndN2AndHe(o2, n2, he)
                .orElseGet(() -> gasMixRepository.save(new GasMixEntity(o2, n2, he)));
    }

    private List<DiveSiteWithDives<DiveSiteEntity, List<Long>>> findDiveSiteEntitiesByUser(
            final long userId, final boolean onlyOwn) {
        final var result =
                onlyOwn
                        ? diveSiteRepository.findSitesByDiveWithUserId(userId)
                        : diveSiteRepository.findSitesByDiveWithReaderUserId(userId);
        return result.stream()
                .map(
                        row -> {
                            final var site = (DiveSiteEntity) row[0];
                            final var diveIds = getLongListFromSqlObject(row[1]);
                            return new DiveSiteWithDives<>(site, diveIds);
                        })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> getLongListFromSqlObject(final Object o) {
        if (o == null) {
            return Collections.emptyList();
        }
        try {
            return switch (o) {
                case final Array sqlArray -> {
                    try {
                        yield Arrays.stream((Long[]) sqlArray.getArray()).toList();
                    } catch (final SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
                case final Long[] longArr -> Arrays.asList(longArr);
                case final long[] longArr -> Arrays.stream(longArr).boxed().toList();
                case final List<?> list -> (List<Long>) list;
                case final Collection<?> collection ->
                        collection.stream().map(l -> (Long) l).toList();
                default -> {
                    logger.warn(
                            "Unrecognized SQL object (tried to convert to List<Long>): class {} with value {}",
                            o.getClass(),
                            o);
                    yield Collections.emptyList();
                }
            };
        } catch (final ClassCastException e) {
            logger.warn("Could not convert {} to List<Long>", o.getClass(), e);
            return Collections.emptyList();
        }
    }

    public Dive linkDive(final long userDiveId, final long buddyDiveId) {
        final var userDive =
                diveRepository
                        .findById(userDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + userDiveId));
        final var buddyDive =
                diveRepository
                        .findById(buddyDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + buddyDiveId));
        if (userDive.hasBuddyDive(buddyDiveId)) {
            return toRecord(userDive);
        }
        userDive.addBuddyDive(buddyDive);
        diveRepository.saveAll(List.of(userDive, buddyDive));
        return toRecord(userDive);
    }

    public Dive unlinkDive(final long userDiveId, final long buddyDiveId) {
        final var userDive =
                diveRepository
                        .findById(userDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + userDiveId));
        final var buddyDive =
                diveRepository
                        .findById(buddyDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + buddyDiveId));
        if (!userDive.hasBuddyDive(buddyDiveId)) {
            return toRecord(userDive);
        }
        userDive.removeBuddyDive(buddyDive);
        diveRepository.saveAll(List.of(userDive, buddyDive));
        return toRecord(userDive);
    }

    private Dive toRecord(final DiveEntity e) {
        return e.toRecord(storageService.baseUrl(), true);
    }

    private SimplifiedDive toSimplifiedRecord(final DiveEntity e) {
        return e.toSimplifiedRecord(storageService.baseUrl(), true);
    }

    private Sort toSort(final DiveSort sort) {
        final var s = Sort.by(sort.column().jpaName());
        return switch (sort.direction()) {
            case ASCENDING -> s.ascending();
            case DESCENDING -> s.descending();
        };
    }
}
