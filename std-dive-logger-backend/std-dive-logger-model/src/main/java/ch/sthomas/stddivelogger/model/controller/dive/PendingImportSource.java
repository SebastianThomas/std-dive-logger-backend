package ch.sthomas.stddivelogger.model.controller.dive;

/** Where a {@code t_pending_import} row's raw data came from. */
public enum PendingImportSource {
    DIVESOFT,
    FIT_GARMIN,
    FIT_SUUNTO,
    JSON_SUUNTO,
    UDDF_SHEARWATER,
    XML_SUBSURFACE,
    XML_SHEARWATER,
    DL7_SHEARWATER
}
