package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.hibernate.exception.DataException;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiveService {

    private static final Logger logger = LoggerFactory.getLogger(DiveService.class);

    public static final int SIMPLIFIED_DIVE_PAGE_SIZE = 20;
    public static final int DIVE_SITE_PAGE_SIZE = 10;

    private final DiveDataService diveDataService;

    public DiveService(final DiveDataService diveDataService) {
        this.diveDataService = diveDataService;
    }

    public List<SimplifiedDive> getDivesForUser(final User user, final int page) {
        return diveDataService.findDivesByUser(user, page, SIMPLIFIED_DIVE_PAGE_SIZE);
    }

    public Optional<Dive> getDiveById(final User user, final long id) {
        if (!hasReadAccess(user, id)) {
            throw ForbiddenException.forDiveId(user, id);
        }
        return diveDataService.findDiveById(id);
    }

    public Dive saveDive(
            final User user,
            final UploadDiveBody body,
            final Long diveSiteId,
            final List<DiveProfileUpload> profiles,
            final List<String> namedBuddies) {
        final var dive =
                diveDataService.saveDive(
                        user,
                        body.diveNumber(),
                        body.diveIdentifier(),
                        diveSiteId,
                        profiles,
                        namedBuddies);
        try {
            diveDataService.saveBuddies(dive.id(), namedBuddies);
        } catch (final DataException e) {
            logger.error("Error while saving dive buddies, but continuing", e);
        }
        return dive;
    }

    public Optional<DiveComputer> getDiveComputer(final User user, final String customName) {
        return diveDataService.findDiveComputerByUserAndName(user.id(), customName);
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

    public List<DiveSite> getSiteByPartialName(final String locationStart) {
        return diveDataService.findDiveSiteByNameContains(locationStart, DIVE_SITE_PAGE_SIZE);
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
        // TODO: Manual Dive Profile, with deepest depth, start and end time or dive time / duration
        return diveDataService.saveDive(
                user,
                body.diveNumber(),
                body.diveIdentifier(),
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

    public DiveSite createDiveSite(final String name, final double lat, final double lon) {
        return diveDataService.saveDiveSite(name, new Coordinate(lon, lat));
    }

    public List<User> getReaders(final @NotNull User authenticated, final long diveId) {
        if (!hasWriteAccess(authenticated, diveId)) {
            throw ForbiddenException.forDiveId(authenticated, diveId);
        }
        return diveDataService.findReaders(diveId);
    }

    public int getNextDiveNumber(final User user) {
        return diveDataService.findMaxDiveNumber(user).orElse(0) + 1;
    }
}
