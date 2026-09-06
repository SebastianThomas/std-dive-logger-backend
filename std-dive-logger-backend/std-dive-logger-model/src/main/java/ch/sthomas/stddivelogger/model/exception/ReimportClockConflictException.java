package ch.sthomas.stddivelogger.model.exception;

import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportConflicts;

public class ReimportClockConflictException extends IllegalArgumentException {
    private final ReimportConflicts.ClockOffset clockOffset;

    public ReimportClockConflictException(final ReimportConflicts.ClockOffset clockOffset) {
        super("The profile times differ. Pick which start time to keep.");
        this.clockOffset = clockOffset;
    }

    public ReimportConflicts.ClockOffset getClockOffset() {
        return clockOffset;
    }
}
