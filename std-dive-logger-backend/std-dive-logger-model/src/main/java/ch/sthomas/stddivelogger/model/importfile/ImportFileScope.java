package ch.sthomas.stddivelogger.model.importfile;

/** How much a stored file holds. */
public enum ImportFileScope {
    SINGLE_PROFILE,
    SINGLE_DIVE_MULTI_PROFILE,
    MULTI_DIVE;

    public static ImportFileScope of(final int diveCount, final int profileCount) {
        if (diveCount > 1) {
            return MULTI_DIVE;
        }
        return profileCount > 1 ? SINGLE_DIVE_MULTI_PROFILE : SINGLE_PROFILE;
    }
}
