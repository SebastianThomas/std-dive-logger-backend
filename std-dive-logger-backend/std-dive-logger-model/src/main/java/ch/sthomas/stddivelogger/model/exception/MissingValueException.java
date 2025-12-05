package ch.sthomas.stddivelogger.model.exception;

public class MissingValueException extends RuntimeException {
    private final ExceptionReason reason;
    private final MissingValueField field;
    private final String additionalInfo;

    public MissingValueException(final MissingValueField field) {
        this(field, null);
    }

    public MissingValueException(final MissingValueField field, final String additionalInfo) {
        super("Missing value");
        this.reason = ExceptionReason.MISSING_VALUE;
        this.field = field;
        this.additionalInfo = additionalInfo;
    }

    public ExceptionReason reason() {
        return reason;
    }

    public MissingValueField getField() {
        return field;
    }

    public String getAdditionalMessage() {
        return additionalInfo;
    }
}
