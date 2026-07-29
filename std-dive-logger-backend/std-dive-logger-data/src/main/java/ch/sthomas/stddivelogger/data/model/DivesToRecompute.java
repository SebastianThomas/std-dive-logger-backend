package ch.sthomas.stddivelogger.data.model;

import ch.sthomas.stddivelogger.model.dive.Dive;

import java.util.List;

public record DivesToRecompute(List<Dive> dives, boolean hasMore) {}
