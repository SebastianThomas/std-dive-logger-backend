package ch.sthomas.stddivegastracker.model.team;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;

/** API.md §1 {@code Team}: the settings (DOMAIN §2) plus the caller's own role and member id. */
public record Team(
        long teamId,
        String name,
        Role role,
        long memberId,
        String currency,
        LossPolicy defaultLossPolicy,
        double lineDeadVolumeLitres,
        double defaultResidualPressureBar) {}
