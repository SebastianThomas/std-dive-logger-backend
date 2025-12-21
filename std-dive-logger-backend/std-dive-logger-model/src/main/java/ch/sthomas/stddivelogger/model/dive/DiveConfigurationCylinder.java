package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.measurement.CylinderSize;

public record DiveConfigurationCylinder(
        long id, CylinderSize size, Double startBar, Double endBar, String notes) {}
