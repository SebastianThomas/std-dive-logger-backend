package ch.sthomas.stddivelogger.data.service;

import static org.apache.commons.lang3.StringUtils.isNumeric;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.*;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;
import ch.sthomas.stddivelogger.model.entity.gas.GasMixEntity;
import ch.sthomas.stddivelogger.model.exception.DBResult;
import ch.sthomas.stddivelogger.model.exception.DiveDBConstraintException;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.utils.LocationUtils;

import com.google.common.collect.MoreCollectors;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.query.SortDirection;
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
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final DiveBuddyNameRepository diveBuddyNameRepository;
    private final CylinderSizeRepository cylinderSizeRepository;
    private final GasMixRepository gasMixRepository;
    private final GasRepository gasRepository;
    private final GroupRepository groupRepository;
    private final ReaderViewRepository readerViewRepository;
    private final DiveProfileRepository diveProfileRepository;
    private final DiveProfileHistoryRepository diveProfileHistoryRepository;
    private final SuitRepository suitRepository;
    private final TagDataService tagDataService;
    private final DiveTagRepository diveTagRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;

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
            final GasMixRepository gasMixRepository,
            final GasRepository gasRepository,
            final GroupRepository groupRepository,
            final ReaderViewRepository readerViewRepository,
            final DiveProfileRepository diveProfileRepository,
            final DiveProfileHistoryRepository diveProfileHistoryRepository,
            final SuitRepository suitRepository,
            final TagDataService tagDataService,
            final DiveTagRepository diveTagRepository,
            final DiveMeasurementRepository diveMeasurementRepository) {
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
        this.groupRepository = groupRepository;
        this.readerViewRepository = readerViewRepository;
        this.diveProfileRepository = diveProfileRepository;
        this.diveProfileHistoryRepository = diveProfileHistoryRepository;
        this.suitRepository = suitRepository;
        this.tagDataService = tagDataService;
        this.diveTagRepository = diveTagRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
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
    public PagedResponse<SimplifiedDive> findDivesByUserIsReader(
            final User user, @NotNull final DiveSort diveSort, final int page, final int pageSize) {
        return PagedResponse.of(
                readerViewRepository.findByUser_Id(
                        user.id(), PageRequest.of(page, pageSize, toReaderSort(diveSort))),
                r -> toSimplifiedRecord(r.getDive()));
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
    public PagedResponse<SimplifiedDive> findDivesByUserAndSuit(
            final User user,
            final Suit suit,
            final DiveSort diveSort,
            final int page,
            final int pageSize) {
        final var result =
                diveRepository.findByUser_IdAndConfiguration_Suit_Id(
                        user.id(), suit.id(), PageRequest.of(page, pageSize, toSort(diveSort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public Optional<Dive> findDiveById(final long id) {
        return diveRepository.findById(id).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public DiveEntity findDiveEntityById(final long diveId) {
        return diveRepository
                .findById(diveId)
                .orElseThrow(
                        () -> new NoSuchElementException("Could not find dive by id " + diveId));
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
    public DBResult<Dive> saveDive(
            final User user,
            final int number,
            @NotNull final String diveIdentifier,
            final String notes,
            @Nullable final Visibility visibility,
            final DiveGasConsumption gasConsumption,
            final DiveConfiguration configuration,
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
                        notes,
                        Optional.ofNullable(visibility).orElse(Visibility.EMPTY),
                        gasConsumption,
                        findOrCreateSuit(userEntity, configuration.suit()),
                        configuration,
                        userEntity,
                        diveSite,
                        profileEntities,
                        namedBuddies,
                        this::toEntity);
        try {
            recomputeAutoTags(entity, user.id());
            final var savedDive = diveRepository.save(entity);
            savedDive
                    .getProfiles()
                    .forEach(
                            profileEntity ->
                                    diveProfileHistoryRepository.save(
                                            new DiveProfileHistoryEntity(profileEntity)));
            return new DBResult<>(toRecord(savedDive), null);
        } catch (final DataIntegrityViolationException e) {
            return new DBResult<>(
                    null,
                    new DiveDBConstraintException(
                            "Could not save dive, it seems to be a duplicate.", e));
        }
    }

    @Transactional
    public SuitEntity findOrCreateSuit(final UserEntity user, final Suit suit) {
        if (suit.id() != null) {
            return suitRepository
                    .findByIdAndUser_Id(suit.id(), user.getId())
                    .orElseThrow(
                            () ->
                                    new NoSuchElementException(
                                            "Could not find suit by id " + suit.id()));
        }
        final var existing =
                suitRepository.findByUser_IdAndTypeAndThicknessMMAndAdditionalNotes(
                        user.getId(), suit.type(), suit.thickness(), suit.notes());
        return existing.orElseGet(() -> suitRepository.save(new SuitEntity(user, suit)));
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
                toMeasurementEntities(diveProfileUpload.measurements()));
    }

    private List<DiveMeasurementEntity> toMeasurementEntities(
            final List<DiveMeasurement> measurements) {
        return measurements.stream()
                .map(
                        m ->
                                new DiveMeasurementEntity(
                                        m, Optional.ofNullable(m.gas()).map(this::toEntity).orElse(null)))
                .toList();
    }

    /**
     * Replaces only the raw measurement data (and start/end) of an existing profile, leaving the
     * parent dive's other properties (suit, gas consumption, weight, visibility, notes, tags,
     * buddies, ...) untouched. Intended as a recovery tool for fixing parser bugs after the fact.
     */
    @Transactional
    public Dive reimportProfileMeasurements(
            final long diveId,
            final long profileId,
            final List<DiveMeasurement> newMeasurements,
            final Instant start,
            final Instant end) {
        final var dive = findDiveEntityById(diveId);
        final var profile =
                dive.getProfiles().stream()
                        .filter(p -> p.getId() == profileId)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find profile "
                                                        + profileId
                                                        + " on dive "
                                                        + diveId));
        // Replace all existing rows atomically: delete then insert via repository,
        // completely bypassing the entity's managed measurements collection (no orphanRemoval).
        diveMeasurementRepository.deleteAllByProfile_Id(profileId);
        diveMeasurementRepository.flush();
        profile.replaceMeasurements(toMeasurementEntities(newMeasurements), start, end);
        return toRecord(diveRepository.save(dive));
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
        return diveComputerRepository
                .findByIdAndUser_Id(computerId, userId)
                .map(DiveComputerEntity::toRecord);
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
    public Dive updateDive(final User user, final @NotNull @Valid UpdateDiveBody updateBody) {
        final var existingDive = diveRepository.findById(updateBody.id()).orElseThrow();
        final var diveSiteEntity =
                Optional.ofNullable(updateBody.siteId())
                        .flatMap(diveSiteRepository::findById)
                        .orElse(null);
        final var namedBuddies =
                existingDive.getNamedBuddies().stream()
                        .collect(
                                Collectors.toMap(
                                        DiveBuddyNameEntity::getName, Function.identity()));
        final var newBuddies = getNewNamedBuddies(updateBody, namedBuddies, existingDive);
        // Mutate the *existing* configuration entity in-place to avoid a Hibernate
        // DuplicateKeyException caused by @MapsId sharing the dive PK.
        if (updateBody.configuration() != null) {
            final var suit = suitRepository
                    .findByIdAndUser_Id(updateBody.suitId(), user.id())
                    .orElseThrow(() -> new NoSuchElementException("Could not find Suit"));
            if (existingDive.getConfiguration() != null) {
                existingDive.getConfiguration().update(suit, updateBody.configuration(), this::toEntity);
            } else {
                existingDive.setConfiguration(
                        new DiveConfigurationEntity(existingDive, suit, updateBody.configuration(), this::toEntity));
            }
            logger.info("Set new configuration with suit: {}, {}", suit, suit.getType());
        }
        // Mutate gasConsumption in-place (same @MapsId constraint as configuration).
        if (updateBody.gasConsumption() != null) {
            if (existingDive.getGasConsumption() != null) {
                existingDive.getGasConsumption().update(updateBody.gasConsumption());
            } else {
                existingDive.setGasConsumption(
                        new DiveGasConsumptionEntity(existingDive, updateBody.gasConsumption()));
            }
        }
        // Mutate visibility in-place (same @MapsId constraint as configuration).
        if (updateBody.visibility() != null) {
            if (existingDive.getVisibility() != null) {
                existingDive.getVisibility().update(updateBody.visibility());
            } else {
                existingDive.setVisibility(
                        new VisibilityEntity(existingDive, updateBody.visibility()));
            }
        }
        // All @MapsId child entities already mutated above — pass null to avoid re-assignment.
        existingDive.update(
                updateBody.number(),
                updateBody.customIdentifier(),
                updateBody.notes(),
                diveSiteEntity,
                newBuddies,
                null,   // configuration mutated in-place above
                null,   // gasConsumption mutated in-place above
                null);  // visibility mutated in-place above
        return toRecord(diveRepository.save(existingDive));
    }

    /**
     * Refreshes the auto-detected tags for a dive and returns the updated dive.
     * Manual and dismissed tag rows are preserved; only the active auto-detected rows
     * are replaced. This is called by the frontend when opening the edit page so that
     * the user always sees up-to-date auto-tags before editing.
     */
    @Transactional
    public Dive refreshAutoTags(final long diveId, final long userId) {
        // Fetch auto-detect defs before loading the entity to avoid auto-flush on a
        // dirty tags collection (Hibernate insert-before-delete ordering issue).
        final var autoDetectDefs = tagDataService.findAutoDetectEntitiesForUser(userId);
        final var coveredTagIds = diveTagRepository.findCoveredTagIdsByDiveId(diveId);
        final var dive = diveRepository.findByIdAndUser_Id(diveId, userId).orElseThrow();

        diveTagRepository.deleteActiveAutoTagsByDiveId(diveId);
        diveTagRepository.flush();

        final var newAutoTags = autoDetectDefs.stream()
                .filter(def -> dive.matchesAutoDetect(def.getAutoDetectRule()))
                .filter(def -> !coveredTagIds.contains(def.getId()))
                .map(def -> new DiveTagEntity(dive, def, false, false))
                .toList();
        diveTagRepository.saveAll(newAutoTags);

        // Clear the 1st-level cache so the reload below fetches fresh rows from DB
        // rather than returning the stale entity that was loaded before the tag operations.
        entityManager.clear();
        return toRecord(diveRepository.findByIdAndUser_Id(diveId, userId).orElseThrow());
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
    public Dive updateTags(
            final long diveId,
            final long userId,
            final List<Long> manualTagIds,
            final List<Long> dismissedAutoTagIds) {
        // Resolve all tag definitions before touching the dive entity, so that no
        // Hibernate auto-flush can occur while the tag collection is in a transient state.
        final var manualTagDefs = tagDataService.findEntitiesByIdsVisibleToUser(manualTagIds, userId);
        final var autoDetectDefs = tagDataService.findAutoDetectEntitiesForUser(userId);

        // Load the dive after all SELECTs are done to avoid auto-flush surprises.
        final var dive = diveRepository.findByIdAndUser_Id(diveId, userId).orElseThrow();

        // Build the complete desired tag set independently of the entity's collection.
        final var manualDefIds = manualTagDefs.stream()
                .map(TagDefinitionEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        // A manually-added tag clears any prior dismissal even if it was in dismissedAutoTagIds.
        final var effectiveDismissed = new java.util.HashSet<>(dismissedAutoTagIds);
        effectiveDismissed.removeAll(manualDefIds);

        final var newTags = new java.util.ArrayList<DiveTagEntity>();
        // Manual tags (dismissed=false, clears any prior dismissal for the same tag).
        manualTagDefs.stream()
                .map(def -> new DiveTagEntity(dive, def, true, false))
                .forEach(newTags::add);
        // Auto-detected tags: dismissed ones are kept as invisible rows so they won't
        // be re-added automatically; active ones are added normally.
        autoDetectDefs.stream()
                .filter(def -> dive.matchesAutoDetect(def.getAutoDetectRule()))
                .filter(def -> !manualDefIds.contains(def.getId()))
                .map(def -> new DiveTagEntity(dive, def, false, effectiveDismissed.contains(def.getId())))
                .forEach(newTags::add);

        // Replace all existing rows atomically: delete then insert via repository,
        // completely bypassing the entity's managed tags collection.
        diveTagRepository.deleteAllByDiveId(diveId);
        diveTagRepository.flush();
        diveTagRepository.saveAll(newTags);

        return toRecord(diveRepository.findByIdAndUser_Id(diveId, userId).orElseThrow());
    }

    private void recomputeAutoTags(final DiveEntity dive, final long userId) {
        final var autoDetectDefs = tagDataService.findAutoDetectEntitiesForUser(userId);
        dive.recomputeAutoTags(autoDetectDefs);
    }

    @Transactional
    public Dive updateDiveSetPreviewImage(
            @NotNull @Valid final Dive dive, final String previewImage) {
        final var existingDive = diveRepository.findById(dive.id()).orElseThrow();
        existingDive.setPreviewImage(previewImage);
        return toRecord(diveRepository.save(existingDive));
    }

    @Transactional
    public SimplifiedDive addProfileToDiveWithDiveId(
            final User user,
            final DiveNumber diveNumber,
            final String newNotes,
            final DiveProfileUpload profile) {
        // Fetch auto-detect defs FIRST so the later SELECT cannot trigger an auto-flush
        // on a dirty entity (same insert-before-delete issue as updateDive).
        final var autoDetectDefs = tagDataService.findAutoDetectEntitiesForUser(user.id());

        final var diveEntityOpt =
                diveRepository.findByUser_IdAndNumber(user.id(), diveNumber.number());
        if (diveEntityOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not find dive by user id "
                            + user.id()
                            + " and number "
                            + diveNumber.number()
                            + ", try adding the first / base dive first.");
        }
        final var diveEntity = diveEntityOpt.get();
        final long diveId = diveEntity.getId();
        final var profileEntity = createDiveProfileEntity(profile);
        diveEntity.addProfiles(List.of(profileEntity));
        final var savedProfile = diveProfileRepository.save(profileEntity);
        if (newNotes != null && !newNotes.isBlank()) {
            diveEntity.appendNotes(diveNumber + "\n" + newNotes);
        }
        diveRepository.save(diveEntity);

        // Evict so the stale tags collection cannot be re-cascaded after the repository ops.
        entityManager.flush();
        entityManager.clear();

        final var coveredTagIds = diveTagRepository.findCoveredTagIdsByDiveId(diveId);
        final var freshDive = diveRepository.findById(diveId).orElseThrow();
        diveTagRepository.deleteActiveAutoTagsByDiveId(diveId);
        diveTagRepository.flush();
        final var newAutoTags = autoDetectDefs.stream()
                .filter(def -> freshDive.matchesAutoDetect(def.getAutoDetectRule()))
                .filter(def -> !coveredTagIds.contains(def.getId()))
                .map(def -> new DiveTagEntity(freshDive, def, false, false))
                .toList();
        diveTagRepository.saveAll(newAutoTags);

        // Clear so the final reload fetches fresh rows rather than the stale cached entity.
        entityManager.clear();
        final var savedDive = diveRepository.findById(diveId).orElseThrow();
        final var savedDiveProfile =
                savedDive.getProfiles().stream()
                        .filter(p -> p.getId() == savedProfile.getId())
                        .collect(MoreCollectors.onlyElement());
        final var historyEntity = new DiveProfileHistoryEntity(savedDiveProfile);
        diveProfileHistoryRepository.save(historyEntity);
        return toSimplifiedRecord(savedDive);
    }

    @Transactional
    public Dive addProfilesToDive(final long baseDiveId, final long toAddDiveId) {
        diveProfileRepository.setDiveWhereDiveIs(baseDiveId, toAddDiveId);
        return toRecord(diveRepository.findById(baseDiveId).orElseThrow());
    }

    @Transactional
    public void deleteDiveById(final long diveId) {
        diveRepository.deleteById(diveId);
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
    public PagedResponse<SimplifiedDive> findDivesByTag(
            final long userId, final long tagId, final DiveSort sort, final Pageable pageable) {
        final var result = diveRepository.findByUser_IdAndTagId(
                userId, tagId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByTags(
            final long userId, final List<Long> tagIds, final DiveSort sort, final Pageable pageable) {
        if (tagIds.size() == 1) {
            // Fast-path: single-tag query is simpler
            return findDivesByTag(userId, tagIds.get(0), sort, pageable);
        }
        final var result = diveRepository.findByUser_IdAndAllTagIds(
                userId, tagIds, tagIds.size(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    /**
     * Combines every filter dimension (tags, site, suit, base configuration, text query, dive-start
     * date range) with AND semantics, unlike the single-dimension {@code findDivesBy*} methods
     * above. Used by the dive-list "view dives in this time range" link from the stats timeline.
     */
    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findFiltered(
            final long userId, final DiveFilterParams filters, final DiveSort sort, final int page,
            final int pageSize) {
        final var params = new MapSqlParameterSource().addValue("userId", userId);
        final var where = new StringBuilder("d.fk_diver_id = :userId");

        if (filters.diveSiteId() != null) {
            where.append(" AND d.dive_site = :diveSiteId");
            params.addValue("diveSiteId", filters.diveSiteId());
        }
        if (filters.suitId() != null) {
            where.append(" AND dc.fk_suit_id = :suitId");
            params.addValue("suitId", filters.suitId());
        }
        if (filters.baseConfiguration() != null) {
            where.append(" AND dc.base_configuration = :baseConfiguration");
            params.addValue("baseConfiguration", filters.baseConfiguration().name());
        }
        if (filters.query() != null && !filters.query().isBlank()) {
            where.append(" AND d.dive_identifier ILIKE :query");
            params.addValue("query", "%" + filters.query().trim() + "%");
        }
        if (filters.startDate() != null) {
            where.append(" AND ds.dive_start >= :startDate");
            params.addValue("startDate", filters.startDate());
        }
        if (filters.endDate() != null) {
            where.append(" AND ds.dive_start < :endDate");
            params.addValue("endDate", filters.endDate());
        }
        if (filters.tagIds() != null && !filters.tagIds().isEmpty()) {
            where.append(
                    """
                     AND d.pk_dive_id IN (
                        SELECT dt.fk_dive_id FROM t_dive_tags dt
                        WHERE dt.fk_tag_id IN (:tagIds) AND dt.dismissed = false
                        GROUP BY dt.fk_dive_id
                        HAVING COUNT(DISTINCT dt.fk_tag_id) = :tagCount
                    )""");
            params.addValue("tagIds", filters.tagIds());
            params.addValue("tagCount", (long) filters.tagIds().size());
        }

        final var fromClause =
                """
                FROM t_dives d
                JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                LEFT JOIN t_dive_configuration dc ON dc.fk_dive_id = d.pk_dive_id
                WHERE """
                        + where;

        final var totalElements =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) " + fromClause, params, Long.class);

        params.addValue("limit", pageSize);
        params.addValue("offset", (long) page * pageSize);
        final var sortColumn = sqlSortColumn(sort.column());
        final var sortDir = sort.direction() == SortDirection.ASCENDING ? "ASC" : "DESC";
        final var ids =
                namedParameterJdbcTemplate.queryForList(
                        "SELECT d.pk_dive_id "
                                + fromClause
                                + " ORDER BY d."
                                + sortColumn
                                + " "
                                + sortDir
                                + " LIMIT :limit OFFSET :offset",
                        params,
                        Long.class);

        final var entitiesById =
                diveRepository.findAllById(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(DiveEntity::getId, e -> e));
        final var ordered =
                ids.stream()
                        .map(entitiesById::get)
                        .filter(java.util.Objects::nonNull)
                        .map(this::toSimplifiedRecord)
                        .toList();

        final var total = totalElements == null ? 0L : totalElements;
        final var totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
        return new PagedResponse<>(pageSize, totalPages, total, ordered);
    }

    private static String sqlSortColumn(final DiveSortColumn column) {
        return switch (column) {
            case ID -> "pk_dive_id";
            case NUMBER -> "dive_number";
            case CUSTOM_IDENTIFIER -> "dive_identifier";
        };
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> searchDives(
            final long userId, final String query, final Pageable pageable) {
        final var result =
                isNumeric(query)
                        ? diveRepository.searchDivesNumeric(userId, query + "%", pageable)
                        : diveRepository.searchDives(userId, query, pageable);
        return PagedResponse.of(result, this::toSimplifiedRecord);
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
        return findDiveSiteByLocationDistanceWithin(coordinate, LocationUtils.MIN_DIVE_SITE_DIST);
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
        try {
            return diveSiteRepository
                    .save(new DiveSiteEntity(name, coordinate.toPoint()))
                    .toRecord();
        } catch (final DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Dive site with name " + name + " already exists.");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasReadAccess(@NotNull final User user, final long diveId) {
        return userRepository.isReader(diveId, user.id());
    }

    @Transactional(readOnly = true)
    public PagedResponse<User> findReaders(final long diveId, final Pageable pageable) {
        return PagedResponse.of(userRepository.findReaders(diveId, pageable), UserEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public Stream<User> findReadersInternal(final long diveId) {
        return userRepository.findReaders(diveId).stream().map(UserEntity::toRecord);
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

    @Transactional(readOnly = true)
    public List<Group> getGroupReaders(final long diveId) {
        return namedParameterJdbcTemplate
                .query(
                        "SELECT fk_group_id FROM t_dive_privileges_groups WHERE fk_dive_id = :diveId",
                        new MapSqlParameterSource().addValue("diveId", diveId),
                        (r, _) -> r.getLong(1))
                .stream()
                .map(groupRepository::findById)
                .flatMap(Optional::stream)
                .map(GroupEntity::toRecord)
                .toList();
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
    public List<DiveSiteWithDives<DiveSite>> findDiveSitesByUser(
            final long userId, final boolean onlyOwn) {
        return findDiveSiteEntitiesByUser(userId, onlyOwn).stream()
                .map(d -> new DiveSiteWithDives<>(d.site().toRecord(), d.diveInfo()))
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

    private List<DiveSiteWithDives<DiveSiteEntity>> findDiveSiteEntitiesByUser(
            final long userId, final boolean onlyOwn) {
        final var result =
                onlyOwn
                        ? diveSiteRepository.findSitesByDiveWithUserId(userId)
                        : diveSiteRepository.findSitesByDiveWithReaderUserId(userId);
        return result.stream()
                .map(
                        row -> {
                            if (row.length < 4) {
                                throw new IllegalArgumentException(
                                        "Could not read dives and dive sites");
                            }
                            final var site = (DiveSiteEntity) row[0];
                            final var diveIds = getLongListFromSqlObject(row[1]);
                            final var diveNumbers = getLongListFromSqlObject(row[2]);
                            final var diveIdentifiers = getStringListFromSqlObject(row[3]);
                            return DiveSiteWithDives.of(
                                    site, diveIds, diveNumbers, diveIdentifiers);
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
                case final Integer[] intArr -> Arrays.stream(intArr).map(Long::valueOf).toList();
                case final int[] intArr -> Arrays.stream(intArr).mapToObj(Long::valueOf).toList();
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

    @SuppressWarnings("unchecked")
    private List<String> getStringListFromSqlObject(final Object o) {
        if (o == null) {
            return Collections.emptyList();
        }
        try {
            return switch (o) {
                case final Array sqlArray -> {
                    try {
                        yield Arrays.stream((String[]) sqlArray.getArray()).toList();
                    } catch (final SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
                case final String[] stringArr -> Arrays.asList(stringArr);
                case final List<?> list -> (List<String>) list;
                case final Collection<?> collection ->
                        collection.stream().map(Object::toString).toList();
                default -> {
                    logger.warn(
                            "Unrecognized SQL object (tried to convert to List<String>): class {} with value {}",
                            o.getClass(),
                            o);
                    yield Collections.emptyList();
                }
            };
        } catch (final ClassCastException e) {
            logger.warn("Could not convert {} to List<String>", o.getClass(), e);
            return Collections.emptyList();
        }
    }

    @Transactional
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

    @Transactional
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

    private Sort toReaderSort(final DiveSort sort) {
        final var s = Sort.by("dive." + sort.column().jpaName());
        return switch (sort.direction()) {
            case ASCENDING -> s.ascending();
            case DESCENDING -> s.descending();
        };
    }

    private Sort toSort(final DiveSort sort) {
        final var s = Sort.by(sort.column().jpaName());
        return switch (sort.direction()) {
            case ASCENDING -> s.ascending();
            case DESCENDING -> s.descending();
        };
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByGroup(
            final long groupId,
            final int page,
            final int simplifiedDivePageSize,
            final DiveSort sort) {
        return PagedResponse.of(
                diveRepository.findByGroupPrivilege(
                        groupId, PageRequest.of(page, simplifiedDivePageSize, toSort(sort))),
                this::toSimplifiedRecord);
    }

    @Transactional
    public Dive alignProfilesManual(
            final Set<Long> profileIds, final long diveId, final Instant alignToManual) {
        final var dive = findDiveEntityById(diveId);
        final var profiles =
                dive.getProfiles().stream()
                        .filter(profile -> profileIds.contains(profile.getId()))
                        .toList();
        if (profiles.size() != profileIds.size()) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Expected to find {0} profiles in dive {1} matching the ids {2}, but found {3} only ({4}).",
                            profileIds.size(),
                            diveId,
                            profileIds,
                            profiles.size(),
                            profiles.stream().map(DiveProfileEntity::getId).toList()));
        }
        profiles.forEach(p -> p.alignProfileManual(alignToManual));
        return toRecord(diveRepository.save(dive));
    }

    @Transactional
    public Dive resetAlignedProfiles(final long diveId, final Set<Long> profileIds) {
        final var dive = findDiveEntityById(diveId);
        final var profiles =
                dive.getProfiles().stream()
                        .filter(profile -> profileIds.contains(profile.getId()))
                        .toList();
        profiles.forEach(DiveProfileEntity::resetAlignProfileManual);
        return toRecord(diveRepository.save(dive));
    }

    @Transactional
    public void computeMissingDiveSummaries() {
        final var max = 500;
        final var count =
                diveRepository
                        .saveAll(
                                diveRepository
                                        .findByNoSummary(Pageable.ofSize(max))
                                        .map(DiveEntity::updateDiveSummary))
                        .size();
        logger.info("Computed Summaries for {} dives (limited at {})", count, max);
    }

    @Transactional
    public Suit saveSuit(final long userId, final Suit suitWithoutId) {
        return suitRepository
                .save(new SuitEntity(userRepository.findById(userId).orElseThrow(), suitWithoutId))
                .toRecord();
    }

    @Transactional(readOnly = true)
    public Optional<Suit> findSuitById(final long userId, final long id) {
        return suitRepository.findByIdAndUser_Id(id, userId).map(SuitEntity::toRecord);
    }

    @Transactional
    public Suit updateSuitById(final long userId, final long id, final @Valid Suit suit) {
        final var existing =
                suitRepository
                        .findByIdAndUser_Id(id, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find Suit by id " + id));
        existing.setType(suit.type());
        existing.setThicknessMM(suit.thickness());
        existing.setAdditionalNotes(suit.notes());
        return suitRepository.save(existing).toRecord();
    }

    @Transactional(readOnly = true)
    public PagedResponse<Suit> findSuitsByUserId(
            final long id, final int page, final int pageSize) {
        return PagedResponse.of(
                suitRepository.findByUser_Id(
                        id,
                        PageRequest.of(
                                page,
                                pageSize,
                                Sort.by(new Sort.Order(Sort.Direction.DESC, "id")))),
                SuitEntity::toRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DiveComputerManufacturer> findDiveComputerManufacturers(
            final Pageable pageable) {
        return PagedResponse.of(
                diveComputerManufacturerRepository.findAll(pageable),
                DiveComputerManufacturerEntity::toRecord);
    }

    @Transactional
    public DiveComputer updateDiveComputer(
            final User user, final long computerId, final @NotBlank String customIdentifier) {
        final var computer =
                diveComputerRepository
                        .findById(computerId)
                        .orElseThrow(() -> ForbiddenException.forDiveComputer(user, computerId));
        computer.setIdentifier(customIdentifier);
        return diveComputerRepository.save(computer).toRecord();
    }

    @Transactional
    public int deleteUnusedDiveComputers(final User user) {
        return diveComputerRepository.deleteAllByUser_IdAndProfilesIsEmpty(user.id());
    }

    public boolean hasWriteAccess(final @NotNull User user, final Set<Long> diveIds) {
        return diveRepository.countByIdInAndUser_Id(diveIds, user.id()) == diveIds.size();
    }

    @Transactional
    public void setBaseConfiguration(final BaseConfiguration newValue, final Set<Long> idsList) {
        diveRepository.updateBaseConfiguration(newValue, idsList);
    }

    @Transactional
    public void setSuitById(final long userId, final long newSuitId, final HashSet<Long> ids) {
        final var suit = suitRepository.findByIdAndUser_Id(newSuitId, userId);
        if (suit.isEmpty()) {
            throw new NoSuchElementException("Could not find suit by id " + newSuitId);
        }
        diveRepository.setSuit(suit.get(), ids);
    }

    @Transactional
    public void setWeight(final double newValue, final List<Long> diveIds) {
        diveRepository.setWeight(newValue, diveIds);
    }

    @Transactional(readOnly = true)
    public List<String> findBuddyNameSuggestions(final long userId, final String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return diveBuddyNameRepository.findDistinctBuddyNamesByUser(userId, query.trim());
    }

    @Transactional(readOnly = true)
    public List<String> findAllBuddyNames(final long userId) {
        return diveBuddyNameRepository.findDistinctBuddyNamesByUser(userId, "");
    }

    /**
     * Renames a named (free-text, non-linked-account) dive buddy across every dive the user
     * owns. If a dive already has a buddy entry with the new name, the old-name row on that
     * dive is dropped instead of renamed, to avoid duplicate buddy names on the same dive.
     *
     * @return the number of dives whose buddy list was affected
     */
    @Transactional
    public int renameBuddyName(final long userId, final String oldName, final String newName) {
        final var trimmedOld = oldName.trim();
        final var trimmedNew = newName.trim();
        if (trimmedOld.equals(trimmedNew)) {
            return 0;
        }
        final var oldMatches = diveBuddyNameRepository.findAllByDive_User_IdAndName(userId, trimmedOld);
        if (oldMatches.isEmpty()) {
            return 0;
        }
        final var diveIdsWithNewName =
                diveBuddyNameRepository.findAllByDive_User_IdAndName(userId, trimmedNew).stream()
                        .map(b -> b.getDive().getId())
                        .collect(Collectors.toSet());

        final var toDelete =
                oldMatches.stream().filter(b -> diveIdsWithNewName.contains(b.getDive().getId())).toList();
        final var toRename =
                oldMatches.stream()
                        .filter(b -> !diveIdsWithNewName.contains(b.getDive().getId()))
                        .toList();

        if (!toDelete.isEmpty()) {
            diveBuddyNameRepository.deleteAll(toDelete);
        }
        toRename.forEach(b -> b.setName(trimmedNew));
        if (!toRename.isEmpty()) {
            diveBuddyNameRepository.saveAll(toRename);
        }
        return oldMatches.size();
    }
}
