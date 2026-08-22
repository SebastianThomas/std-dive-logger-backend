package ch.sthomas.stddivelogger.utils.advice;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.stream.Collectors;

/**
 * A {@code @Valid @RequestBody} controller argument that fails Jakarta Bean Validation (e.g.
 * {@code @NotBlank}, {@code @Positive} on a request DTO field) used to surface to the client as a
 * bare {@code "Invalid request content."} - {@link MethodArgumentNotValidException}'s own {@code
 * ProblemDetail} sets that fixed string as its {@code detail} in its constructor, and {@code
 * ErrorResponse#updateAndGetBody} only ever replaces it by resolving {@code getDetailMessageCode()}
 * against a {@code MessageSource} - a bundle entry this project doesn't define - so the field-level
 * errors captured in the exception's own {@code BindingResult} never reached the response, only the
 * server logs. Same shape of gap as {@link HttpMessageNotReadableAdviceTrait}, just one validation
 * path earlier (annotation-driven DTO validation instead of a record's compact constructor).
 *
 * <p>This can't be a plain {@code @ExceptionHandler} default method for the same reason described
 * on {@link HttpMessageNotReadableAdviceTrait}: {@code ResponseEntityExceptionHandler} already
 * claims {@code MethodArgumentNotValidException} on its own {@code final handleException(...)}
 * dispatch method, so the real extension point is the {@code protected
 * handleMethodArgumentNotValid(...)} method it delegates to - confirmed via {@code javap -p} on
 * {@code ResponseEntityExceptionHandler} - which only a genuine {@code @Override} in a concrete
 * subclass can hook into. Hence this interface only provides the message-extraction helper, called
 * from {@code ExceptionHandling}'s override.
 */
public interface MethodArgumentNotValidAdviceTrait {

    static String extractDetail(final MethodArgumentNotValidException exception) {
        final var fieldErrors = exception.getBindingResult().getFieldErrors();
        if (!fieldErrors.isEmpty()) {
            return fieldErrors.stream()
                    .map(MethodArgumentNotValidAdviceTrait::describe)
                    .collect(Collectors.joining("; "));
        }
        final var globalErrors = exception.getBindingResult().getGlobalErrors();
        if (!globalErrors.isEmpty()) {
            return globalErrors.stream()
                    .map(
                            error ->
                                    error.getDefaultMessage() != null
                                            ? error.getDefaultMessage()
                                            : "is invalid")
                    .collect(Collectors.joining("; "));
        }
        return "The request body failed validation - check that every field meets its"
                + " constraints.";
    }

    private static String describe(final FieldError error) {
        final var message =
                error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
        return error.getField() + ": " + message;
    }
}
