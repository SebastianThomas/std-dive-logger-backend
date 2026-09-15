package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;

/**
 * Output version of each importer. Bump a source's number whenever the same file would now parse
 * differently (a fixed field, a new value read, a clock fix): the re-processing job then re-derives
 * every profile and dive linked to a stored file of that source, applies what only adds data and
 * asks the diver about the rest.
 */
public final class ImportParserVersions {
    private ImportParserVersions() {}

    public static int current(final PendingImportSource source) {
        return switch (source) {
            case DIVESOFT -> 1;
            case FIT_GARMIN -> 1;
            case FIT_SUUNTO -> 1;
            case JSON_SUUNTO -> 1;
            case UDDF_SHEARWATER -> 1;
            case XML_SUBSURFACE -> 1;
            case XML_SHEARWATER -> 1;
            case DL7_SHEARWATER -> 1;
            case DB_SHEARWATER -> 1;
        };
    }
}
