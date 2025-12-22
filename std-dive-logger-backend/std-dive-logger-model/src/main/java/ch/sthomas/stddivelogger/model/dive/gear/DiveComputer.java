package ch.sthomas.stddivelogger.model.dive.gear;

public record DiveComputer(
        long id,
        DiveComputerManufacturer manufacturer,
        String serialNumber,
        String customIdentifier) {}
