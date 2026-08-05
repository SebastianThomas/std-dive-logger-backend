package ch.sthomas.stddivelogger.model.dive.gear;

import org.jspecify.annotations.Nullable;

public record DiveComputer(
        long id,
        DiveComputerManufacturer manufacturer,
        @Nullable String serialNumber,
        String customIdentifier,
        // Set when this computer/handset is permanently paired with a specific CCR unit - lets
        // importing a dive recorded on it infer the CCR unit (and, via the unit's own default
        // base configuration, the dive mode) automatically. See
        // DiveService#inferConfigurationFromComputer.
        @Nullable Long ccrUnitId) {}
