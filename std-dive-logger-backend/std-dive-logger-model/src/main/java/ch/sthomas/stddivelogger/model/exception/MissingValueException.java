package ch.sthomas.stddivelogger.model.exception;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MissingValueException extends AbstractThrowableProblem {
    private final ExceptionReason reason;
    private final MissingValueField field;
    private final @Nullable String additionalInfo;
    private final Map<String, Object> additionalProperties;

    public MissingValueException(final MissingValueField field) {
        this(field, null, List.of());
    }

    public MissingValueException(
            final MissingValueField field,
            final @Nullable String additionalInfo,
            final List<Pair<String, Object>> additionalParams) {
        super(
                URI.create("/problem/missing-value"),
                "Missing value",
                HttpStatus.BAD_REQUEST,
                additionalInfo != null ? additionalInfo : "Missing value for field " + field);
        this.reason = ExceptionReason.MISSING_VALUE;
        this.field = field;
        this.additionalInfo = additionalInfo;

        final var properties = new LinkedHashMap<String, Object>();
        properties.put("reason", reason);
        properties.put("field", field);
        if (additionalInfo != null) {
            properties.put("additionalInfo", additionalInfo);
        }
        additionalParams.forEach(param -> properties.put(param.getKey(), param.getValue()));
        this.additionalProperties = Map.copyOf(properties);
    }

    @Override
    public Map<String, Object> additionalProperties() {
        return additionalProperties;
    }

    public ExceptionReason reason() {
        return reason;
    }

    public MissingValueField getField() {
        return field;
    }

    public @Nullable String getAdditionalMessage() {
        return additionalInfo;
    }
}
