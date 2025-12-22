package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;

public record DiveConfigurationCylinder(
        long id, CylinderSize size, Double startBar, Double endBar, String notes) {}
