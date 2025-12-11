package ch.sthomas.stddivelogger.model.controller;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

public record UpdateDiveBody(
        long id,
        int number,
        @Nullable String customIdentifier,
        @Nullable Long siteId,
        @Nullable List<String> namedBuddies) {
    @Override
    @NotNull
    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(number)
                .append(customIdentifier)
                .toString();
    }
}
