package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiveService {

    private final DiveDataService diveDataService;

    public DiveService(final DiveDataService diveDataService) {
        this.diveDataService = diveDataService;
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
}
