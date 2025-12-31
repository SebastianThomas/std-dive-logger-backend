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
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;
import ch.sthomas.stddivelogger.utils.LocationUtils;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hibernate.query.SortDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;

@Service
public class DiveService {

    private static final Logger logger = LoggerFactory.getLogger(DiveService.class);

    public static final int SIMPLIFIED_DIVE_PAGE_SIZE = 20;
    public static final int DIVE_SITE_PAGE_SIZE = 10;
    public static final int DIVE_COMPUTER_PAGE_SIZE = 10;
    public static final int USER_PAGE_SIZE = 30;

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

    public PagedResponse<SimplifiedDive> searchDives(
            final User user, final String query, final boolean includeReader, final int page) {
        if (!includeReader) {
            return diveDataService.searchDives(
                    user.id(), query, PageRequest.of(page, SIMPLIFIED_DIVE_PAGE_SIZE));
        }
        throw new NotImplementedException();
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

    @Transactional
    public SimplifiedDive saveDive(
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
        final var dive =
                diveDataService.saveDive(
                        user,
                        diveNumber,
                        diveIdentifier,
                        notes,
                        visibility,
                        gasConsumption,
                        configuration,
                        diveSiteId,
                        profiles,
                        namedBuddies);
        createSaveDivePreview(dive);
        return diveDataService.findSimplifiedDiveById(dive.id()).orElseThrow();
    }

    private static final Map<
                    DiveMeasurement.DiveMeasurementProperty,
                    Pair<Function<DiveMeasurement, Double>, LegendType>>
            diveMeasurementLegendExtractors =
                    Map.ofEntries(
                            Map.entry(
                                    DiveMeasurement.DiveMeasurementProperty.DEPTH,
                                    Pair.of(DiveMeasurement::depth, LegendType.RIGHT)));

    public Dive createSaveDivePreview(final User user, final long diveId) {
        final var dive = getDiveById(user, diveId).orElseThrow();
        final var result = createSaveDivePreview(dive);
        if (result == null || result.previewImage() == null) {
            return null;
        }
        return result;
    }

    public Dive createSaveDivePreview(final Dive dive) {
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
        return getDiveComputerBySerialNumber(user, manufacturer, serialNumber)
                .orElseGet(
                        () ->
                                createDiveComputer(
                                        serialNumber, customName, manufacturer, user.id()));
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
        return diveDataService.updateDive(dive);
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
        if (!keepToAddDive) {
            diveDataService.deleteDiveById(toAddDiveId);
        }
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

    public boolean hasWriteAccess(final @NotNull User user, final long diveId) {
        final var diveUser = diveDataService.findUserForDive(diveId);
        return diveUser.isPresent()
                && diveUser.get().id() == user.id()
                && diveUser.get().email().equals(user.email());
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
        // TODO: Manual Dive Profile, with deepest depth, start and end time or dive time / duration
        return diveDataService.saveDive(
                user,
                diveNumber,
                body.diveIdentifier(),
                "",
                null,
                DiveGasConsumption.EMPTY,
                DiveConfiguration.EMPTY,
                diveSiteId,
                List.of(),
                List.of());
    }

    public Optional<DiveSite> getSiteById(final long id) {
        return diveDataService.findDiveSiteById(id);
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

    // TODO: Pagination
    public List<DiveSiteWithDives<DiveSite>> getSitesByUser(
            final User user, final boolean onlyOwn) {
        return diveDataService.findDiveSitesByUser(user.id(), onlyOwn);
    }

    private boolean isSimilarName(final CharSequence a, final CharSequence b) {
        final var dist = levenshteinDistance.apply(a, b);
        if (dist == null || dist < 0) {
            return false;
        }
        return dist <= 1 || dist < 1.0 / 10 * a.length();
    }
}
