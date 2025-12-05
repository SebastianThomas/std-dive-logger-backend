package ch.sthomas.stddivelogger.model.user;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Group(long id, @NotNull String name) {}
