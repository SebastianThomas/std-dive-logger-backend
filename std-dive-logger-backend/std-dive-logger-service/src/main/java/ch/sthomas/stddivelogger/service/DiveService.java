package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiveService {

    private final DiveDataService diveDataService;
    private final UserDataService userDataService;

    public DiveService(final DiveDataService diveDataService, UserDataService userDataService) {
        this.diveDataService = diveDataService;
        this.userDataService = userDataService;
    }

    public List<Dive> getDivesForUser(final User user) {
        return diveDataService.findDivesByUser(user);
    }

    public Dive saveDive(
            final UploadDiveBody body,
            final Long diveSiteId,
            final List<DiveProfileUpload> profiles) {
        return diveDataService.saveDive(
                body.diveNumber(), body.diveIdentifier(), body.userId(), diveSiteId, profiles);
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
        return diveDataService.findDiveSiteByNameContains(locationStart);
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
        return hasWriteAccess(user, diveId); // TODO: Or is in table for reads
    }

    public ResponseEntity<Dive> createEmptyDive(
            final User user, @Valid @NotNull final UploadDiveBody body) {
        // TODO: Manual Dive Profile, with deepest depth, start and end time or duration
        throw new NotImplementedException("Backend TODO");
    }
}
