package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.*;
import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.exception.DBResult;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;
import ch.sthomas.stddivelogger.service.processing.ProfileAlignService;
import ch.sthomas.stddivelogger.utils.LocationUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hibernate.query.SortDirection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DiveService {

    private static final Logger logger = LoggerFactory.getLogger(DiveService.class);

    public static final int SIMPLIFIED_DIVE_PAGE_SIZE = 20;
    public static final int DIVE_SITE_PAGE_SIZE = 10;
    public static final int DIVE_COMPUTER_PAGE_SIZE = 10;
    public static final int DIVE_COMPUTER_MANUFACTURER_PAGE_SIZE = 20;
    public static final int USER_PAGE_SIZE = 30;
    public static final int SUIT_PAGE_SIZE = 20;
    public static final int CCR_UNIT_PAGE_SIZE = 20;

    /**
     * Above this many sites, {@link #getSitesByUser} stops inlining each site's full dive list
     * (id/number/identifier per dive) and returns only its count instead - the map view only needs
     * coordinates + a count to render markers, and fetches a site's actual dive list lazily via
     * {@code GET /v1/dives/sites/{id}/dives} only once its popup is opened. Below this, every site
     * keeps its full dive list inline, matching what most users have (a few dozen to a few hundred
     * sites) with no extra round trip.
     */
    public static final int SITE_LIST_LIGHTWEIGHT_THRESHOLD = 300;

    private final DiveDataService diveDataService;
    private final StorageService storageService;
    private final UserDataService userDataService;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance(100);

    public DiveService(
            final DiveDataService diveDataService,
            final StorageService storageService,
            final UserDataService userDataService) {
        this.diveDataService = diveDataService;
        this.storageService = storageService;
        this.userDataService = userDataService;
    }

    public PagedResponse<SimplifiedDive> getDivesForUser(
            final User user,
            @NotNull final DiveSort sort,
            final int page,
            final boolean includeReader) {
        if (!includeReader) {
            return diveDataService.findDivesByUser(user, sort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
        }
        return diveDataService.findDivesByUserIsReader(user, sort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    // includeReader is accepted but doesn't change anything: the underlying
    // fuzzy_search_dives_for_user() Postgres function (see V0_2_5__text.sql/V0_2_9__dive_tags.sql)
    // already scopes its candidate set to v_readers, which itself already includes the diver
    // (owner), buddies, explicit readers, and group readers - there's no "owned dives only" mode
    // to opt out into. Previously, includeReader=true threw NotImplementedException instead of
    // just returning the same (already reader-inclusive) results the false branch always gave.
    public PagedResponse<SimplifiedDive> searchDives(
            final User user, final String query, final boolean includeReader, final int page) {
        return diveDataService.searchDives(
                user.id(), query, PageRequest.of(page, SIMPLIFIED_DIVE_PAGE_SIZE));
    }

    public Optional<Dive> getDiveById(final User user, final long id) {
        if (!hasReadAccess(user, id)) {
            throw ForbiddenException.forDiveId(user, id);
        }
        return diveDataService.findDiveById(id);
    }

    public List<SimplifiedDive> getDivesByIds(final User user, final List<Long> ids) {
        return diveDataService.findDivesByIds(user, ids);
    }

    public PagedResponse<SimplifiedDive> getDivesByGroup(
            final User user, final long groupId, final int page) {
        if (!userDataService.isGroupMember(groupId, user)) {
            throw ForbiddenException.forGroup(user, groupId);
        }
        return diveDataService.findDivesByGroup(
                groupId,
                page,
                SIMPLIFIED_DIVE_PAGE_SIZE,
                // TODO: Change to Date
                DiveSort.ofNullable(DiveSortColumn.ID, SortDirection.ASCENDING));
    }

    // Deliberately NOT @Transactional: diveDataService.saveDive() below is @Transactional on its
    // own bean, so it already commits (and releases its DB connection) as soon as it returns -
    // wrapping this whole method in a transaction too would join that same transaction and keep
    // it (and its connection) open for the full duration of createSaveDivePreview()'s call into
    // external storage (with its own 15s timeouts and up to 5 retries), which under load or a
    // slow/degraded storage backend can tie up a pooled connection for well over a minute per
    // request. createSaveDivePreview() already treats a failed upload as best-effort (catches and
    // logs rather than throwing), so the dive itself is never at risk of being rolled back by a
    // preview-image problem even without a shared transaction.
    public DBResult<SimplifiedDive> saveDive(
            final User user,
            final Optional<Integer> diveNumberOptional,
            final String diveIdentifier,
            final String notes,
            @Nullable final Visibility visibility,
            @Nullable final DiveGasConsumption gasConsumption,
            @Nullable final DiveConfiguration configuration,
            final Long diveSiteId,
            final List<DiveProfileUpload> profiles,
            final List<String> namedBuddies) {
        final var diveNumber = diveNumberOptional.orElseGet(() -> getNextDiveNumber(user));
        final var effectiveConfiguration =
                inferConfigurationFromComputer(
                        user,
                        Optional.ofNullable(configuration)
                                .orElseGet(() -> DiveConfiguration.createEmpty(user)),
                        profiles);
        final var diveResult =
                diveDataService.saveDive(
                        user,
                        diveNumber,
                        diveIdentifier,
                        notes,
                        visibility,
                        Optional.ofNullable(gasConsumption).orElse(DiveGasConsumption.EMPTY),
                        effectiveConfiguration,
                        diveSiteId,
                        profiles,
                        namedBuddies);
        if (diveResult.isException()) {
            return new DBResult<>(null, diveResult.dbException());
        }
        createSaveDivePreview(diveResult.value());
        return new DBResult<>(
                diveDataService.findSimplifiedDiveById(diveResult.value().id()).orElseThrow());
    }

    /**
     * Auto-fills the CCR unit (and, via that unit's own default base configuration, the dive mode)
     * from whichever computer recorded this dive's first profile - an import reader has no way to
     * know the diver's own CCR units, but if that computer/handset was already linked to one (see
     * DiveComputer.ccrUnitId, set via ComputerController#updateDiveComputer), this reproduces what
     * a diver picking that unit manually would get, automatically.
     *
     * <p>A no-op whenever there's nothing to infer from: no profiles, an explicit CCR unit already
     * on the configuration (never overridden - the caller's own choice always wins), the computer
     * isn't linked to a unit, or that unit has no default base configuration set yet (the diver
     * hasn't confirmed one, so there's nothing safe to guess). The link itself is only evaluated at
     * import time - relinking a computer later doesn't retroactively change dives already saved.
     */
    // Package-private rather than private so DiveServiceTest can exercise it directly instead of
    // needing to mock the whole saveDive()/createSaveDivePreview() pipeline just to reach it.
    DiveConfiguration inferConfigurationFromComputer(
            final User user,
            final DiveConfiguration configuration,
            final List<DiveProfileUpload> profiles) {
        if (configuration.ccrUnit() != null || profiles.isEmpty()) {
            return configuration;
        }
        final var linkedCcrUnit =
                diveDataService
                        .findCcrUnitLinkedToComputer(profiles.getFirst().diveComputerId())
                        .orElse(null);
        if (linkedCcrUnit == null || linkedCcrUnit.defaultBaseConfiguration() == null) {
            return configuration;
        }
        // Defense in depth: the computer<->CCR-unit link is set via ComputerController, which
        // already scopes both the computer and the unit to the caller - this should be
        // unreachable in practice, but a link is only ever meaningful for the user who set it, so
        // never adopt one belonging to someone else regardless of how it got here.
        if (linkedCcrUnit.userId() != user.id()) {
            return configuration;
        }
        logger.info(
                "Inferred CCR unit {} (base {}) for user {} from dive computer {}",
                linkedCcrUnit.id(),
                linkedCcrUnit.defaultBaseConfiguration(),
                user.id(),
                profiles.getFirst().diveComputerId());
        return new DiveConfiguration(
                configuration.suit(),
                linkedCcrUnit.defaultBaseConfiguration(),
                configuration.weight(),
                configuration.weightFeeling(),
                configuration.cylinders(),
                linkedCcrUnit);
    }

    public SimplifiedDive addProfile(
            final User user,
            final DiveNumber diveNumber,
            final String newNotes,
            final DiveProfileUpload profile) {
        return diveDataService.addProfileToDiveWithDiveId(user, diveNumber, newNotes, profile);
    }

    private static final Map<
                    DiveMeasurement.DiveMeasurementProperty,
                    Pair<Function<DiveMeasurement, Double>, LegendType>>
            diveMeasurementLegendExtractors =
                    Map.ofEntries(
                            Map.entry(
                                    DiveMeasurement.DiveMeasurementProperty.DEPTH,
                                    Pair.of(DiveMeasurement::depth, LegendType.RIGHT)));

    public @Nullable Dive createSaveDivePreview(final User user, final long diveId) {
        final var dive = getDiveById(user, diveId).orElseThrow();
        final var result = createSaveDivePreview(dive);
        if (result == null || result.previewImage() == null) {
            return null;
        }
        return result;
    }

    public @Nullable Dive createSaveDivePreview(final Dive dive) {
        try {
            final var d = createSaveDivePreviewUnsafe(dive);
            logger.info("Added preview image {} to dive {} ({})", d.previewImage(), d.id(), d);
            return d;
        } catch (final IOException | IllegalArgumentException e) {
            logger.error("IOException while uploading dive preview for dive {}", dive.id(), e);
            return null;
        }
    }

    private Dive createSaveDivePreviewUnsafe(final Dive dive) throws IOException {
        final var previewImagePath = String.format("preview/%d.svg", dive.id());
        final var outputStream = new ByteArrayOutputStream();
        try (final var writer = new OutputStreamWriter(outputStream)) {
            GraphImageCreator.fromDive(
                    dive, writer, diveMeasurementLegendExtractors, new Dimension(500, 200));
        }
        final var bytes = outputStream.toByteArray();
        final var byteInput = new ByteArrayInputStream(bytes);
        storageService.upload(previewImagePath, byteInput, "image/svg+xml", bytes.length);
        return diveDataService.updateDiveSetPreviewImage(dive, previewImagePath);
    }

    public PagedResponse<DiveComputer> getDiveComputers(final User user, final int page) {
        return diveDataService.findDiveComputersByUser(user.id(), page, DIVE_COMPUTER_PAGE_SIZE);
    }

    public PagedResponse<DiveComputer> getDiveComputers(
            final User user, final String customName, final int page) {
        return diveDataService.findDiveComputersByUserAndName(
                user.id(), customName, page, DIVE_COMPUTER_PAGE_SIZE);
    }

    public Optional<DiveComputer> getDiveComputerById(final User user, final long id) {
        return diveDataService.findDiveComputerByUserAndId(user.id(), id);
    }

    public Optional<DiveComputer> getDiveComputer(final User user, final String customName) {
        return diveDataService.findDiveComputerByUserAndName(user.id(), customName);
    }

    public DiveComputer getOrCreateDiveComputer(
            final User user,
            final String manufacturer,
            final String serialNumber,
            final String customName) {
        final var existing = getDiveComputerBySerialNumber(user, manufacturer, serialNumber);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return createDiveComputer(serialNumber, customName, manufacturer, user.id());
        } catch (final DataIntegrityViolationException e) {
            // Lost a race with a concurrent request for the same computer (e.g. two imports for
            // the same user/serial number running at once) - t_dive_computer's own unique
            // constraint rejected our insert rather than creating a duplicate. Use the winner's
            // row instead of failing the request with a raw 500.
            return getDiveComputerBySerialNumber(user, manufacturer, serialNumber)
                    .orElseThrow(() -> e);
        }
    }

    public Optional<DiveComputer> getDiveComputerBySerialNumber(
            final User user, final String manufacturer, final String serialNumber) {
        return diveDataService.findDiveComputerByUserAndSerialNumber(
                user.id(), manufacturer, serialNumber);
    }

    public DiveComputer createDiveComputer(
            @Nullable final String serialNumber,
            @NotNull final String customIdentifier,
            final String manufacturer,
            final long userId) {
        return diveDataService.saveDiveComputer(
                serialNumber, customIdentifier, manufacturer, userId);
    }

    public long getDiveCount() {
        return diveDataService.getDiveCount();
    }

    public PagedResponse<DiveSite> getSiteByPartialName(
            final String locationStart, final int page) {
        return diveDataService.findDiveSiteByNameContains(locationStart, page, DIVE_SITE_PAGE_SIZE);
    }

    public Dive updateDive(final @NotNull User user, final @NotNull @Valid UpdateDiveBody dive)
            throws ForbiddenException {
        if (!hasWriteAccess(user, dive.id())) {
            throw ForbiddenException.forDiveId(user, dive.id());
        }
        return diveDataService.updateDive(user, dive);
    }

    /**
     * Refreshes auto-detected tags for the given dive and returns the updated dive. Intended to be
     * called by the frontend when opening the edit page.
     */
    public Dive refreshAutoTags(final User user, final long diveId) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return diveDataService.refreshAutoTags(diveId, user.id());
    }

    public Dive linkBuddyDive(final User user, final long userDive, final long buddyDive) {
        if (!hasWriteAccess(user, userDive) || !hasReadAccess(user, buddyDive)) {
            throw ForbiddenException.forDiveIds(user, List.of(userDive, buddyDive));
        }
        return diveDataService.linkDive(userDive, buddyDive);
    }

    public Dive unlinkBuddyDive(final User user, final long userDive, final long buddyDive) {
        if (!hasWriteAccess(user, userDive)) {
            throw ForbiddenException.forDiveId(user, userDive);
        }
        return diveDataService.unlinkDive(userDive, buddyDive);
    }

    public Dive mergeProfiles(
            final User user,
            final long baseDiveId,
            final long toAddDiveId,
            final boolean keepToAddDive) {
        if (!hasWriteAccess(user, baseDiveId)) {
            throw ForbiddenException.forDiveId(user, baseDiveId);
        }
        if (!hasReadAccess(user, toAddDiveId)) {
            throw ForbiddenException.forDiveId(user, toAddDiveId);
        }
        final var result = diveDataService.addProfilesToDive(baseDiveId, toAddDiveId);
        createSaveDivePreview(result);
        if (!keepToAddDive) {
            diveDataService.deleteDiveById(toAddDiveId);
        }
        return result;
    }

    /**
     * Removes a single profile from a dive without deleting the dive itself - the recovery path for
     * a profile attached/merged to the wrong dive by mistake (e.g. via import).
     */
    public Dive deleteProfile(final User user, final long diveId, final long profileId) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var result = diveDataService.deleteProfile(diveId, profileId);
        createSaveDivePreview(result);
        return result;
    }

    /**
     * Permanently deletes every measurement of a profile outside {@code [trimStart, trimEnd]} -
     * e.g. the trailing few minutes at 0.3-0.6m a Divesoft Liberty logs while waiting to have its
     * dive ended manually on the computer. Either bound may be {@code null} to only trim the other
     * end.
     */
    public Dive trimProfile(
            final User user,
            final long diveId,
            final long profileId,
            final @Nullable Instant trimStart,
            final @Nullable Instant trimEnd) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var result = diveDataService.trimProfile(diveId, profileId, trimStart, trimEnd);
        createSaveDivePreview(result);
        return result;
    }

    public Dive moveProfiles(final User user, final Long diveId, final List<Long> profileIds) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var dives = diveDataService.findDivesByProfileIds(profileIds);
        final var diveIds = dives.stream().map(Dive::id).toList();
        if (!diveIds.stream().allMatch(d -> hasWriteAccess(user, d))) {
            throw ForbiddenException.forDiveIds(user, diveIds);
        }
        return diveDataService.moveProfiles(diveId, profileIds);
    }

    public boolean hasWriteAccess(final @NotNull User user, final Set<Long> diveIds) {
        return diveDataService.hasWriteAccess(user, diveIds);
    }

    public boolean hasWriteAccess(final @NotNull User user, final long diveId) {
        return hasWriteAccess(user, Set.of(diveId));
    }

    public boolean hasReadAccess(final @NotNull User user, final long diveId) {
        return hasWriteAccess(user, diveId) || diveDataService.hasReadAccess(user, diveId);
    }

    public Dive createEmptyDive(final User user, @Valid @NotNull final UploadDiveBody body) {
        final var diveSiteId =
                Optional.ofNullable(body.diveSiteId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Dive Site is required to save dive manually."));
        final var maxDepth =
                Optional.ofNullable(body.maxDepth())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Maximum depth is required to save dive manually."));
        final var duration =
                Optional.ofNullable(body.duration())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Duration is required to save dive manually."));
        final var diveNumber =
                Optional.ofNullable(body.diveNumber()).orElseGet(() -> getNextDiveNumber(user));
        final var diveIdentifier =
                Optional.ofNullable(body.diveIdentifier())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Dive Identifier is required to save dive manually."));
        // TODO: Manual Dive Profile, with deepest depth, start and end time or dive time / duration
        return diveDataService
                .saveDive(
                        user,
                        diveNumber,
                        diveIdentifier,
                        "",
                        null,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        diveSiteId,
                        List.of(),
                        List.of())
                .value();
    }

    public Optional<DiveSite> getSiteById(final long id) {
        return diveDataService.findDiveSiteById(id);
    }

    public Optional<DiveSite> getSiteByName(final String name) {
        return diveDataService.findDiveSiteByName(name);
    }

    public List<DiveSite> getSitesByLocation(final Location coordinate) {
        return diveDataService.findDiveSitesByLocation(coordinate);
    }

    public DiveSite getOrCreateDiveSite(final String name, final Location location) {
        final var df = new DecimalFormat("0.###");
        final var existingByName = diveDataService.findDiveSiteByName(name);
        return existingByName
                .filter(e -> LocationUtils.isClose(e.getCoordinate(), location.toCoordinate()))
                .or(
                        () ->
                                diveDataService.findDiveSitesByLocation(location).stream()
                                        .filter(e -> isSimilarName(e.name(), name))
                                        .min(
                                                Comparator.comparing(
                                                        e ->
                                                                levenshteinDistance.apply(
                                                                        e.name(), name))))
                .or(
                        () ->
                                existingByName.map(
                                        _ ->
                                                diveDataService.saveDiveSite(
                                                        name
                                                                + " ("
                                                                + df.format(location.lat())
                                                                + ", "
                                                                + df.format(location.lon())
                                                                + ")",
                                                        location)))
                .orElseGet(() -> diveDataService.saveDiveSite(name, location));
    }

    public DiveSite createDiveSite(final String name, final Location location) {
        return diveDataService.saveDiveSite(name, location);
    }

    public void deleteDiveById(final User user, final long diveId) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        diveDataService.deleteDiveById(diveId);
    }

    public Dive updateTags(
            final User user,
            final long diveId,
            final List<Long> manualTagIds,
            final List<Long> dismissedAutoTagIds) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return diveDataService.updateTags(diveId, user.id(), manualTagIds, dismissedAutoTagIds);
    }

    public PagedResponse<User> getReaders(
            final @NotNull User authenticated, final long diveId, final int page) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        return diveDataService.findReaders(diveId, PageRequest.of(page, USER_PAGE_SIZE));
    }

    public PagedResponse<User> addReaders(
            final @NotNull User authenticated, final long diveId, final List<Long> userIds) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        final var currentReaders = diveDataService.findReadersInternal(diveId);
        final var userIdsSet = new HashSet<>(userIds);
        currentReaders.map(User::id).forEach(userIdsSet::remove);
        diveDataService.saveReaders(diveId, userIdsSet);
        return diveDataService.findReaders(diveId, Pageable.ofSize(USER_PAGE_SIZE));
    }

    public PagedResponse<User> removeReaders(
            final @NotNull User authenticated, final long diveId, final List<Long> userIds) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        final var currentReaders =
                diveDataService.findReadersInternal(diveId).map(User::id).toList();
        final var userIdsSet = new HashSet<>(userIds);
        userIdsSet.removeIf(userId -> !currentReaders.contains(userId));
        diveDataService.removeReaders(diveId, userIdsSet);
        return diveDataService.findReaders(diveId, Pageable.ofSize(USER_PAGE_SIZE));
    }

    public PagedResponse<User> addGroupReader(
            final @NotNull User authenticated, final long diveId, final long groupId) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        diveDataService.saveGroupReader(diveId, groupId);
        return diveDataService.findReaders(diveId, Pageable.ofSize(USER_PAGE_SIZE));
    }

    public List<Group> getGroupReaders(final @NotNull User authenticated, final long diveId) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        return diveDataService.getGroupReaders(diveId);
    }

    public PagedResponse<User> removeGroupReader(
            final @NotNull User authenticated, final long diveId, final long groupId) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        diveDataService.removeGroupReader(diveId, groupId);
        return diveDataService.findReaders(diveId, Pageable.ofSize(USER_PAGE_SIZE));
    }

    public int getNextDiveNumber(final User user) {
        return diveDataService.findMaxDiveNumber(user).orElse(0) + 1;
    }

    public PagedResponse<SimplifiedDive> getDiveByCustomIdentifier(
            final User user, final String query, final int page) {
        return diveDataService.findByIdentifierContains(
                user.id(), query, PageRequest.of(page, SIMPLIFIED_DIVE_PAGE_SIZE));
    }

    public PagedResponse<SimplifiedDive> getDivesByComputer(
            final User user, final long computerId, final DiveSort diveSort, final int page) {
        final var computer =
                getDiveComputerById(user, computerId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Cannot find computer with id " + computerId));
        return diveDataService.findDivesByUserAndComputer(
                user, computer, diveSort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public PagedResponse<SimplifiedDive> getDivesByTag(
            final User user, final long tagId, final DiveSort diveSort, final int page) {
        return diveDataService.findDivesByTag(
                user.id(), tagId, diveSort, PageRequest.of(page, SIMPLIFIED_DIVE_PAGE_SIZE));
    }

    public PagedResponse<SimplifiedDive> getDivesByTags(
            final User user, final List<Long> tagIds, final DiveSort diveSort, final int page) {
        return diveDataService.findDivesByTags(
                user.id(), tagIds, diveSort, PageRequest.of(page, SIMPLIFIED_DIVE_PAGE_SIZE));
    }

    public PagedResponse<SimplifiedDive> getDivesBySuit(
            final User user, final long suitId, final DiveSort diveSort, final int page) {
        final var suit = getSuitById(user, suitId);
        return diveDataService.findDivesByUserAndSuit(
                user, suit, diveSort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public PagedResponse<SimplifiedDive> getDivesByCcrUnit(
            final User user, final long ccrUnitId, final DiveSort diveSort, final int page) {
        final var ccrUnit = getCcrUnitById(user, ccrUnitId);
        return diveDataService.findDivesByUserAndCcrUnit(
                user, ccrUnit, diveSort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public PagedResponse<SimplifiedDive> getFilteredDives(
            final User user,
            final DiveFilterParams filters,
            final DiveSort diveSort,
            final int page) {
        return diveDataService.findFiltered(
                user.id(), filters, diveSort, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public List<DiveSiteWithDives<DiveSite>> getSitesByUser(
            final User user, final boolean onlyOwn) {
        final var sites = diveDataService.findDiveSitesByUser(user.id(), onlyOwn);
        if (sites.size() <= SITE_LIST_LIGHTWEIGHT_THRESHOLD) {
            return sites;
        }
        return sites.stream().map(DiveSiteWithDives::withoutDiveInfo).toList();
    }

    /**
     * The lazily-fetched counterpart to a {@link DiveSiteWithDives} whose {@code diveInfo} was
     * stripped by {@link #getSitesByUser} for having too many sites - fetches just this one site's
     * dive list on demand (e.g. when the map's marker popup for it is opened).
     */
    public List<BasicDiveInfo> getDivesAtSiteForUser(
            final User user, final long siteId, final boolean onlyOwn) {
        return diveDataService.findDivesAtSiteForUser(user.id(), siteId, onlyOwn);
    }

    private boolean isSimilarName(final CharSequence a, final CharSequence b) {
        final var dist = levenshteinDistance.apply(a, b);
        if (dist == null || dist < 0) {
            return false;
        }
        return dist <= 1 || dist < 1.0 / 10 * a.length();
    }

    public Dive alignProfilesManualToTime(
            final User user,
            final Set<Long> profileIds,
            final long diveId,
            final Instant alignToManual) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return diveDataService.alignProfilesManual(profileIds, diveId, alignToManual);
    }

    public Dive alignProfilesAuto(
            final User user,
            final long referenceProfileId,
            final long targetProfileId,
            final int diveId,
            final AlignType type) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var dive =
                diveDataService
                        .findDiveById(diveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + diveId));
        final var profiles = dive.profiles();
        final var referenceProfile =
                profiles.stream()
                        .filter(profile -> profile.id() == referenceProfileId)
                        .findFirst()
                        .orElseThrow();
        final var targetProfile =
                profiles.stream()
                        .filter(profile -> profile.id() == targetProfileId)
                        .findFirst()
                        .orElseThrow();
        final var alignAdjustment =
                ProfileAlignService.alignProfilesAuto(referenceProfile, targetProfile, type);
        return alignProfilesManualToTime(user, Set.of(targetProfileId), diveId, alignAdjustment);
    }

    public Dive resetAlignedProfiles(
            final User user, final int diveId, final Set<Long> profileIds) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var dive =
                diveDataService
                        .findDiveById(diveId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive by id " + diveId));
        final var profiles =
                dive.profiles().stream()
                        .filter(p -> profileIds.contains(p.id()))
                        .collect(Collectors.toSet());
        if (profiles.size() != profileIds.size()) {
            throw new IllegalArgumentException(
                    "Could not find all profiles "
                            + profileIds
                            + ", only found "
                            + profiles.stream().map(DiveProfile::id).toList());
        }
        return diveDataService.resetAlignedProfiles(diveId, profileIds);
    }

    /**
     * Replaces a single profile's raw measurement data by re-parsing its original source file.
     * Leaves every other dive property (suit, gas consumption, weight, visibility, notes, tags,
     * buddies, ...) untouched. Recovery tool for fixing importer bugs after the fact.
     */
    public Dive reimportProfile(
            final User user,
            final long diveId,
            final long profileId,
            final List<DiveMeasurement> newMeasurements,
            final Instant start,
            final Instant end) {
        if (!hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return diveDataService.reimportProfileMeasurements(
                diveId, profileId, newMeasurements, start, end);
    }

    public Suit createSuit(
            final @NotNull User user,
            final @NotNull SuitType type,
            @Nullable final Double thickness,
            @Nullable final String notes) {
        return diveDataService.saveSuit(
                user.id(),
                new Suit(null, user.id(), type, thickness, Objects.requireNonNullElse(notes, "")));
    }

    public PagedResponse<Suit> getSuits(final User user, final int page) {
        return diveDataService.findSuitsByUserId(user.id(), page, SUIT_PAGE_SIZE);
    }

    public Suit getSuitById(final User user, final long id) {
        return diveDataService
                .findSuitById(user.id(), id)
                .orElseThrow(() -> new NoSuchElementException("Could not find suit by id " + id));
    }

    public Suit updateSuit(final @NotNull User user, final long id, @Valid final Suit suit) {
        return diveDataService.updateSuitById(user.id(), id, suit);
    }

    public CcrUnit createCcrUnit(
            final @NotNull User user,
            final @NotNull String name,
            @Nullable final String notes,
            final boolean isPublic) {
        return diveDataService.saveCcrUnit(
                user.id(),
                new CcrUnit(
                        null,
                        user.id(),
                        name,
                        Objects.requireNonNullElse(notes, ""),
                        isPublic,
                        null));
    }

    public PagedResponse<CcrUnit> getCcrUnits(final User user, final int page) {
        return diveDataService.findCcrUnitsByUserId(user.id(), page, CCR_UNIT_PAGE_SIZE);
    }

    public CcrUnit getCcrUnitById(final User user, final long id) {
        return diveDataService
                .findCcrUnitById(user.id(), id)
                .orElseThrow(
                        () -> new NoSuchElementException("Could not find CCR unit by id " + id));
    }

    public CcrUnit updateCcrUnit(
            final @NotNull User user, final long id, @Valid final CcrUnit ccrUnit) {
        return diveDataService.updateCcrUnitById(user.id(), id, ccrUnit);
    }

    /**
     * Deletes a CCR unit only - unlinks it from every dive configuration and dive computer that
     * currently references it, never touches the dives/profiles/computers themselves. See {@link
     * ch.sthomas.stddivelogger.data.service.DiveDataService#deleteCcrUnitById}.
     */
    public void deleteCcrUnit(final @NotNull User user, final long id) {
        diveDataService.deleteCcrUnitById(user.id(), id);
    }

    /**
     * Deletes every dive that uses this CCR unit, then the unit itself (unlinking anything left,
     * e.g. a computer with no dives). Ownership-checked per dive the same way a normal single-dive
     * delete is - a dive whose id turns up under this CCR unit but that the user no longer has
     * write access to is simply skipped rather than failing the whole batch. Intentionally
     * destructive and only ever reached via an explicit, heavily-confirmed frontend action - not a
     * side effect of the plain {@link #deleteCcrUnit} above.
     *
     * <p>Wrapped in a single transaction (unlike most orchestration in this class - see the {@code
     * saveDive} comment above for why that's usually deliberately avoided) because every step here
     * is a plain DB delete with no slow external I/O to hold a connection open across - so there's
     * no downside to atomicity, and every upside: a failure partway through a multi-dive batch must
     * not leave some dives permanently deleted while others (and the CCR unit itself) survive.
     */
    @Transactional
    public int deleteCcrUnitAndAllDives(final @NotNull User user, final long id) {
        // Ownership-checked first so a unit id belonging to someone else 404s here rather than
        // silently returning zero deleted dives.
        getCcrUnitById(user, id);
        final var diveIds = diveDataService.findDiveIdsByUserAndCcrUnit(user.id(), id);
        var deleted = 0;
        for (final var diveId : diveIds) {
            if (hasWriteAccess(user, diveId)) {
                diveDataService.deleteDiveById(diveId);
                deleted++;
            }
        }
        deleteCcrUnit(user, id);
        return deleted;
    }

    public List<String> getCcrUnitNameSuggestions(final String query) {
        return diveDataService.findCcrUnitNameSuggestions(query);
    }

    public List<FrontendUser> getUsersByPublicCcrUnitName(final String query) {
        return diveDataService.findUsersByPublicCcrUnitName(query).stream()
                .map(User::toFrontendModel)
                .toList();
    }

    public PagedResponse<DiveComputerManufacturer> getDiveComputerManufacturers(final int page) {
        return diveDataService.findDiveComputerManufacturers(
                PageRequest.of(page, DIVE_COMPUTER_MANUFACTURER_PAGE_SIZE));
    }

    public DiveComputer updateDiveComputer(
            final User user,
            final long computerId,
            final @NotBlank String customIdentifier,
            final @Nullable Long ccrUnitId) {
        return diveDataService.updateDiveComputer(user, computerId, customIdentifier, ccrUnitId);
    }

    public int deleteUnusedDiveComputers(final User user) {
        return diveDataService.deleteUnusedDiveComputers(user);
    }

    public void setSuit(final User user, final List<Long> diveIds, final long newValue) {
        final var ids = new HashSet<>(diveIds);
        if (!hasWriteAccess(user, ids)) {
            throw ForbiddenException.forDiveIds(user, ids);
        }
        diveDataService.setSuitById(user.id(), newValue, ids);
    }

    public void setCcrUnit(final User user, final List<Long> diveIds, final long newValue) {
        final var ids = new HashSet<>(diveIds);
        if (!hasWriteAccess(user, ids)) {
            throw ForbiddenException.forDiveIds(user, ids);
        }
        diveDataService.setCcrUnitById(user.id(), newValue, ids);
    }

    public void updateBaseConfigurationDives(
            final User user, final List<Long> idsList, final BaseConfiguration newValue) {
        final var ids = new HashSet<>(idsList);
        if (!hasWriteAccess(user, ids)) {
            throw ForbiddenException.forDiveIds(user, ids);
        }
        diveDataService.setBaseConfiguration(newValue, ids);
    }

    public void updateWeightDives(
            final User user, final List<Long> diveIds, final double newValue) {
        final var ids = new HashSet<>(diveIds);
        if (!hasWriteAccess(user, ids)) {
            throw ForbiddenException.forDiveIds(user, ids);
        }
        diveDataService.setWeight(newValue, diveIds);
    }

    public List<String> getBuddyNameSuggestions(final User user, final String query) {
        return diveDataService.findBuddyNameSuggestions(user.id(), query);
    }

    public List<String> getAllBuddyNames(final User user) {
        return diveDataService.findAllBuddyNames(user.id());
    }

    /**
     * Renames a named dive buddy across every dive the user owns. Scoped to {@code user.id()}, so
     * there is no separate ownership check to perform here.
     */
    public List<String> renameBuddyName(
            final User user, @Nullable final String oldName, @Nullable final String newName) {
        if (oldName == null || oldName.isBlank()) {
            throw new IllegalArgumentException("Buddy name to rename must not be blank");
        }
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("New buddy name must not be blank");
        }
        diveDataService.renameBuddyName(user.id(), oldName, newName);
        return diveDataService.findAllBuddyNames(user.id());
    }
}
