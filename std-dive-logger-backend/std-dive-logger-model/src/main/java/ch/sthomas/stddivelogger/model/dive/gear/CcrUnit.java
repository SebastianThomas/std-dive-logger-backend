package ch.sthomas.stddivelogger.model.dive.gear;

import jakarta.validation.constraints.NotNull;

/**
 * A user-owned rebreather unit (e.g. "rEvo", "Poseidon SE7EN", a custom nickname). Unlike
 * {@link Suit}, which every dive configuration always has one of, a dive's configuration only
 * ever references a CcrUnit when its {@link BaseConfiguration} is a CCR variant — for any other
 * configuration, no CcrUnit applies and none is required.
 */
public record CcrUnit(Long id, long userId, @NotNull String name, @NotNull String notes) {}
