package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A named "standard" cylinder = a canonical water-volume size + a material, for the size-picker
 * dropdown ("12 L Steel", "11.1 L Alu (AL80)"). Plain data on purpose so the frontend can mirror
 * this catalog 1:1 (see {@code src/lib/dive/cylinders.ts}) - keep the two in lockstep.
 *
 * <p>Everything is stored as {@link CylinderSizeUnit#LITER} water volume; US "cuft" ratings are
 * free-gas capacity, not water volume, and only ever appear in a label.
 */
public record StandardCylinder(
        String key, String label, CylinderSize size, CylinderMaterial material) {

    /** A tracked cylinder within this many litres of a catalog value snaps onto it. */
    public static final double SNAP_TOLERANCE_LITERS = 0.3;

    private static StandardCylinder liter(
            final String key,
            final String label,
            final double liters,
            final CylinderMaterial material) {
        return new StandardCylinder(
                key, label, new CylinderSize(CylinderSizeUnit.LITER, liters), material);
    }

    /** TWEAK IN REVIEW - drafted catalog, ~14 entries. */
    public static final List<StandardCylinder> CATALOG =
            List.of(
                    liter("steel-3", "3 L Steel (pony)", 3, CylinderMaterial.STEEL),
                    liter("steel-5", "5 L Steel", 5, CylinderMaterial.STEEL),
                    liter("steel-7", "7 L Steel", 7, CylinderMaterial.STEEL),
                    liter("steel-10", "10 L Steel", 10, CylinderMaterial.STEEL),
                    liter("steel-12", "12 L Steel", 12, CylinderMaterial.STEEL),
                    liter("steel-15", "15 L Steel", 15, CylinderMaterial.STEEL),
                    liter("steel-18", "18 L Steel", 18, CylinderMaterial.STEEL),
                    liter("steel-20", "20 L Steel", 20, CylinderMaterial.STEEL),
                    liter("steel-24", "24 L Steel (twin 12)", 24, CylinderMaterial.STEEL),
                    liter("steel-30", "30 L Steel (twin 15)", 30, CylinderMaterial.STEEL),
                    liter("alu-5.5", "5.5 L Alu (AL40 / ~40 cuft)", 5.5, CylinderMaterial.ALU),
                    liter("alu-7", "7 L Alu", 7, CylinderMaterial.ALU),
                    liter("alu-9", "9 L Alu (AL63 / ~63 cuft)", 9, CylinderMaterial.ALU),
                    liter("alu-11.1", "11.1 L Alu (AL80 / ~80 cuft)", 11.1, CylinderMaterial.ALU));

    /**
     * The nearest catalog entry within {@link #SNAP_TOLERANCE_LITERS}, if any. Ties (e.g. an exact
     * 7 L, which has both a Steel and an Alu entry) resolve to the entry whose material matches
     * {@link #inferMaterial} for that size, then to catalog order - so a "snap" never picks an
     * arbitrary material when the litre value alone is ambiguous.
     */
    public static Optional<StandardCylinder> snap(final CylinderSize size) {
        final var liters = size.liters();
        final var inferred = inferMaterial(liters, size.unit());
        return CATALOG.stream()
                .filter(c -> Math.abs(c.size().liters() - liters) <= SNAP_TOLERANCE_LITERS)
                .min(
                        Comparator.<StandardCylinder>comparingDouble(
                                        c -> Math.abs(c.size().liters() - liters))
                                .thenComparingInt(c -> c.material() == inferred ? 0 : 1));
    }

    /** The catalog entry for a key, if the key is a known standard. */
    public static Optional<StandardCylinder> byKey(@Nullable final String key) {
        return CATALOG.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    /**
     * Best-guess material for a size with none recorded (legacy rows, imports). TWEAK IN REVIEW:
     * {@code <= 3.5 L} steel (steel pony bottles); {@code 3.5 - 8.5 L} alu (5.5/7 alu, AL40);
     * {@code >= 8.5 L} steel (10/12/15/18/20); any {@code CUFT} size alu (US alu ratings). Exact 9
     * / 11.1 L alu are handled by snapping before inference runs.
     */
    public static CylinderMaterial inferMaterial(final double liters, final CylinderSizeUnit unit) {
        if (unit == CylinderSizeUnit.CUFT) {
            return CylinderMaterial.ALU;
        }
        if (liters <= 3.5) {
            return CylinderMaterial.STEEL;
        }
        if (liters < 8.5) {
            return CylinderMaterial.ALU;
        }
        return CylinderMaterial.STEEL;
    }
}
