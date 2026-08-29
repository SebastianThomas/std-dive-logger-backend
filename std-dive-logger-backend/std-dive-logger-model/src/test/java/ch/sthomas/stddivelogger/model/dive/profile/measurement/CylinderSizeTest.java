package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CylinderSizeTest {

    @Test
    void literSizeIsItsOwnWaterVolume() {
        assertThat(new CylinderSize(CylinderSizeUnit.LITER, 12.0).liters()).isEqualTo(12.0);
    }

    @Test
    void cuftRatingConvertsToARealisticWaterVolume() {
        // AL80 (~77.4 cuft free gas @ 3000 psi) has ~11 L water volume, not ~2200 L.
        assertThat(new CylinderSize(CylinderSizeUnit.CUFT, 77.4).liters()).isBetween(10.0, 11.5);
        // AL40 (~40 cuft) ~5.5 L.
        assertThat(new CylinderSize(CylinderSizeUnit.CUFT, 40.0).liters()).isBetween(5.0, 6.0);
        // A 30 cuft pony ~4 L, never ~850 L.
        assertThat(new CylinderSize(CylinderSizeUnit.CUFT, 30.0).liters()).isLessThan(5.0);
    }
}
