package ch.sthomas.stddivelogger.model.dive.home;

/** A named buddy and how many of the user's dives they appear on - frequency-ranked, top few. */
public record HomeBuddy(String name, long diveCount) {}
