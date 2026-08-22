package ch.sthomas.stddivelogger.utils.advice;

import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * A request body that fails to deserialize (malformed JSON, a field of the wrong type, or - the
 * case this exists for - a record's compact constructor rejecting the value, e.g. {@code Gas}'s
 * "Gas must consist of 100%") used to surface to the client as a bare, generic message from {@code
 * ResponseEntityExceptionHandler}'s own default handling, with the actual reason only ever visible
 * in the server logs.
 *
 * <p>Jackson's own wrapping exception - and Spring's {@link HttpMessageNotReadableException}
 * wrapping that in turn - both preserve the failing constructor's message verbatim behind a literal
 * {@code "problem: "} marker (see {@code GasJsonDeserializationTest} for exactly what this looks
 * like). Extracting that gives every record's compact-constructor validation across the whole app -
 * not just {@code Gas} - a real, specific message on the client, instead of only in the logs.
 *
 * <p>This can't be a plain {@code @ExceptionHandler} default method like the other advice traits in
 * this package: {@code ResponseEntityExceptionHandler} (which {@code ExceptionHandling} extends)
 * already claims {@code HttpMessageNotReadableException} on its own {@code final
 * handleException(...)} method, so a second {@code @ExceptionHandler} mapped to the same type is
 * flatly rejected at startup as ambiguous. The actual extension point is the {@code protected
 * handleHttpMessageNotReadable(...)} method that {@code handleException} delegates to internally -
 * only a real {@code @Override} in a concrete subclass can hook into that, hence this interface
 * only provides the message-extraction helper, called from {@code ExceptionHandling}'s override.
 */
public interface HttpMessageNotReadableAdviceTrait {

    String PROBLEM_MARKER = "problem: ";

    static String extractDetail(final HttpMessageNotReadableException exception) {
        final var message = exception.getMessage();
        final var markerIndex = message == null ? -1 : message.indexOf(PROBLEM_MARKER);
        if (message == null || markerIndex < 0) {
            return "The request body could not be read - check that every field is present and"
                    + " correctly formatted.";
        }
        // Jackson appends a "\n at [Source: ...]" location suffix after the original message -
        // strip it so only the actual validation message reaches the client.
        return message.substring(markerIndex + PROBLEM_MARKER.length()).split("\n", 2)[0].trim();
    }
}
