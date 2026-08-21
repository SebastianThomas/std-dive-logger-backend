package ch.sthomas.stddivelogger.model.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Deliberately requires more than just a name to add a new certifying agency - a bare name is cheap
 * to fill in for a troll/duplicate entry, but a plausible full name and a real-looking website URL
 * raise the bar meaningfully while still being trivial for a genuine, currently- missing agency.
 * See {@code CertificationDataService.createAgency}'s own doc comment.
 */
public record CreateCertificationAgencyBody(
        @NotBlank @Size(min = 2, max = 32) String name,
        @NotBlank @Size(min = 4, max = 128) String fullName,
        @NotBlank @Pattern(regexp = "^https?://.+\\..+") String websiteUrl,
        @Nullable @Size(max = 500) String description) {}
