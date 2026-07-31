package ch.sthomas.stddivelogger.model.controller.dive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDive;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse.DivesoftDiveAndMixes;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Confirms that malformed Divesoft import requests (e.g. {@code {"dives":[{}]}}) are rejected by
 * bean validation at the controller boundary - which the existing {@code
 * ConstraintViolationAdviceTrait}/{@code ResponseEntityExceptionHandler} map to a 400 - instead of
 * reaching {@code DivesoftReaderService.parse}, where dereferencing the missing nested fields
 * would otherwise NPE unguarded.
 */
class DivesoftImportRequestTest {
    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void diveWithoutDiveAndMixesFailsValidationInsteadOfNpeingLaterInTheReader() {
        final var request =
                new DivesoftImportRequest(List.of(new DivesoftDiveDetailResponse(null)));

        final var violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "expected a constraint violation for the missing dive");
    }

    @Test
    void diveAndMixesWithoutDiveFailsValidation() {
        final var request =
                new DivesoftImportRequest(
                        List.of(new DivesoftDiveDetailResponse(new DivesoftDiveAndMixes(null))));

        final var violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "expected a constraint violation for the missing dive");
    }

    @Test
    void emptyDivesListFailsValidation() {
        final var request = new DivesoftImportRequest(List.of());

        final var violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "expected a constraint violation for the empty list");
    }

    @Test
    void wellFormedRequestPassesValidation() {
        final var dive =
                new DivesoftDive(
                        "dive-1", null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null);
        final var request =
                new DivesoftImportRequest(
                        List.of(
                                new DivesoftDiveDetailResponse(
                                        new DivesoftDiveAndMixes(dive))));

        final var violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "did not expect violations: " + violations);
    }
}
