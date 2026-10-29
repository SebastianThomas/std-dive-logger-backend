package ch.sthomas.stddivegastracker.calc.inventory;

import ch.sthomas.stddivegastracker.calc.gas.CompressibilityModel;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replays a vessel's book content in time order, ties by id (DOMAIN §6). The book keeps each
 * component honest, so an overdraw can drive it negative; a {@link GasMix} is built only from the
 * positive parts, and pressures clamp at 0 (§4.3).
 */
public final class InventoryReplay {

    // Same instant: inflows, then draws, then readings (a gauge read with a fill shows the state
    // after it); ids only break ties within one kind (they come from different tables).
    private static final Comparator<Event> REPLAY_ORDER =
            Comparator.comparing(Event::at)
                    .thenComparingInt(InventoryReplay::kindRank)
                    .thenComparingLong(Event::id);

    private InventoryReplay() {}

    /**
     * What the replay needs of a vessel. Pressures and water volume are checked by {@link
     * GasContent}; {@code closingPressureBar} is set only for a closed vessel. {@code initialMix}
     * is the gas at {@code initialPressureBar} when registered; the declared mix may have changed
     * since (an analysis after blending) and is used for the residual and closing content.
     */
    public record Vessel(
            double waterVolumeLitres,
            GasMix declaredMix,
            GasMix initialMix,
            double initialPressureBar,
            double expectedResidualPressureBar,
            @Nullable Double closingPressureBar) {

        /** A vessel that still holds the gas it was registered with (nothing blended in). */
        public Vessel(
                final double waterVolumeLitres,
                final GasMix declaredMix,
                final double initialPressureBar,
                final double expectedResidualPressureBar,
                final @Nullable Double closingPressureBar) {
            this(
                    waterVolumeLitres,
                    declaredMix,
                    declaredMix,
                    initialPressureBar,
                    expectedResidualPressureBar,
                    closingPressureBar);
        }
    }

    public sealed interface Event {
        long id();

        Instant at();
    }

    /** One fill into the vessel: Σ added litres of its stages; N2 is {@code total - o2 - he}. */
    public record Inflow(long id, Instant at, double o2Litres, double heLitres, double totalLitres)
            implements Event {

        public Inflow {
            requireLitres(o2Litres, "O2 litres");
            requireLitres(heLitres, "He litres");
            requireLitres(totalLitres, "Total litres");
        }
    }

    /**
     * One draw: the stage's draw litres incl. line loss, composed like the stage's snapshotted
     * source mix (DOMAIN §5.1, §5.4).
     */
    public record Outflow(long id, Instant at, double drawLitres, double lineLossLitres, GasMix mix)
            implements Event {

        public Outflow {
            requireLitres(drawLitres, "Draw litres");
            requireLitres(lineLossLitres, "Line loss litres");
        }
    }

    /** A gauge reading of the vessel. */
    public record Reading(long id, Instant at, double pressureBar) implements Event {

        public Reading {
            if (!Double.isFinite(pressureBar) || pressureBar < 0) {
                throw new IllegalArgumentException(
                        "Reading pressure must be a number of at least 0 bar");
            }
        }
    }

    /**
     * Book state at a reading; {@code discrepancyLitres = book - measured} (positive = missing).
     */
    public record ReadingResult(
            long readingId,
            double bookContentLitres,
            double bookPressureBar,
            double discrepancyLitres) {}

    /** DOMAIN §6 loss report of a closed vessel; the residual is at the closing pressure. */
    public record LossReport(
            double inflowLitres,
            double drawnLitres,
            double lineLossLitres,
            double residualLitres,
            double unaccountedLitres,
            double lossFraction) {}

    /**
     * {@code inflowLitres} is V_in and {@code drawnLitres} D (DOMAIN §7). The estimate is the last
     * reading's measured content plus the events after it (the book when there is no reading);
     * {@code residualLitres} is V_res, the content at the expected residual pressure.
     */
    public record Result(
            double bookContentLitres,
            double bookO2Litres,
            double bookHeLitres,
            double bookN2Litres,
            GasMix bookMix,
            double bookPressureBar,
            double inflowLitres,
            double drawnLitres,
            double lineLossLitres,
            List<ReadingResult> readings,
            double estimatedContentLitres,
            double estimatedPressureBar,
            double usableContentLitres,
            double residualLitres,
            @Nullable LossReport lossReport) {

        public Result {
            readings = List.copyOf(readings);
        }
    }

    public static Result replay(
            final Vessel vessel,
            final List<? extends Event> events,
            final CompressibilityModel model) {
        final double waterVolumeLitres = vessel.waterVolumeLitres();
        final var declaredMix = vessel.declaredMix();
        final var initialMix = vessel.initialMix();
        final double openLitres =
                GasContent.contentLitres(
                        waterVolumeLitres, vessel.initialPressureBar(), initialMix, model);
        double o2Litres = openLitres * initialMix.o2();
        double heLitres = openLitres * initialMix.he();
        double n2Litres = openLitres * initialMix.n2();
        double inflowLitres = openLitres;
        double drawnLitres = 0;
        double lineLossLitres = 0;
        double lastDiscrepancyLitres = 0;
        final var readings = new ArrayList<ReadingResult>();

        final var ordered = new ArrayList<Event>(events);
        ordered.sort(REPLAY_ORDER);
        for (final var event : ordered) {
            switch (event) {
                case Inflow(_, _, final var o2, final var he, final var total) -> {
                    o2Litres += o2;
                    heLitres += he;
                    n2Litres += total - o2 - he;
                    inflowLitres += total;
                }
                case Outflow(_, _, final var draw, final var lineLoss, final var source) -> {
                    o2Litres -= draw * source.o2();
                    heLitres -= draw * source.he();
                    n2Litres -= draw * source.n2();
                    drawnLitres += draw;
                    lineLossLitres += lineLoss;
                }
                case Reading(final var id, _, final var pressureBar) -> {
                    final double bookLitres = o2Litres + heLitres + n2Litres;
                    final var mix = mixOf(o2Litres, heLitres, n2Litres, declaredMix);
                    final double measuredLitres =
                            GasContent.contentLitres(waterVolumeLitres, pressureBar, mix, model);
                    lastDiscrepancyLitres = bookLitres - measuredLitres;
                    readings.add(
                            new ReadingResult(
                                    id,
                                    bookLitres,
                                    pressureCapped(waterVolumeLitres, bookLitres, mix, model),
                                    lastDiscrepancyLitres));
                }
            }
        }

        final double bookLitres = o2Litres + heLitres + n2Litres;
        final var bookMix = mixOf(o2Litres, heLitres, n2Litres, declaredMix);
        // measured = book - discrepancy at the last reading, and later events move both equally
        final double estimatedLitres = bookLitres - lastDiscrepancyLitres;
        final double residualLitres =
                GasContent.contentLitres(
                        waterVolumeLitres,
                        vessel.expectedResidualPressureBar(),
                        declaredMix,
                        model);

        final var closingPressureBar = vessel.closingPressureBar();
        final var lossReport =
                closingPressureBar == null
                        ? null
                        : lossReportOf(
                                inflowLitres,
                                drawnLitres,
                                lineLossLitres,
                                GasContent.contentLitres(
                                        waterVolumeLitres, closingPressureBar, bookMix, model));

        return new Result(
                bookLitres,
                o2Litres,
                heLitres,
                n2Litres,
                bookMix,
                pressureCapped(waterVolumeLitres, bookLitres, bookMix, model),
                inflowLitres,
                drawnLitres,
                lineLossLitres,
                readings,
                estimatedLitres,
                pressureCapped(waterVolumeLitres, estimatedLitres, bookMix, model),
                Math.max(0.0, estimatedLitres - residualLitres),
                residualLitres,
                lossReport);
    }

    /**
     * Pressure for a book/estimated content; implausible data (more gas than fits at {@link
     * GasContent#MAX_PRESSURE_BAR}) shows as that cap instead of failing the team's whole inventory
     * (DOMAIN §6).
     */
    private static double pressureCapped(
            final double waterVolumeLitres,
            final double contentLitres,
            final GasMix mix,
            final CompressibilityModel model) {
        final var maxLitres =
                GasContent.contentLitres(
                        waterVolumeLitres, GasContent.MAX_PRESSURE_BAR, mix, model);
        return contentLitres > maxLitres
                ? GasContent.MAX_PRESSURE_BAR
                : GasContent.pressureFor(waterVolumeLitres, contentLitres, mix, model);
    }

    private static LossReport lossReportOf(
            final double inflowLitres,
            final double drawnLitres,
            final double lineLossLitres,
            final double closingLitres) {
        return new LossReport(
                inflowLitres,
                drawnLitres,
                lineLossLitres,
                closingLitres,
                inflowLitres - drawnLitres - closingLitres,
                inflowLitres == 0 ? 0 : (inflowLitres - drawnLitres) / inflowLitres);
    }

    /** Mix of the positive component parts; the declared mix when there is no content. */
    private static GasMix mixOf(
            final double o2Litres,
            final double heLitres,
            final double n2Litres,
            final GasMix declaredMix) {
        if (o2Litres + heLitres + n2Litres <= 0) {
            return declaredMix;
        }
        final double o2Part = Math.max(0.0, o2Litres);
        final double hePart = Math.max(0.0, heLitres);
        final double partsLitres = o2Part + hePart + Math.max(0.0, n2Litres);
        return new GasMix(o2Part / partsLitres, hePart / partsLitres);
    }

    private static void requireLitres(final double litres, final String name) {
        if (!Double.isFinite(litres) || litres < 0) {
            throw new IllegalArgumentException(name + " must be a number of at least 0");
        }
    }

    private static int kindRank(final Event event) {
        return switch (event) {
            case Inflow _ -> 0;
            case Outflow _ -> 1;
            case Reading _ -> 2;
        };
    }
}
