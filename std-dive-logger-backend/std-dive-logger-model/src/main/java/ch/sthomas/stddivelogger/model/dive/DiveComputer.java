package ch.sthomas.stddivelogger.model.dive;

public record DiveComputer(
        long id,
        DiveComputerManufacturer manufacturer,
        String serialNumber,
        String customIdentifier) {}
