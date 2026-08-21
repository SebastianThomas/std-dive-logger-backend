package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

public record DiveSiteLink(long id, String url, @Nullable String label) {}
