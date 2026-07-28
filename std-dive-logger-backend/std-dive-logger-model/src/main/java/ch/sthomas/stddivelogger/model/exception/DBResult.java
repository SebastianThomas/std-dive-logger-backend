package ch.sthomas.stddivelogger.model.exception;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

public record DBResult<T>(@Nullable T value, @Nullable DiveDBConstraintException dbException) {

    public DBResult(@NotNull final T value) {
        this(value, null);
    }

    public DBResult {
        if (value != null && dbException != null) {
            throw new IllegalArgumentException("At least one of value and exception must be null");
        }
    }

    public T value() {
        if (isException()) {
            throw new UnsupportedOperationException("DBResult is an exception");
        }
        return value;
    }

    public DiveDBConstraintException dbException() {
        if (!isException()) {
            throw new UnsupportedOperationException("DBResult is not an exception");
        }
        return dbException;
    }

    public boolean isException() {
        return dbException != null;
    }
}
