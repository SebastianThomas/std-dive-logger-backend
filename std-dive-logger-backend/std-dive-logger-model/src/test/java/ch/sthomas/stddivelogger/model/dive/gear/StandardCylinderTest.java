package ch.sthomas.stddivelogger.model.dive.gear;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;

import org.junit.jupiter.api.Test;

class StandardCylinderTest {

    private static CylinderSize liters(final double v) {
        return new CylinderSize(CylinderSizeUnit.LITER, v);
    }

    @Test
    void catalogIsInternallyConsistent() {
        assertThat(StandardCylinder.CATALOG).isNotEmpty();
        assertThat(StandardCylinder.CATALOG)
                .extracting(StandardCylinder::key)
                .doesNotHaveDuplicates();
        assertThat(StandardCylinder.CATALOG)
                .allSatisfy(
                        c -> {
                            assertThat(c.label()).isNotBlank();
                            assertThat(c.size().unit()).isEqualTo(CylinderSizeUnit.LITER);
                            assertThat(c.size().value()).isPositive();
                            assertThat(StandardCylinder.byKey(c.key())).contains(c);
                        });
    }

    @Test
    void snapHitsTheNearestCatalogEntryWithinTolerance() {
        assertThat(StandardCylinder.snap(liters(11.9)).orElseThrow().key()).isEqualTo("steel-12");
        assertThat(StandardCylinder.snap(liters(5.6)).orElseThrow().key()).isEqualTo("alu-5.5");
        assertThat(StandardCylinder.snap(liters(15.0)).orElseThrow().material())
                .isEqualTo(CylinderMaterial.STEEL);
    }

    @Test
    void snapMissesWhenNoCatalogEntryIsCloseEnough() {
        assertThat(StandardCylinder.snap(liters(13.5))).isEmpty();
        assertThat(StandardCylinder.snap(liters(8.0))).isEmpty();
    }

    @Test
    void snapResolvesAnAmbiguousLitreValueViaInferMaterial() {
        // 7 L has both a Steel and an Alu entry; inferMaterial(7) is ALU.
        assertThat(StandardCylinder.snap(liters(7.0)).orElseThrow().material())
                .isEqualTo(CylinderMaterial.ALU);
    }

    @Test
    void inferMaterialFollowsTheBoundaryTable() {
        assertThat(StandardCylinder.inferMaterial(3.0, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.STEEL);
        assertThat(StandardCylinder.inferMaterial(3.5, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.STEEL);
        assertThat(StandardCylinder.inferMaterial(5.5, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.ALU);
        assertThat(StandardCylinder.inferMaterial(8.4, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.ALU);
        assertThat(StandardCylinder.inferMaterial(8.5, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.STEEL);
        assertThat(StandardCylinder.inferMaterial(12.0, CylinderSizeUnit.LITER))
                .isEqualTo(CylinderMaterial.STEEL);
        // Any CUFT size -> ALU regardless of magnitude.
        assertThat(StandardCylinder.inferMaterial(80.0, CylinderSizeUnit.CUFT))
                .isEqualTo(CylinderMaterial.ALU);
    }
}
