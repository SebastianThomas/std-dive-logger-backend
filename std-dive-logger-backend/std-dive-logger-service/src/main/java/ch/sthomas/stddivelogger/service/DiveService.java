package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.user.User;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiveService {

    private final DiveDataService diveDataService;

    public DiveService(final DiveDataService diveDataService) {
        this.diveDataService = diveDataService;
    }

    public List<Dive> getDivesForUser(final User user) {
        return diveDataService.findDivesByUser(user);
    }

    public Dive saveDive(final UploadDiveBody body) {
        // TODO
        throw new NotImplementedException();
    }
}
