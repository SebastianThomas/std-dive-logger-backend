package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

public record NamedBuddy(long id, String name, @Nullable BuddyRole role) {}
