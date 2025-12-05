package ch.sthomas.stddivelogger.model.exception;

public class MissingValueException extends RuntimeException {
    private final MissingValueField field;

    public MissingValueException(final MissingValueField field) {
        super("Missing value");
        this.field = field;
    }

    public MissingValueField getField() {
        return field;
    }
}
