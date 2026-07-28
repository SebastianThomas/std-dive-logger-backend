package ch.sthomas.stddivelogger.model.user;

import org.jspecify.annotations.Nullable;

public record FrontendUser(
        long id,
        String name,
        @Nullable String customIconUrl,
        @Nullable String customBackgroundUrl) {}
