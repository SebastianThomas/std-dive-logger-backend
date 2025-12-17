package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.user.FrontendUser;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

public record Dive(
        long id,
        FrontendUser user,
        int number,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable DiveSite site,
        @NotNull List<DiveProfile> profiles,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies) {
    @Override
    @NotNull
    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(number)
                .append(customIdentifier)
                .append(previewImage)
                .append(site)
                .toString();
    }
}
