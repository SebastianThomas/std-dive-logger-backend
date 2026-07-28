package ch.sthomas.stddivelogger.model.user;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupWithMembers(
        long id,
        @NotNull String name,
        @Nullable Collection<FrontendUser> admins,
        @Nullable Collection<FrontendUser> members) {}
