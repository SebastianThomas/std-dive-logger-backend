package ch.sthomas.stddivegastracker.model.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code PUT /v1/me}. Bean Validation runs on the raw value; the service trims before saving. */
public record UpdateMeRequest(@NotBlank @Size(max = 60) String displayName) {}
