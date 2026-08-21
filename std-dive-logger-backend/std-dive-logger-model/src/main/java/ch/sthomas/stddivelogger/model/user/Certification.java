package ch.sthomas.stddivelogger.model.user;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record Certification(
        long id,
        long userId,
        CertificationAgency agency,
        String level,
        LocalDate certDate,
        @Nullable String certId,
        @Nullable String instructorName,
        @Nullable String facility,
        @Nullable String courseLink,
        @Nullable String certificationLink) {}
