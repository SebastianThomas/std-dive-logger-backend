package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.exception.DataException;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
public class DiveService {

    private static final Logger logger = LoggerFactory.getLogger(DiveService.class);

    public static final int SIMPLIFIED_DIVE_PAGE_SIZE = 20;
    public static final int DIVE_SITE_PAGE_SIZE = 10;
    public static final int USER_PAGE_SIZE = 30;

    private final DiveDataService diveDataService;
    private final StorageService storageService;

    public DiveService(final DiveDataService diveDataService, final StorageService storageService) {
        this.diveDataService = diveDataService;
        this.storageService = storageService;
    }

    public PagedResponse<SimplifiedDive> getDivesForUser(final User user, final int page) {
        return diveDataService.findDivesByUser(user, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public Optional<Dive> getDiveById(final User user, final long id) {
        if (!hasReadAccess(user, id)) {
            throw ForbiddenException.forDiveId(user, id);
        }
        return diveDataService.findDiveById(id);
    }

    @Transactional
    public SimplifiedDive saveDive(
            final User user,
            final Optional<Integer> diveNumberOptional,
            final String diveIdentifier,
            final Long diveSiteId,
            final List<DiveProfileUpload> profiles,
            final List<String> namedBuddies) {
        final var diveNumber = diveNumberOptional.orElseGet(() -> getNextDiveNumber(user));
        final var dive =
                diveDataService.saveDive(
                        user, diveNumber, diveIdentifier, null, diveSiteId, profiles, namedBuddies);
        try {
            final var d = createSaveDivePreview(dive);
            logger.info("Added preview image {} to dive {} ({})", d.previewImage(), d.id(), d);
        } catch (final IOException e) {
            logger.error("IOException while uploading dive preview for dive {}", dive.id(), e);
        }
        try {
            diveDataService.saveBuddies(dive.id(), namedBuddies);
        } catch (final DataException e) {
            logger.error("Error while saving dive buddies, but continuing", e);
        }
        return diveDataService.findSimplifiedDiveById(dive.id()).orElseThrow();
    }

    private static final Map<
                    DiveMeasurement.DiveMeasurementProperty,
                    Pair<Function<DiveMeasurement, Double>, LegendType>>
            diveMeasurementLegendExtractors = Map.ofEntries();

    private Dive createSaveDivePreview(final Dive dive) throws IOException {
        final var previewImagePath = String.format("/preview/%d-preview.svg", dive.id());
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

    public Dive updateDive(final @NotNull User user, @NotNull @Valid final Dive dive)
            throws ForbiddenException {
        if (!hasWriteAccess(user, dive.id())) {
            throw ForbiddenException.forDiveId(user, dive.id());
        }
        return diveDataService.updateDive(dive);
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
        if (body.diveSiteId() == null) {
            throw new IllegalArgumentException("Dive Site is required to save dive manually.");
        }
        final var diveNumber =
                body.diveNumber() != null ? body.diveNumber() : getNextDiveNumber(user);
        // TODO: Manual Dive Profile, with deepest depth, start and end time or dive time / duration
        return diveDataService.saveDive(
                user,
                diveNumber,
                body.diveIdentifier(),
                null,
                body.diveSiteId(),
                List.of(),
                List.of());
    }

    public Optional<DiveSite> getSiteById(final long id) {
        return diveDataService.findDiveSiteById(id);
    }

    public List<DiveSite> getSitesByLocation(final Coordinate coordinate) {
        return diveDataService.findDiveSitesByLocation(coordinate);
    }

    public DiveSite createDiveSite(final String name, final Location location) {
        return diveDataService.saveDiveSite(name, location);
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
        final var currentReaders = diveDataService.findReaders(diveId, Pageable.unpaged());
        final var userIdsSet = new HashSet<>(userIds);
        currentReaders.result().stream().map(User::id).forEach(userIdsSet::remove);
        diveDataService.saveReaders(diveId, userIdsSet);
        return diveDataService.findReaders(diveId, Pageable.ofSize(USER_PAGE_SIZE));
    }

    public PagedResponse<User> removeReaders(
            final @NotNull User authenticated, final long diveId, final List<Long> userIds) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        final var currentReaders =
                diveDataService.findReaders(diveId, Pageable.unpaged()).result().stream()
                        .map(User::id)
                        .toList();
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
}
