package ch.sthomas.stddivelogger.data.service;

import static org.apache.commons.lang3.StringUtils.isNumeric;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.NamedBuddyInput;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.*;
import ch.sthomas.stddivelogger.model.dive.profile.ReimportSimilarityCheck;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.stats.BuddyRoleBreakdown;
import ch.sthomas.stddivelogger.model.dive.stats.BuddyRoleCount;
import ch.sthomas.stddivelogger.model.dive.stats.BuddyRoleStats;
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

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.hibernate.query.SortDirection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private final CcrUnitRepository ccrUnitRepository;
    private final TagDataService tagDataService;
    private final DiveTagRepository diveTagRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final AnalyticsDataService analyticsDataService;
    private final DiveBuddyRepository diveBuddyRepository;
    private final DiveSiteLinkRepository diveSiteLinkRepository;
    private final DiveBuddyDefaultRoleRepository diveBuddyDefaultRoleRepository;

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
            final CcrUnitRepository ccrUnitRepository,
            final TagDataService tagDataService,
            final DiveTagRepository diveTagRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final AnalyticsDataService analyticsDataService,
            final DiveBuddyRepository diveBuddyRepository,
            final DiveSiteLinkRepository diveSiteLinkRepository,
            final DiveBuddyDefaultRoleRepository diveBuddyDefaultRoleRepository) {
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
        this.ccrUnitRepository = ccrUnitRepository;
        this.tagDataService = tagDataService;
        this.diveTagRepository = diveTagRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.analyticsDataService = analyticsDataService;
        this.diveBuddyRepository = diveBuddyRepository;
        this.diveSiteLinkRepository = diveSiteLinkRepository;
        this.diveBuddyDefaultRoleRepository = diveBuddyDefaultRoleRepository;
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
                        user.id(),
                        Objects.requireNonNull(suit.id(), "Cannot query dives by an unsaved suit"),
                        PageRequest.of(page, pageSize, toSort(diveSort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByUserAndCcrUnit(
            final User user,
            final CcrUnit ccrUnit,
            final DiveSort diveSort,
            final int page,
            final int pageSize) {
        final var result =
                diveRepository.findByUser_IdAndConfiguration_CcrUnit_Id(
                        user.id(),
                        Objects.requireNonNull(
                                ccrUnit.id(), "Cannot query dives by an unsaved CCR unit"),
                        PageRequest.of(page, pageSize, toSort(diveSort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public List<Long> findDiveIdsByUserAndCcrUnit(final long userId, final long ccrUnitId) {
        return diveRepository
                .findAllByUser_IdAndConfiguration_CcrUnit_Id(userId, ccrUnitId)
                .stream()
                .map(DiveEntity::getId)
                .toList();
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

    /**
     * Shared by every profile-mutating method below (reimport/delete/trim) - each needs to locate
     * one specific profile on an already-loaded dive and fail the same way if it's not actually
     * there.
     */
    private DiveProfileEntity findProfileOnDive(final DiveEntity dive, final long profileId) {
        return dive.getProfiles().stream()
                .filter(p -> p.getId() == profileId)
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Profile "
                                                + profileId
                                                + " not found on dive "
                                                + dive.getId()));
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
                        resolveCcrUnitForConfiguration(userEntity, configuration.ccrUnit()),
                        resolveCcrUnitForConfiguration(
                                userEntity, configuration.secondaryCcrUnit()),
                        configuration,
                        userEntity,
                        diveSite,
                        profileEntities,
                        namedBuddies,
                        this::toEntity);
        applyDefaultBuddyRoles(user.id(), entity.getNamedBuddies());
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
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return suitRepository.save(new SuitEntity(user, suit));
        } catch (final DataIntegrityViolationException e) {
            // Lost a race with a concurrent request for the same suit - the DB's unique
            // constraint (see V0_3_5__suit_ccr_unit_unique_constraints.sql) rejected our insert
            // rather than silently creating a second, indistinguishable row. Use the winner's row.
            return suitRepository
                    .findByUser_IdAndTypeAndThicknessMMAndAdditionalNotes(
                            user.getId(), suit.type(), suit.thickness(), suit.notes())
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public CcrUnitEntity findOrCreateCcrUnit(final UserEntity user, final CcrUnit ccrUnit) {
        if (ccrUnit.id() != null) {
            return ccrUnitRepository
                    .findByIdAndUser_Id(ccrUnit.id(), user.getId())
                    .orElseThrow(
                            () ->
                                    new NoSuchElementException(
                                            "Could not find CCR unit by id " + ccrUnit.id()));
        }
        final var existing =
                ccrUnitRepository.findByUser_IdAndNameAndAdditionalNotes(
                        user.getId(), ccrUnit.name(), ccrUnit.notes());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return ccrUnitRepository.save(new CcrUnitEntity(user, ccrUnit));
        } catch (final DataIntegrityViolationException e) {
            // Same race as findOrCreateSuit above.
            return ccrUnitRepository
                    .findByUser_IdAndNameAndAdditionalNotes(
                            user.getId(), ccrUnit.name(), ccrUnit.notes())
                    .orElseThrow(() -> e);
        }
    }

    /**
     * A CCR unit is independent of the diver's own {@code BaseConfiguration} - any dive may
     * reference 0, 1, or 2 units (see {@link DiveConfiguration#ccrUnit}/{@link
     * DiveConfiguration#secondaryCcrUnit}) regardless of how the diver's own cylinders are rigged.
     */
    @Nullable
    private CcrUnitEntity resolveCcrUnitForConfiguration(
            final UserEntity user, @Nullable final CcrUnit ccrUnit) {
        return ccrUnit == null ? null : findOrCreateCcrUnit(user, ccrUnit);
    }

    /**
     * Same best-guess as {@code DiveService#inferConfigurationFromComputer} - auto-fills the CCR
     * unit from whichever computer recorded this profile - but applied when a companion profile is
     * attached to an *already existing* dive, which previously skipped this entirely (only a
     * brand-new dive via {@code saveDive} got it). Never touches the dive's own {@code
     * BaseConfiguration}, which is independent of which CCR unit is used. A no-op whenever there's
     * nothing to infer from: no configuration on the dive yet (shouldn't happen - every dive gets
     * one at creation), an explicit primary CCR unit already set (never overridden), the computer
     * isn't linked to a unit, or that unit belongs to a different user.
     */
    // Package-private rather than private so it can be exercised directly by a focused unit test
    // instead of needing a full addProfileToDiveWithDiveId() integration fixture just to reach it
    // - same rationale as DiveService#inferConfigurationFromComputer.
    void inferConfigurationFromComputerIfMissing(
            final User user,
            final @Nullable DiveConfigurationEntity configuration,
            final DiveProfileUpload profile) {
        if (configuration == null || configuration.toRecord().ccrUnit() != null) {
            return;
        }
        final var linkedCcrUnit =
                diveComputerRepository
                        .findById(profile.diveComputerId())
                        .map(DiveComputerEntity::getCcrUnit)
                        .orElse(null);
        if (linkedCcrUnit == null || linkedCcrUnit.getUser().getId() != user.id()) {
            return;
        }
        final var current = configuration.toRecord();
        final var inferred =
                new DiveConfiguration(
                        current.suit(),
                        current.base(),
                        current.weight(),
                        current.weightFeeling(),
                        current.cylinders(),
                        linkedCcrUnit.toRecord(),
                        current.secondaryCcrUnit(),
                        current.adHocSuitType());
        configuration.update(
                configuration.getSuitEntity(),
                linkedCcrUnit,
                configuration.getSecondaryCcrUnitEntity(),
                inferred,
                this::toEntity);
        logger.info(
                "Inferred CCR unit {} for user {} from dive computer {} while attaching a"
                        + " companion profile",
                linkedCcrUnit.getId(),
                user.id(),
                profile.diveComputerId());
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
                                        m,
                                        Optional.ofNullable(m.gas())
                                                .map(this::toEntity)
                                                .orElse(null)))
                .toList();
    }

    /**
     * Replaces only the raw measurement data (and start/end) of an existing profile, leaving the
     * parent dive's other properties (suit, gas consumption, weight, visibility, notes, tags,
     * buddies, ...) untouched. Intended for re-parsing a dive's original source with a newer/richer
     * importer (e.g. a format that now carries TTS that didn't used to be captured), not for
     * attaching a different computer's own recording of the same dive - that's "merge profiles"
     * ({@code addProfileToDiveWithDiveId}). Rejects (via {@link ReimportSimilarityCheck}) anything
     * that doesn't look like the same dive, so the two features can't be confused for each other.
     */
    @Transactional
    public Dive reimportProfileMeasurements(
            final long diveId,
            final long profileId,
            final List<DiveMeasurement> newMeasurements,
            final Instant newStart,
            final Instant newEnd) {
        final var dive = findDiveEntityById(diveId);
        final var profile = findProfileOnDive(dive, profileId);

        final var mismatch =
                ReimportSimilarityCheck.checkSameDive(
                        profile.getStart(),
                        profile.getEnd(),
                        profile.toMeasurementRecords(),
                        newStart,
                        newEnd,
                        newMeasurements);
        if (mismatch.isPresent()) {
            throw new IllegalArgumentException(
                    "The uploaded file doesn't look like the same dive as the profile you're "
                            + "replacing ("
                            + mismatch.get()
                            + "). If this is meant to be a different dive computer's own "
                            + "recording of the same dive, use \"merge profiles\" instead of "
                            + "reimport.");
        }

        // Looked up directly via the repository rather than profile.diveProfileHistory - that
        // mapped-by association is never explicitly back-filled on the in-memory entity right
        // after DiveProfileHistoryEntity is first inserted (see createDive() above), so trusting
        // it here could see a stale null within the same persistence context/transaction.
        final var history = diveProfileHistoryRepository.findById(profileId).orElseThrow();

        // A prior manual time-alignment (see alignProfileManual) is preserved by applying the same
        // offset to the freshly re-parsed data, rather than silently reverting to the file's own
        // raw (unaligned) timestamp - reimport should only ever change what the parser produced.
        final var manualOffset = Duration.between(history.getOriginalStart(), profile.getStart());
        final var alignedStart = newStart.plus(manualOffset);
        final var alignedEnd = newEnd.plus(manualOffset);
        final var alignedMeasurements =
                manualOffset.isZero()
                        ? newMeasurements
                        : shiftMeasurementTimes(newMeasurements, manualOffset);

        // Replace all existing rows atomically: delete then insert via repository,
        // completely bypassing the entity's managed measurements collection (no orphanRemoval).
        diveMeasurementRepository.deleteAllByProfile_Id(profileId);
        diveMeasurementRepository.flush();
        profile.replaceMeasurements(
                toMeasurementEntities(alignedMeasurements), alignedStart, alignedEnd);
        // The manual-alignment reset target re-baselines to the newly reimported raw times too -
        // "reset alignment" should undo drift relative to the file just reimported, not a stale
        // pre-reimport one.
        history.updateOriginal(newStart, newEnd);
        diveProfileHistoryRepository.save(history);

        dive.updateDiveSummary();
        recomputeAutoTags(dive, dive.getUserEntity().getId());
        return toRecord(diveRepository.save(dive));
    }

    private static List<DiveMeasurement> shiftMeasurementTimes(
            final List<DiveMeasurement> measurements, final Duration offset) {
        return measurements.stream()
                .map(
                        m ->
                                new DiveMeasurement(
                                        m.time().plus(offset),
                                        m.temperature(),
                                        m.depth(),
                                        m.ndl(),
                                        m.deco(),
                                        m.gas(),
                                        m.po2(),
                                        m.rmvLiters(),
                                        m.n2(),
                                        m.o2Tox(),
                                        m.cns(),
                                        m.mode(),
                                        m.timeToSurface()))
                .toList();
    }

    public record ReimportPreviewContext(
            Instant profileStart,
            Instant profileEnd,
            List<DiveMeasurement> profileMeasurements,
            Dive dive) {}

    /**
     * Everything ImportService needs to run the similarity check and compute field conflicts for a
     * reimport preview, in one read.
     */
    @Transactional(readOnly = true)
    public ReimportPreviewContext getReimportPreviewContext(
            final long diveId, final long profileId) {
        final var dive = findDiveEntityById(diveId);
        final var profile = findProfileOnDive(dive, profileId);
        return new ReimportPreviewContext(
                profile.getStart(),
                profile.getEnd(),
                profile.toMeasurementRecords(),
                toRecord(dive));
    }

    /**
     * Applies a reimport's resolved notes/visibility/namedBuddies/gasConsumption (see {@code
     * ReimportFieldMerge} - each null param here means "leave exactly as-is", already decided by
     * that resolution logic before this is called). Never touches anything else - site,
     * configuration, tags, leader/team fields are deliberately out of scope for reimport.
     */
    @Transactional
    public Dive applyReimportResolution(
            final long diveId,
            final @Nullable String notes,
            final @Nullable Visibility visibility,
            final @Nullable List<String> namedBuddies,
            final @Nullable DiveGasConsumption gasConsumption) {
        final var dive = findDiveEntityById(diveId);
        dive.applyReimportResolution(notes, visibility, namedBuddies, gasConsumption);
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
                // Same real computer under a differently-spelled manufacturer name (e.g.
                // "Shearwater" vs "Shearwater Research, Inc") - reuse it rather than creating a
                // duplicate. Serial number must still match exactly; see the repository method's
                // own doc comment for what counts as a fuzzy manufacturer match.
                .or(
                        () ->
                                diveComputerRepository
                                        .findByUser_IdAndSerialNumberAndManufacturer_NameFuzzy(
                                                userId, serialNumber, manufacturer))
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
            final var suit =
                    suitRepository
                            .findByIdAndUser_Id(updateBody.suitId(), user.id())
                            .orElseThrow(() -> new NoSuchElementException("Could not find Suit"));
            final var userEntity = userRepository.findById(user.id()).orElseThrow();
            final var ccrUnit =
                    resolveCcrUnitForConfiguration(
                            userEntity, updateBody.configuration().ccrUnit());
            final var secondaryCcrUnit =
                    resolveCcrUnitForConfiguration(
                            userEntity, updateBody.configuration().secondaryCcrUnit());
            if (existingDive.getConfiguration() != null) {
                existingDive
                        .getConfiguration()
                        .update(
                                suit,
                                ccrUnit,
                                secondaryCcrUnit,
                                updateBody.configuration(),
                                this::toEntity);
            } else {
                existingDive.setConfiguration(
                        new DiveConfigurationEntity(
                                existingDive,
                                suit,
                                ccrUnit,
                                secondaryCcrUnit,
                                updateBody.configuration(),
                                this::toEntity));
            }
            logger.info("Set new configuration with suit: {}, {}", suit, suit.getType());
            // Flush the cylinders orphanRemoval mutation to completion here, before anything else
            // in this method (namedBuddies below, then the final repository.save()) adds more
            // dirty state to the same session. Leaving it pending let a later, unrelated
            // auto-flush (triggered by some other query further down the call chain) catch this
            // collection mid-flight and throw "A collection with orphan deletion was no longer
            // referenced by the owning entity instance" - reproduced by
            // DiveConfigurationUpdateIntegrationTest editing an already-persisted cylinder set.
            entityManager.flush();
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
        // Mutate conditions in-place (same @MapsId constraint as configuration). Unlike
        // visibility/gasConsumption, a null waterType/current here is a legitimate "clear it"
        // value (not "leave unchanged"), since the frontend's Water Type & Current fieldset is
        // always rendered/submitted (not gated behind an existing-conditions check like those two
        // are) - so always apply it, rather than skipping when both happen to be null, which
        // would otherwise make clearing an already-set value to "Unspecified" a no-op.
        if (existingDive.getConditions() != null) {
            existingDive.getConditions().update(updateBody.waterType(), updateBody.current());
        } else {
            existingDive.setConditions(
                    new DiveConditionsEntity(
                            existingDive, updateBody.waterType(), updateBody.current()));
        }
        validateLeaderReference(updateBody, newBuddies, existingDive);
        if (updateBody.averageDepth() != null) {
            // Must happen before update() below, which recomputes the rest of the summary and
            // would otherwise read this field before it's set.
            existingDive.setAverageDepth(updateBody.averageDepth());
        }
        // All @MapsId child entities already mutated above — pass null to avoid re-assignment.
        existingDive.update(
                updateBody.number(),
                updateBody.customIdentifier(),
                updateBody.notes(),
                diveSiteEntity,
                newBuddies,
                null, // configuration mutated in-place above
                null, // gasConsumption mutated in-place above
                null, // visibility mutated in-place above
                updateBody.leaderNamedBuddyId(),
                updateBody.leaderBuddyDiveId(),
                updateBody.leaderSelfExplicit(),
                updateBody.teamTerminology());
        return toRecord(diveRepository.save(existingDive));
    }

    /**
     * Refreshes the auto-detected tags for a dive and returns the updated dive. Manual and
     * dismissed tag rows are preserved; only the active auto-detected rows are replaced. This is
     * called by the frontend when opening the edit page so that the user always sees up-to-date
     * auto-tags before editing.
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

        final var newAutoTags =
                autoDetectDefs.stream()
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

    private @Nullable ArrayList<DiveBuddyNameEntity> getNewNamedBuddies(
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

    /**
     * A dive leader must actually be a buddy on this dive - the FK constraints alone only prove the
     * referenced row exists somewhere, not that it belongs to this dive, so a client could
     * otherwise point {@code leaderNamedBuddyId} at a name from a completely different dive.
     * Checked against {@code newBuddies} (the just-resolved incoming set) rather than the dive's
     * pre-update collection, so naming someone as leader in the same request that adds them still
     * works.
     */
    private void validateLeaderReference(
            final UpdateDiveBody updateBody,
            final @Nullable List<DiveBuddyNameEntity> newBuddies,
            final DiveEntity existingDive) {
        final var leaderNamedBuddyId = updateBody.leaderNamedBuddyId();
        if (leaderNamedBuddyId != null) {
            final var candidates = newBuddies != null ? newBuddies : existingDive.getNamedBuddies();
            final var isOwnBuddy =
                    candidates.stream().anyMatch(b -> leaderNamedBuddyId.equals(b.getId()));
            if (!isOwnBuddy) {
                throw new IllegalArgumentException(
                        "Named buddy " + leaderNamedBuddyId + " is not a buddy on this dive.");
            }
        }
        final var leaderBuddyDiveId = updateBody.leaderBuddyDiveId();
        if (leaderBuddyDiveId != null && !existingDive.hasBuddyDive(leaderBuddyDiveId)) {
            throw new IllegalArgumentException(
                    "Dive " + leaderBuddyDiveId + " is not a linked buddy dive on this dive.");
        }
    }

    private @NonNull DiveBuddyNameEntity getOldOrNewBuddy(
            final UpdateDiveBody dive,
            final Map<String, DiveBuddyNameEntity> namedBuddies,
            final DiveEntity existingDive,
            final NamedBuddyInput input) {
        final var existing =
                Optional.ofNullable(namedBuddies.get(input.name()))
                        .or(
                                () ->
                                        diveBuddyNameRepository.findByDive_IdAndName(
                                                dive.id(), input.name()));
        if (existing.isPresent()) {
            final var entity = existing.get();
            entity.setRole(input.role());
            return entity;
        }
        // A buddy genuinely new to this dive - fall back to the diver's saved default role for
        // them (see DiveBuddyDefaultRoleEntity) rather than leaving it unset, but only here: an
        // already-present buddy always takes the client's explicit input above, even if that's
        // null (a deliberate clear must never be silently re-filled from the default).
        final var role =
                input.role() != null
                        ? input.role()
                        : diveBuddyDefaultRoleRepository
                                .findByUser_IdAndBuddyName(
                                        existingDive.getUserEntity().getId(), input.name())
                                .map(DiveBuddyDefaultRoleEntity::getRole)
                                .orElse(null);
        return diveBuddyNameRepository.save(
                new DiveBuddyNameEntity(existingDive, input.name(), role));
    }

    @Transactional
    public Dive updateTags(
            final long diveId,
            final long userId,
            final List<Long> manualTagIds,
            final List<Long> dismissedAutoTagIds) {
        // Resolve all tag definitions before touching the dive entity, so that no
        // Hibernate auto-flush can occur while the tag collection is in a transient state.
        final var manualTagDefs =
                tagDataService.findEntitiesByIdsVisibleToUser(manualTagIds, userId);
        final var autoDetectDefs = tagDataService.findAutoDetectEntitiesForUser(userId);

        // Load the dive after all SELECTs are done to avoid auto-flush surprises.
        final var dive = diveRepository.findByIdAndUser_Id(diveId, userId).orElseThrow();

        // Build the complete desired tag set independently of the entity's collection.
        final var manualDefIds =
                manualTagDefs.stream()
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
                .map(
                        def ->
                                new DiveTagEntity(
                                        dive, def, false, effectiveDismissed.contains(def.getId())))
                .forEach(newTags::add);

        // Replace all existing rows atomically: delete then insert via repository,
        // completely bypassing the entity's managed tags collection.
        diveTagRepository.deleteAllByDiveId(diveId);
        diveTagRepository.flush();
        diveTagRepository.saveAll(newTags);

        // Clear the 1st-level cache so the reload below fetches fresh rows from DB rather than
        // returning the same still-managed `dive` instance from above - which still holds
        // whatever tags collection it had before deleteAllByDiveId/saveAll ran, since those went
        // straight through the repository and never touched `dive`'s own in-memory collection.
        // Without this, the response returned to the caller shows the pre-update tags even though
        // the DB itself was updated correctly (see refreshAutoTags() just above, which already
        // does this for the identical reason).
        entityManager.clear();
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
            final @Nullable String newNotes,
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
        inferConfigurationFromComputerIfMissing(user, diveEntity.getConfiguration(), profile);
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
        final var newAutoTags =
                autoDetectDefs.stream()
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
        // A newly-added profile can change segment/rate/PO2-FO2 analytics for the whole dive (a
        // bailout computer's mode data now affects every other profile's calculated values, for
        // instance) - without this, the dive keeps whatever analytics it had before this profile
        // existed until something else happens to invalidate it.
        analyticsDataService.invalidateAnalyticsForDive(diveId);
        return toSimplifiedRecord(savedDive);
    }

    @Transactional
    public Dive addProfilesToDive(final long baseDiveId, final long toAddDiveId) {
        diveProfileRepository.setDiveWhereDiveIs(baseDiveId, toAddDiveId);
        analyticsDataService.invalidateAnalyticsForDive(baseDiveId);
        return toRecord(diveRepository.findById(baseDiveId).orElseThrow());
    }

    /**
     * Removes a single profile (and its measurements, segments, history) from a dive without
     * touching the rest of the dive - the recovery path for a profile attached to the wrong dive by
     * mistake (e.g. via import) rather than a genuine duplicate-computer merge.
     */
    @Transactional
    public Dive deleteProfile(final long diveId, final long profileId) {
        final var dive = findDiveEntityById(diveId);
        if (dive.getProfiles().size() <= 1) {
            throw new IllegalArgumentException(
                    "Cannot delete the only profile on a dive - delete the whole dive instead.");
        }
        final var profile = findProfileOnDive(dive, profileId);
        // The dive entity is still managed and still references this profile in its own
        // in-memory collection - without removing it there too, cascade=ALL on that mapping
        // re-asserts the association on flush (no-op delete) since nothing told Hibernate the
        // parent's view of its children changed, only that this one row should be removed.
        dive.getProfiles().remove(profile);
        diveProfileRepository.delete(profile);
        entityManager.flush();
        entityManager.clear();
        analyticsDataService.invalidateAnalyticsForDive(diveId);
        return toRecord(findDiveEntityById(diveId));
    }

    /**
     * Permanently deletes every measurement of a profile falling outside {@code [trimStart,
     * trimEnd]} - e.g. the trailing few minutes at 0.3-0.6m a Divesoft Liberty logs while waiting
     * to have its dive ended manually on the computer. Either bound may be omitted to only trim the
     * other end. Leaves at least 2 measurements; refuses a range that would leave fewer.
     */
    @Transactional
    public Dive trimProfile(
            final long diveId,
            final long profileId,
            final @Nullable Instant trimStart,
            final @Nullable Instant trimEnd) {
        final var dive = findDiveEntityById(diveId);
        findProfileOnDive(dive, profileId);

        final var measurements =
                diveMeasurementRepository.findAllByProfile_IdOrderByTimeAsc(profileId);
        if (measurements.isEmpty()) {
            throw new IllegalStateException(
                    "Profile " + profileId + " has no measurements to trim.");
        }
        final var effectiveStart =
                trimStart != null ? trimStart : measurements.getFirst().getTime().toInstant();
        final var effectiveEnd =
                trimEnd != null ? trimEnd : measurements.getLast().getTime().toInstant();
        if (!effectiveStart.isBefore(effectiveEnd)) {
            throw new IllegalArgumentException("Trim start must be before trim end.");
        }

        final var survivors = new ArrayList<DiveMeasurementEntity>();
        final var toDelete = new ArrayList<DiveMeasurementEntity>();
        for (final var m : measurements) {
            final var t = m.getTime().toInstant();
            (t.isBefore(effectiveStart) || t.isAfter(effectiveEnd) ? toDelete : survivors).add(m);
        }
        if (survivors.size() < 2) {
            throw new IllegalArgumentException(
                    "Trimming this range would leave fewer than 2 measurements on the profile.");
        }
        if (toDelete.isEmpty()) {
            // The requested range already covers every measurement - nothing to actually trim.
            return toRecord(dive);
        }

        final var newStart = survivors.getFirst().getTime().toInstant();
        final var newEnd = survivors.getLast().getTime().toInstant();

        diveMeasurementRepository.deleteAll(toDelete);
        diveMeasurementRepository.flush();

        // dive's own in-memory profile/measurements collections are stale now (deleted straight
        // through the repository, bypassing them) - clear and reload fresh rather than risk
        // re-persisting or returning the deleted rows, same as deleteProfile() above.
        entityManager.clear();
        final var freshDive = findDiveEntityById(diveId);
        final var freshProfile = findProfileOnDive(freshDive, profileId);
        // Only the two bounds need updating - the surviving measurement rows themselves are
        // already correct and untouched in the DB, so there's nothing to reassign on the entity.
        freshProfile.updateBounds(newStart, newEnd);
        freshDive.updateDiveSummary();
        analyticsDataService.invalidateAnalyticsForDive(diveId);

        return toRecord(diveRepository.save(freshDive));
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
        final var result =
                diveRepository.findByUser_IdAndTagId(
                        userId,
                        tagId,
                        PageRequest.of(
                                pageable.getPageNumber(), pageable.getPageSize(), toSort(sort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findDivesByTags(
            final long userId,
            final List<Long> tagIds,
            final DiveSort sort,
            final Pageable pageable) {
        if (tagIds.size() == 1) {
            // Fast-path: single-tag query is simpler
            return findDivesByTag(userId, tagIds.get(0), sort, pageable);
        }
        final var result =
                diveRepository.findByUser_IdAndAllTagIds(
                        userId,
                        tagIds,
                        tagIds.size(),
                        PageRequest.of(
                                pageable.getPageNumber(), pageable.getPageSize(), toSort(sort)));
        return PagedResponse.of(result, this::toSimplifiedRecord);
    }

    /**
     * Combines every filter dimension (tags, site, suit, base configuration, text query, dive-start
     * date range) with AND semantics, unlike the single-dimension {@code findDivesBy*} methods
     * above. Used by the dive-list "view dives in this time range" link from the stats timeline.
     */
    @Transactional(readOnly = true)
    public PagedResponse<SimplifiedDive> findFiltered(
            final long userId,
            final DiveFilterParams filters,
            final DiveSort sort,
            final int page,
            final int pageSize) {
        // OFFSET is computed by hand below (native SQL, not a JPA Pageable) so it doesn't get the
        // usual PageRequest.of validation for free — trigger it explicitly for the same clean
        // IllegalArgumentException every other paginated method here already gives on bad input,
        // instead of a raw "OFFSET must not be negative" PSQLException surfacing as a 500.
        PageRequest.of(page, pageSize);
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
        if (filters.ccrUnitId() != null) {
            where.append(" AND dc.fk_ccr_unit_id = :ccrUnitId");
            params.addValue("ccrUnitId", filters.ccrUnitId());
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
            params.addValue("startDate", java.sql.Timestamp.from(filters.startDate()));
        }
        if (filters.endDate() != null) {
            where.append(" AND ds.dive_start < :endDate");
            params.addValue("endDate", java.sql.Timestamp.from(filters.endDate()));
        }
        if (filters.minNumber() != null) {
            where.append(" AND d.dive_number >= :minNumber");
            params.addValue("minNumber", filters.minNumber());
        }
        if (filters.maxNumber() != null) {
            where.append(" AND d.dive_number <= :maxNumber");
            params.addValue("maxNumber", filters.maxNumber());
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
        if (filters.startTime() != null && filters.endTime() != null) {
            // Time-of-day overlap (e.g. "morning dives"), compared against the dive's own
            // start/end wall-clock time rather than requiring it to be fully contained in the
            // range. The dive's own window is assumed not to cross midnight (true in practice);
            // the filter range itself may, e.g. a "night" preset of 22:00-06:00.
            if (!filters.startTime().isAfter(filters.endTime())) {
                where.append(
                        " AND ds.dive_start::time < :endTime AND ds.dive_end::time > :startTime");
            } else {
                where.append(
                        " AND (ds.dive_end::time > :startTime OR ds.dive_start::time < :endTime)");
            }
            params.addValue("startTime", java.sql.Time.valueOf(filters.startTime()));
            params.addValue("endTime", java.sql.Time.valueOf(filters.endTime()));
        }

        final var fromClause =
                """
                FROM t_dives d
                JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                LEFT JOIN t_dive_configuration dc ON dc.fk_dive_id = d.pk_dive_id
                WHERE
                """
                        + where;

        final var totalElements =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) " + fromClause, params, Long.class);

        params.addValue("limit", pageSize);
        params.addValue("offset", (long) page * pageSize);
        final var sortColumn = sqlSortColumn(sort.column());
        final var sortDir = sort.direction() == SortDirection.ASCENDING ? "ASC" : "DESC";
        // pk_dive_id is a primary key column, so it is never actually null - requireNonNull just
        // narrows the type back from JdbcTemplate's unannotated List<@Nullable Long>.
        final var ids =
                namedParameterJdbcTemplate
                        .queryForList(
                                "SELECT d.pk_dive_id "
                                        + fromClause
                                        + " ORDER BY "
                                        + sortColumn
                                        + " "
                                        + sortDir
                                        + " LIMIT :limit OFFSET :offset",
                                params,
                                Long.class)
                        .stream()
                        .map(java.util.Objects::requireNonNull)
                        .toList();

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

    // Alias-qualified (not bare column names) since findFiltered's query joins both d (t_dives)
    // and ds (t_dive_summary) - DATE's column lives on the latter, everything else on the former.
    private static String sqlSortColumn(final DiveSortColumn column) {
        return switch (column) {
            case ID -> "d.pk_dive_id";
            case NUMBER -> "d.dive_number";
            case CUSTOM_IDENTIFIER -> "d.dive_identifier";
            case DATE -> "ds.dive_start";
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
    public Optional<DiveSite> findDiveSiteByIdWithLinks(final long id, final long userId) {
        return diveSiteRepository
                .findById(id)
                .map(e -> e.toRecordWithLinks(hasLoggedDiveAtSite(userId, id)));
    }

    @Transactional(readOnly = true)
    public boolean hasLoggedDiveAtSite(final long userId, final long siteId) {
        return diveRepository.existsByUser_IdAndDiveSite_Id(userId, siteId);
    }

    @Transactional(readOnly = true)
    public Optional<TeamTerminology> findMostRecentTeamTerminology(final long userId) {
        return diveRepository
                .findFirstByUser_IdAndTeamTerminologyIsNotNullOrderByIdDesc(userId)
                .map(DiveEntity::getTeamTerminology);
    }

    /**
     * Every dive of this user's that still has at least one backfill checklist item unfilled,
     * ordered so the most incomplete/oldest dives come first - lets the frontend present a single
     * "next dive to fill in" as well as a full browsable queue without a second query. Unpaginated
     * like {@link #findByUser_Id} above (needs every dive up front to sort/filter over), but cheap
     * per-dive since {@link DiveEntity#toBackfillStatus} deliberately skips the heavier
     * cylinder-consumption/buddy-link/tag computation {@link DiveEntity#toRecord} does.
     */
    // Active dives first (fully-dismissed sink to the bottom), then most outstanding gaps first,
    // then oldest dive first - "so nothing slips through" per the user's own framing - then id for
    // a stable order.
    private static final Comparator<DiveBackfillStatus> BACKFILL_QUEUE_ORDER =
            Comparator.comparing(DiveBackfillStatus::fullyDismissed)
                    .thenComparing(
                            Comparator.comparingInt(DiveBackfillStatus::outstandingCount)
                                    .reversed())
                    .thenComparing(
                            DiveBackfillStatus::diveStart,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(DiveBackfillStatus::diveId);

    @Transactional(readOnly = true)
    public List<DiveBackfillStatus> getBackfillQueue(final long userId) {
        return diveRepository.findByUser_Id(userId).stream()
                .map(DiveEntity::toBackfillStatus)
                .filter(status -> status.missingCount() > 0)
                .sorted(BACKFILL_QUEUE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public DiveBackfillStatus getBackfillStatus(final long diveId) {
        return diveRepository.findById(diveId).orElseThrow().toBackfillStatus();
    }

    /** Dismiss ({@code true}) or restore one backfill reason for one dive. */
    @Transactional
    public DiveBackfillStatus setBackfillFieldDismissed(
            final long diveId, final DiveBackfillField reason, final boolean dismissed) {
        final var dive = diveRepository.findById(diveId).orElseThrow();
        if (dismissed) {
            dive.dismissBackfillField(reason);
        } else {
            dive.restoreBackfillField(reason);
        }
        diveRepository.save(dive);
        entityManager.flush();
        return dive.toBackfillStatus();
    }

    /**
     * Dismiss every currently-missing reason for one dive ({@code true}) - a snapshot, so a reason
     * that becomes missing later still surfaces - or clear all of this dive's dismissals.
     */
    @Transactional
    public DiveBackfillStatus setDiveBackfillDismissed(final long diveId, final boolean dismissed) {
        final var dive = diveRepository.findById(diveId).orElseThrow();
        if (dismissed) {
            dive.toBackfillStatus().missingFields().forEach(dive::dismissBackfillField);
        } else {
            EnumSet.allOf(DiveBackfillField.class).forEach(dive::restoreBackfillField);
        }
        diveRepository.save(dive);
        entityManager.flush();
        return dive.toBackfillStatus();
    }

    /** Dismiss every outstanding reason on every currently-queued dive. */
    @Transactional
    public List<DiveBackfillStatus> dismissAllBackfill(final long userId) {
        getBackfillQueue(userId).stream()
                .filter(status -> !status.fullyDismissed())
                .forEach(status -> setDiveBackfillDismissed(status.diveId(), true));
        return getBackfillQueue(userId);
    }

    /**
     * Dismiss one reason across every one of the user's dives that currently has it outstanding -
     * the batch button for when a new backfillable field ships and shouldn't flood old dives.
     */
    @Transactional
    public List<DiveBackfillStatus> dismissBackfillReasonEverywhere(
            final long userId, final DiveBackfillField reason) {
        final var dives = diveRepository.findByUser_Id(userId);
        for (final var dive : dives) {
            if (dive.toBackfillStatus().missingFields().contains(reason)) {
                dive.dismissBackfillField(reason);
            }
        }
        diveRepository.saveAll(dives);
        entityManager.flush();
        return getBackfillQueue(userId);
    }

    @Transactional
    public DiveSite updateDiveSite(
            final long siteId,
            @Nullable final String description,
            @Nullable final String countryRegion,
            @Nullable final Double maxDepth,
            @Nullable final DiveSiteType type,
            final List<DiveSiteLink> links) {
        final var entity =
                diveSiteRepository
                        .findById(siteId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive site by id " + siteId));
        entity.setDescription(description);
        entity.setCountryRegion(countryRegion);
        entity.setMaxDepth(maxDepth);
        entity.setSiteType(type);
        diveSiteRepository.save(entity);

        diveSiteLinkRepository.deleteByDiveSite_Id(siteId);
        diveSiteLinkRepository.flush();
        for (final var link : links) {
            diveSiteLinkRepository.save(new DiveSiteLinkEntity(entity, link.url(), link.label()));
        }

        // A plain field-assigned `links` (set on construction, not hydrated by Hibernate) never
        // becomes a lazy-loadable PersistentCollection - without clearing the persistence context,
        // a same-transaction re-fetch of `entity` below would return this very instance from the
        // identity map with its stale in-memory `links` rather than re-querying the now-updated
        // child rows.
        entityManager.flush();
        entityManager.clear();

        return diveSiteRepository.findById(siteId).orElseThrow().toRecordWithLinks(true);
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
    public Optional<AdjacentDives> findAdjacentDives(final long userId, final long diveId) {
        return diveRepository
                .findByIdAndUser_Id(diveId, userId)
                .map(DiveEntity::getNumber)
                .map(
                        number -> {
                            final var previousId =
                                    diveRepository
                                            .findPreviousDiveNumber(userId, number)
                                            .flatMap(
                                                    n ->
                                                            diveRepository.findByUser_IdAndNumber(
                                                                    userId, n))
                                            .map(DiveEntity::getId)
                                            .orElse(null);
                            final var nextId =
                                    diveRepository
                                            .findNextDiveNumber(userId, number)
                                            .flatMap(
                                                    n ->
                                                            diveRepository.findByUser_IdAndNumber(
                                                                    userId, n))
                                            .map(DiveEntity::getId)
                                            .orElse(null);
                            return new AdjacentDives(previousId, nextId);
                        });
    }

    @Transactional(readOnly = true)
    public List<DiveSiteWithDives<DiveSite>> findDiveSitesByUser(
            final long userId, final boolean onlyOwn) {
        return findDiveSiteEntitiesByUser(userId, onlyOwn).stream()
                .map(d -> new DiveSiteWithDives<>(d.site().toRecord(), d.diveCount(), d.diveInfo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BasicDiveInfo> findDivesAtSiteForUser(
            final long userId, final long siteId, final boolean onlyOwn) {
        return onlyOwn
                ? diveRepository.findBasicDiveInfoByUserIdAndDiveSiteId(userId, siteId)
                : readerViewRepository.findBasicDiveInfoByUserIdAndDiveSiteId(userId, siteId);
    }

    @Transactional
    public GasEntity toEntity(final Gas gas) {
        final var size = Optional.ofNullable(gas.size()).map(this::toEntity);
        final var mix = toEntity(gas.o2(), gas.n2(), gas.he());
        final var entity = new GasEntity(gas, mix, size.orElse(null));
        final var existing = findLowestIdMatch(entity);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return gasRepository.save(entity);
        } catch (final DataIntegrityViolationException e) {
            // Lost a race with a concurrent request resolving the same gas composition - the DB's
            // unique constraint (see V0_3_6__gas_unique_constraint.sql) rejected our insert rather
            // than silently creating a second, indistinguishable row. Use the winner's row.
            return findLowestIdMatch(entity).orElseThrow(() -> e);
        }
    }

    /**
     * t_gas has no unique constraint prior to V0_3_6, so historical duplicates may still match the
     * same example (and even after the constraint, all-NULL optional columns aren't deduplicated by
     * Postgres). Pick the lowest id deterministically instead of failing on more than one match.
     */
    private Optional<GasEntity> findLowestIdMatch(final GasEntity example) {
        return gasRepository.findAll(Example.of(example)).stream()
                .min(Comparator.comparing(g -> g.id));
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
        if (userDive.hasBuddyDive(buddyDiveId)) {
            return toRecord(userDive);
        }
        final var buddyDive =
                diveRepository
                        .findById(buddyDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + buddyDiveId));
        // t_dive_buddy's CHECK constraint requires fk_dive_id < fk_buddy_dive_id - store
        // consistently regardless of which id the caller passed as "the dive" vs "the buddy", so
        // linking never fails just because the two ids were named in the "wrong" order.
        final var lower = userDiveId < buddyDiveId ? userDive : buddyDive;
        final var higher = userDiveId < buddyDiveId ? buddyDive : userDive;
        final var link = new DiveBuddyEntity(lower, higher);
        // Freshly linked, so both sides start role-less - fill in each diver's own saved default
        // for the other, if they have one (see setDefaultLinkedBuddyRole). Independent lookups:
        // either, both, or neither side may have a default set for the other.
        diveBuddyDefaultRoleRepository
                .findByUser_IdAndBuddyUser_Id(
                        userDive.getUserEntity().getId(), buddyDive.getUserEntity().getId())
                .ifPresent(d -> link.setRoleAsSeenFrom(userDiveId, d.getRole()));
        diveBuddyDefaultRoleRepository
                .findByUser_IdAndBuddyUser_Id(
                        buddyDive.getUserEntity().getId(), userDive.getUserEntity().getId())
                .ifPresent(d -> link.setRoleAsSeenFrom(buddyDiveId, d.getRole()));
        diveBuddyRepository.save(link);
        return reloadDive(userDiveId);
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
        final var link = diveBuddyRepository.findLink(userDiveId, buddyDiveId);
        if (link.isEmpty()) {
            return toRecord(userDive);
        }
        diveBuddyRepository.delete(link.get());
        return reloadDive(userDiveId);
    }

    /**
     * Sets the role of {@code buddyDiveId}'s diver, as rated from {@code viewpointDiveId}'s side of
     * an existing link - the two dives must already be linked via {@link #linkDive}.
     */
    @Transactional
    public Dive setBuddyDiveRole(
            final long viewpointDiveId, final long buddyDiveId, @Nullable final BuddyRole role) {
        final var link =
                diveBuddyRepository
                        .findLink(viewpointDiveId, buddyDiveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Dives "
                                                        + viewpointDiveId
                                                        + " and "
                                                        + buddyDiveId
                                                        + " are not linked buddy dives."));
        link.setRoleAsSeenFrom(viewpointDiveId, role);
        diveBuddyRepository.save(link);
        return reloadDive(viewpointDiveId);
    }

    /**
     * Clears the 1st-level cache and re-fetches - needed after mutating a DiveBuddyEntity directly
     * via the repository, since the dive's EAGER buddy-link collections were already loaded into
     * this session before the write and won't otherwise reflect it.
     */
    private Dive reloadDive(final long diveId) {
        entityManager.flush();
        entityManager.clear();
        return toRecord(diveRepository.findById(diveId).orElseThrow());
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
        // DATE needs its own query (see findByGroupPrivilegeOrderByDiveStart's own comment) -
        // native-query sorting can't reach the joined t_dive_summary column the generic
        // toSort()/Pageable-sort path relies on for every other column.
        final var divesPage =
                sort.column() == DiveSortColumn.DATE
                        ? diveRepository.findByGroupPrivilegeOrderByDiveStart(
                                groupId,
                                sort.direction() == SortDirection.ASCENDING,
                                PageRequest.of(page, simplifiedDivePageSize))
                        : diveRepository.findByGroupPrivilege(
                                groupId,
                                PageRequest.of(page, simplifiedDivePageSize, toSort(sort)));
        return PagedResponse.of(divesPage, this::toSimplifiedRecord);
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

    @Transactional
    public CcrUnit saveCcrUnit(final long userId, final CcrUnit ccrUnitWithoutId) {
        return ccrUnitRepository
                .save(
                        new CcrUnitEntity(
                                userRepository.findById(userId).orElseThrow(), ccrUnitWithoutId))
                .toRecord();
    }

    @Transactional(readOnly = true)
    public Optional<CcrUnit> findCcrUnitById(final long userId, final long id) {
        return ccrUnitRepository.findByIdAndUser_Id(id, userId).map(CcrUnitEntity::toRecord);
    }

    @Transactional
    public CcrUnit updateCcrUnitById(
            final long userId, final long id, final @Valid CcrUnit ccrUnit) {
        final var existing =
                ccrUnitRepository
                        .findByIdAndUser_Id(id, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find CCR unit by id " + id));
        existing.setName(ccrUnit.name());
        existing.setAdditionalNotes(ccrUnit.notes());
        existing.setPublic(ccrUnit.isPublic());
        existing.setMountPosition(ccrUnit.mountPosition());
        return ccrUnitRepository.save(existing).toRecord();
    }

    /**
     * Deletes a CCR unit and nothing else - every dive configuration and dive computer currently
     * linked to it is unlinked first (set to no unit), never deleted. No FK in the schema cascades
     * from a CCR unit to a configuration/computer, so skipping the unlink would simply make the
     * delete itself fail with a foreign-key violation once anything still references the unit.
     */
    @Transactional
    public void deleteCcrUnitById(final long userId, final long id) {
        final var existing =
                ccrUnitRepository
                        .findByIdAndUser_Id(id, userId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find CCR unit by id " + id));
        diveRepository.clearCcrUnitFromConfigurations(id);
        diveRepository.clearSecondaryCcrUnitFromConfigurations(id);
        diveComputerRepository.clearCcrUnitFromComputers(id);
        ccrUnitRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public List<String> findCcrUnitNameSuggestions(final @Nullable String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return ccrUnitRepository.findDistinctNames(query.trim());
    }

    @Transactional(readOnly = true)
    public List<User> findUsersByPublicCcrUnitName(final @Nullable String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return ccrUnitRepository.findUsersByPublicName(query.trim()).stream()
                .map(UserEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<CcrUnit> findCcrUnitsByUserId(
            final long id, final int page, final int pageSize) {
        return PagedResponse.of(
                ccrUnitRepository.findByUser_Id(
                        id,
                        PageRequest.of(
                                page,
                                pageSize,
                                Sort.by(new Sort.Order(Sort.Direction.DESC, "id")))),
                CcrUnitEntity::toRecord);
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
            final User user,
            final long computerId,
            final @NotBlank String customIdentifier,
            final @Nullable Long ccrUnitId) {
        final var computer =
                diveComputerRepository
                        .findByIdAndUser_Id(computerId, user.id())
                        .orElseThrow(() -> ForbiddenException.forDiveComputer(user, computerId));
        computer.setIdentifier(customIdentifier);
        if (ccrUnitId == null) {
            computer.setCcrUnit(null);
        } else {
            computer.setCcrUnit(
                    ccrUnitRepository
                            .findByIdAndUser_Id(ccrUnitId, user.id())
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "Could not find CCR unit by id " + ccrUnitId)));
        }
        return diveComputerRepository.save(computer).toRecord();
    }

    /**
     * The CCR unit this computer is permanently linked to, if any - used to infer a new dive's CCR
     * unit (and, via the unit's own default base configuration, its dive mode) from whichever
     * computer recorded it. See DiveService#inferConfigurationFromComputer.
     */
    @Transactional(readOnly = true)
    public Optional<CcrUnit> findCcrUnitLinkedToComputer(final long computerId) {
        return diveComputerRepository
                .findById(computerId)
                .map(DiveComputerEntity::getCcrUnit)
                .map(CcrUnitEntity::toRecord);
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

    /**
     * Sets the (primary) CCR unit on every dive in {@code ids} - a CCR unit is independent of the
     * dive's own {@code BaseConfiguration}, so this applies unconditionally to the whole batch.
     */
    @Transactional
    public void setCcrUnitById(
            final long userId, final long newCcrUnitId, final HashSet<Long> ids) {
        final var ccrUnit = ccrUnitRepository.findByIdAndUser_Id(newCcrUnitId, userId);
        if (ccrUnit.isEmpty()) {
            throw new NoSuchElementException("Could not find CCR unit by id " + newCcrUnitId);
        }
        diveRepository.setCcrUnit(ccrUnit.get(), ids);
    }

    @Transactional
    public void setWeight(final double newValue, final List<Long> diveIds) {
        diveRepository.setWeight(newValue, diveIds);
    }

    @Transactional(readOnly = true)
    public List<String> findBuddyNameSuggestions(final long userId, final @Nullable String query) {
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
     * Renames a named (free-text, non-linked-account) dive buddy across every dive the user owns.
     * If a dive already has a buddy entry with the new name, the old-name row on that dive is
     * dropped instead of renamed, to avoid duplicate buddy names on the same dive.
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
        final var oldMatches =
                diveBuddyNameRepository.findAllByDive_User_IdAndName(userId, trimmedOld);
        if (oldMatches.isEmpty()) {
            return 0;
        }
        final var diveIdsWithNewName =
                diveBuddyNameRepository.findAllByDive_User_IdAndName(userId, trimmedNew).stream()
                        .map(b -> b.getDive().getId())
                        .collect(Collectors.toSet());

        final var toDelete =
                oldMatches.stream()
                        .filter(b -> diveIdsWithNewName.contains(b.getDive().getId()))
                        .toList();
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

    /**
     * Sets the role for a named (free-text) buddy across every dive the user owns that lists them -
     * "all dives with this buddy", not a single dive at a time.
     *
     * @return the number of dives updated
     */
    @Transactional
    public int setNamedBuddyRole(
            final long userId, final String name, @Nullable final BuddyRole role) {
        final var matches =
                diveBuddyNameRepository.findAllByDive_User_IdAndName(userId, name.trim());
        matches.forEach(b -> b.setRole(role));
        diveBuddyNameRepository.saveAll(matches);
        return matches.size();
    }

    /**
     * Sets the role of {@code buddyUserId}'s diver, as rated from {@code userId}'s side, across
     * every linked dive pair between the two of them - "all dives with this buddy" for the
     * linked-account case.
     *
     * @return the number of links updated
     */
    @Transactional
    public int setLinkedBuddyRoleForUser(
            final long userId, final long buddyUserId, @Nullable final BuddyRole role) {
        final var links = diveBuddyRepository.findAllLinksBetweenUsers(userId, buddyUserId);
        links.forEach(
                l -> {
                    // setRoleAsSeenFrom takes a *dive* id, not a user id - resolve which side of
                    // this particular link row is the one userId actually owns.
                    final var viewpointDiveId =
                            l.getDive().getUserId() == userId
                                    ? l.getDive().getId()
                                    : l.getBuddyDive().getId();
                    l.setRoleAsSeenFrom(viewpointDiveId, role);
                });
        diveBuddyRepository.saveAll(links);
        return links.size();
    }

    /**
     * Saves (or, when {@code role} is null, clears) the diver's default role for a named (free-
     * text) buddy - applied automatically the next time this buddy is newly added to a dive, see
     * {@link #applyDefaultBuddyRoles}. Distinct from {@link #setNamedBuddyRole}, which instead
     * retroactively backfills every dive that already lists them.
     */
    @Transactional
    public void setDefaultNamedBuddyRole(
            final long userId, final String name, @Nullable final BuddyRole role) {
        final var trimmedName = name.trim();
        if (role == null) {
            diveBuddyDefaultRoleRepository.deleteByUser_IdAndBuddyName(userId, trimmedName);
            return;
        }
        final var userEntity = userRepository.findById(userId).orElseThrow();
        final var existing =
                diveBuddyDefaultRoleRepository.findByUser_IdAndBuddyName(userId, trimmedName);
        if (existing.isPresent()) {
            existing.get().setRole(role);
            diveBuddyDefaultRoleRepository.save(existing.get());
        } else {
            diveBuddyDefaultRoleRepository.save(
                    new DiveBuddyDefaultRoleEntity(userEntity, null, trimmedName, role));
        }
    }

    /**
     * Saves (or, when {@code role} is null, clears) the diver's default role for a linked-account
     * buddy, as rated from {@code userId}'s own side. See {@link #setDefaultNamedBuddyRole}.
     */
    @Transactional
    public void setDefaultLinkedBuddyRole(
            final long userId, final long buddyUserId, @Nullable final BuddyRole role) {
        if (role == null) {
            diveBuddyDefaultRoleRepository.deleteByUser_IdAndBuddyUser_Id(userId, buddyUserId);
            return;
        }
        final var userEntity = userRepository.findById(userId).orElseThrow();
        final var existing =
                diveBuddyDefaultRoleRepository.findByUser_IdAndBuddyUser_Id(userId, buddyUserId);
        if (existing.isPresent()) {
            existing.get().setRole(role);
            diveBuddyDefaultRoleRepository.save(existing.get());
        } else {
            final var buddyUserEntity = userRepository.findById(buddyUserId).orElseThrow();
            diveBuddyDefaultRoleRepository.save(
                    new DiveBuddyDefaultRoleEntity(userEntity, buddyUserEntity, null, role));
        }
    }

    /** Every default buddy role the user has saved, named and linked alike. */
    @Transactional(readOnly = true)
    public List<DiveBuddyDefaultRole> findDefaultBuddyRoles(final long userId) {
        return diveBuddyDefaultRoleRepository.findByUser_Id(userId).stream()
                .map(DiveBuddyDefaultRoleEntity::toRecord)
                .toList();
    }

    /**
     * Fills in a saved default role (see {@link #setDefaultNamedBuddyRole}) for every named buddy
     * in {@code buddies} that doesn't already have one - used right after a dive is first created
     * (import or otherwise), when every named buddy is still fresh and role-less. Never touches a
     * buddy that already carries a role.
     */
    private void applyDefaultBuddyRoles(
            final long userId, final List<DiveBuddyNameEntity> buddies) {
        final var roleless =
                buddies.stream()
                        .filter(b -> b.getRole() == null)
                        .map(DiveBuddyNameEntity::getName)
                        .toList();
        if (roleless.isEmpty()) {
            return;
        }
        final var defaults =
                diveBuddyDefaultRoleRepository
                        .findByUser_IdAndBuddyNameIn(userId, roleless)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        DiveBuddyDefaultRoleEntity::getBuddyName,
                                        DiveBuddyDefaultRoleEntity::getRole));
        buddies.forEach(
                b -> {
                    if (b.getRole() == null) {
                        final var defaultRole = defaults.get(b.getName());
                        if (defaultRole != null) {
                            b.setRole(defaultRole);
                        }
                    }
                });
    }

    /**
     * Every distinct user linked as a buddy on at least one of {@code userId}'s dives - powers the
     * "bulk-set this buddy's role everywhere" picker on the frontend.
     */
    @Transactional(readOnly = true)
    public List<User> findLinkedBuddyUsersForUser(final long userId) {
        final var fromLowerSide =
                diveBuddyRepository.findByDive_User_Id(userId).stream()
                        .map(l -> l.getBuddyDive().getUserEntity());
        final var fromHigherSide =
                diveBuddyRepository.findByBuddyDive_User_Id(userId).stream()
                        .map(l -> l.getDive().getUserEntity());
        return Stream.concat(fromLowerSide, fromHigherSide)
                .distinct()
                .map(UserEntity::toRecord)
                .toList();
    }

    private record BuddyRoleAssignment(
            BuddyRole role, String buddyLabel, String site, String year, String yearMonth) {}

    private static final String NAMED_BUDDY_ROLE_ASSIGNMENTS_SQL =
            """
            SELECT ds.dive_start AS dive_start, site.name AS site_name,
                   bn.role AS role, bn.name AS buddy_label
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            JOIN t_dive_site site ON site.pk_dive_site_id = d.dive_site
            JOIN t_dive_buddy_name bn ON bn.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId AND bn.role IS NOT NULL
            """;

    // Resolves each linked-buddy row's directional role exactly as DiveBuddyEntity.roleAsSeenFrom
    // does (role_of_buddy_from_dive when userId's own dive is the "dive" side of the pair,
    // role_of_dive_from_buddy when it's the "buddy_dive" side), plus the other diver's name, in
    // one query instead of hydrating every dive's full entity graph to call that method in Java.
    private static final String LINKED_BUDDY_ROLE_ASSIGNMENTS_SQL =
            """
            SELECT ds.dive_start AS dive_start, site.name AS site_name,
                   CASE WHEN tb.fk_dive_id = d.pk_dive_id THEN tb.role_of_buddy_from_dive
                        ELSE tb.role_of_dive_from_buddy END AS role,
                   other_user.name AS buddy_label
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            JOIN t_dive_site site ON site.pk_dive_site_id = d.dive_site
            JOIN t_dive_buddy tb ON tb.fk_dive_id = d.pk_dive_id OR tb.fk_buddy_dive_id = d.pk_dive_id
            JOIN t_dives d_other
                ON d_other.pk_dive_id =
                   CASE WHEN tb.fk_dive_id = d.pk_dive_id THEN tb.fk_buddy_dive_id ELSE tb.fk_dive_id END
            JOIN t_users other_user ON other_user.pk_user_id = d_other.fk_diver_id
            WHERE d.fk_diver_id = :userId
              AND ((tb.fk_dive_id = d.pk_dive_id AND tb.role_of_buddy_from_dive IS NOT NULL)
                OR (tb.fk_buddy_dive_id = d.pk_dive_id AND tb.role_of_dive_from_buddy IS NOT NULL))
            """;

    private BuddyRoleAssignment mapBuddyRoleAssignmentRow(final ResultSet rs) throws SQLException {
        final var start =
                ZonedDateTime.ofInstant(rs.getTimestamp("dive_start").toInstant(), ZoneOffset.UTC);
        return new BuddyRoleAssignment(
                BuddyRole.valueOf(rs.getString("role")),
                rs.getString("buddy_label"),
                rs.getString("site_name"),
                String.valueOf(start.getYear()),
                YearMonth.from(start).toString());
    }

    /**
     * How often each {@code BuddyRole} was assigned to a buddy (named or linked) across {@code
     * userId}'s own dives, broken down by buddy, by site, by year, and by year-month. Computed via
     * two grouped SQL queries (one per buddy kind) rather than hydrating every dive's full entity
     * graph (profiles, measurements, gas consumption, etc.) just to read its buddy roles - see
     * {@code DiveBuddyEntity.roleAsSeenFrom} for the directional-role logic mirrored in {@link
     * #LINKED_BUDDY_ROLE_ASSIGNMENTS_SQL} above.
     */
    @Transactional(readOnly = true)
    public BuddyRoleStats getBuddyRoleStats(final long userId) {
        // These two queries go straight through namedParameterJdbcTemplate, bypassing Hibernate's
        // own session - which normally auto-flushes pending writes before a *Hibernate* query but
        // has no reason to before a raw JDBC one. Without this, any not-yet-flushed change earlier
        // in the same transaction (e.g. a just-called DiveService.updateDive) would silently be
        // invisible to the aggregation below.
        entityManager.flush();
        final var params = new MapSqlParameterSource("userId", userId);
        final var assignments =
                Stream.concat(
                                namedParameterJdbcTemplate
                                        .query(
                                                NAMED_BUDDY_ROLE_ASSIGNMENTS_SQL,
                                                params,
                                                (rs, rowNum) -> mapBuddyRoleAssignmentRow(rs))
                                        .stream(),
                                namedParameterJdbcTemplate
                                        .query(
                                                LINKED_BUDDY_ROLE_ASSIGNMENTS_SQL,
                                                params,
                                                (rs, rowNum) -> mapBuddyRoleAssignmentRow(rs))
                                        .stream())
                        .toList();

        return new BuddyRoleStats(
                countByRole(assignments.stream()),
                groupByRole(assignments, BuddyRoleAssignment::buddyLabel),
                groupByRole(assignments, BuddyRoleAssignment::site),
                groupByRole(assignments, BuddyRoleAssignment::year),
                groupByRole(assignments, BuddyRoleAssignment::yearMonth));
    }

    private static List<BuddyRoleCount> countByRole(final Stream<BuddyRoleAssignment> stream) {
        return stream
                .collect(Collectors.groupingBy(BuddyRoleAssignment::role, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new BuddyRoleCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(BuddyRoleCount::count).reversed())
                .toList();
    }

    private static List<BuddyRoleBreakdown> groupByRole(
            final List<BuddyRoleAssignment> assignments,
            final Function<BuddyRoleAssignment, String> groupKey) {
        return assignments.stream().collect(Collectors.groupingBy(groupKey)).entrySet().stream()
                .map(
                        e ->
                                new BuddyRoleBreakdown(
                                        e.getKey(),
                                        countByRole(e.getValue().stream()),
                                        e.getValue().size()))
                .sorted(Comparator.comparingLong(BuddyRoleBreakdown::total).reversed())
                .toList();
    }
}
