package ch.sthomas.stddivelogger.service.importer;

import org.apache.commons.io.FilenameUtils;

public abstract class BaseReaderService {

    protected String getDiveName(final String filename) {
        return FilenameUtils.getBaseName(filename);
    }
}
