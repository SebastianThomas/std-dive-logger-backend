package ch.sthomas.stddivelogger.model.user;

import org.jspecify.annotations.Nullable;

public record CertificationAgency(
        long id,
        String name,
        @Nullable String fullName,
        @Nullable String websiteUrl,
        @Nullable String description) {}
