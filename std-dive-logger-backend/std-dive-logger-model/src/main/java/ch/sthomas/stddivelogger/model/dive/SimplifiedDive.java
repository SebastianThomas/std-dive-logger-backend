package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.user.FrontendUser;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SimplifiedDive(
        long id,
        FrontendUser user,
        int number,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable Visibility visibility,
        @Nullable DiveSite site,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies) {}
