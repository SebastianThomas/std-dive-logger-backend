package ch.sthomas.stddivelogger.model.exception;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

public record DBResult<T>(@Nullable T value, @Nullable DiveDBConstraintException dbException) {

    public DBResult(@NotNull final T value) {
        this(value, null);
    }

    public DBResult {
        if (value != null && dbException != null) {
            throw new IllegalArgumentException("At least one of value and exception must be null");
        }
    }

    // isException()/the constructor invariant guarantee value is non-null here, but that's a
    // runtime invariant NullAway's flow analysis can't see across the two accessor methods.
    @SuppressWarnings("NullAway")
    public T value() {
        if (isException()) {
            throw new UnsupportedOperationException("DBResult is an exception");
        }
        return value;
    }

    @SuppressWarnings("NullAway")
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
