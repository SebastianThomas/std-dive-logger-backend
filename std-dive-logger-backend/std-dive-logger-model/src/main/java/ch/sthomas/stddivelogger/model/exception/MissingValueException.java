package ch.sthomas.stddivelogger.model.exception;

import org.apache.commons.lang3.tuple.Pair;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MissingValueException extends AbstractThrowableProblem {
    private final ExceptionReason reason;
    private final MissingValueField field;
    private final String additionalInfo;

    public MissingValueException(final MissingValueField field) {
        this(field, null, List.of());
    }

    public MissingValueException(
            final MissingValueField field,
            final String additionalInfo,
            final List<Pair<String, Object>> additionalParams) {
        final var reason = ExceptionReason.MISSING_VALUE;
        final var map =
                Stream.concat(
                                Stream.<Map.Entry<String, Object>>of(
                                        Map.entry("reason", reason),
                                        Map.entry("additionalInfo", additionalInfo),
                                        Map.entry("field", field)),
                                additionalParams.stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        super(
                URI.create("/problem/missing-value"),
                "Missing value",
                Status.BAD_REQUEST,
                null,
                null,
                null,
                map);
        this.reason = reason;
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
