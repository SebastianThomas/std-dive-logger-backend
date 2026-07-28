package ch.sthomas.stddivelogger.model.dive.gear;

import org.jspecify.annotations.Nullable;

public record DiveComputer(
        long id,
        DiveComputerManufacturer manufacturer,
        @Nullable String serialNumber,
        String customIdentifier) {}
