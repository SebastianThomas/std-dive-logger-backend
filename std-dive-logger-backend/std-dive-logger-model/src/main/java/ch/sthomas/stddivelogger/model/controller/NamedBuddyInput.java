package ch.sthomas.stddivelogger.model.controller;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

public record NamedBuddyInput(@NotBlank String name, @Nullable BuddyRole role) {}
