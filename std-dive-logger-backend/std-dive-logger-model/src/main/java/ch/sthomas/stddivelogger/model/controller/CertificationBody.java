package ch.sthomas.stddivelogger.model.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record CertificationBody(
        @Positive long agencyId,
        @NotBlank String level,
        @NotNull LocalDate certDate,
        @Nullable String certId,
        @Nullable String instructorName,
        @Nullable String facility,
        @Nullable String courseLink,
        @Nullable String certificationLink) {}
