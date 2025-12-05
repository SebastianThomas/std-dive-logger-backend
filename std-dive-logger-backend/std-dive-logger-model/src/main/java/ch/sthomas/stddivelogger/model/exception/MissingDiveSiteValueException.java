package ch.sthomas.stddivelogger.model.exception;

public class MissingDiveSiteValueException extends MissingValueException {
    private final String name;

    public MissingDiveSiteValueException(final String name) {
        super(MissingValueField.DIVE_SITE, "Could not find Dive Site by name.");
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
