package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;

import org.apache.commons.io.FilenameUtils;

import java.util.Optional;

public abstract class BaseReaderService {

    protected String getDiveName(final UploadDiveBody body, final String filename) {
        return Optional.ofNullable(body.diveIdentifier())
                .orElse(FilenameUtils.getBaseName(filename));
    }
}
