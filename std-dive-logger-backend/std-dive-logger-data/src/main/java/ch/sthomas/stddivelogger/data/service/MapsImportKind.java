package ch.sthomas.stddivelogger.data.service;

/** The boundary sources that {@link MapsImportRunStore} orchestrates imports for. */
public enum MapsImportKind {
    /** osm2pgsql import of an OpenStreetMap extract, covering that extract only. */
    OSM("db/maps/promote-boundaries.sql"),
    /** geoBoundaries CGAZ import, covering the whole world at ADM0/ADM1. */
    CGAZ("db/maps/promote-cgaz.sql");

    private final String promotionScript;

    MapsImportKind(final String promotionScript) {
        this.promotionScript = promotionScript;
    }

    public String getPromotionScript() {
        return promotionScript;
    }
}
