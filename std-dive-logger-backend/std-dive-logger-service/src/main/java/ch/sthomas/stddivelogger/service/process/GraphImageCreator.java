package ch.sthomas.stddivelogger.service.process;

import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.graphs.LegendType;

import com.google.common.collect.Streams;

import jakarta.validation.constraints.NotNull;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class GraphImageCreator {
    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    public static void fromDive(
            final Dive dive,
            final Writer writer,
            final Map<
                            DiveMeasurement.DiveMeasurementProperty,
                            Pair<Function<DiveMeasurement, Double>, LegendType>>
                    extractorsAndLegendTypes)
            throws IOException {
        final var height = 100;
        final var width = 300;
        final var padding = 10;

        final var domImpl = GenericDOMImplementation.getDOMImplementation();
        final var document = domImpl.createDocument(SVG_NS, "svg", null);
        final var graphics = new SVGGraphics2D(document);
        final var start =
                dive.profiles().stream()
                        .map(DiveProfile::start)
                        .min(Instant::compareTo)
                        .orElseThrow()
                        .toEpochMilli();
        final var end =
                dive.profiles().stream()
                        .map(DiveProfile::end)
                        .max(Instant::compareTo)
                        .orElseThrow()
                        .toEpochMilli();
        final DoubleUnaryOperator widthCalculator =
                (a) -> width * (a - start) / (end - start) + padding;
        final var canvasSize =
                new Dimension(
                        (int) Math.ceil(widthCalculator.applyAsDouble(end) + 2 * padding),
                        height + 2 * padding);
        graphics.setSVGCanvasSize(canvasSize);
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, canvasSize.width, canvasSize.height);

        extractorsAndLegendTypes.forEach(
                (property, extractorAndType) -> {
                    final var color = getColor(property);
                    final var data = getDataRow(dive, extractorAndType.getLeft()).data().getFirst();
                    graphics.setColor(color);
                    final var lines = data.getLines(widthCalculator, height, padding);
                    lines.forEach(graphics::draw);
                    final var legendType = extractorAndType.getRight();
                    // TODO: Legend based on legend type
                    graphics.setFont(new Font("SansSerif", Font.BOLD, 8));
                    final var min = data.min();
                    final var max = data.max();
                    if (legendType == LegendType.NO_LEGEND) {
                        return;
                    }
                    IntStream.range(0, 5)
                            .mapToObj(i -> Pair.of(i, min + (max - min) * i))
                            .forEach(
                                    p ->
                                            graphics.drawString(
                                                    String.valueOf(p.getRight()),
                                                    (int) Math.ceil(legendType.getX() * height)
                                                            + padding,
                                                    p.getLeft()));
                });

        graphics.stream(writer, true);
    }

    private static Color getColor(final DiveMeasurement.DiveMeasurementProperty property) {
        return switch (property) {
            case TEMPERATURE -> new Color(63, 105, 212);
            case DEPTH -> new Color(200, 200, 200);
            case NDL -> new Color(232, 63, 63);
            case GAS_O2 -> new Color(81, 205, 203);
            case GAS_N2 -> new Color(234, 197, 92);
            case GAS_HE -> new Color(155, 112, 223);
        };
    }

    private static DataRow getDataRow(
            final Dive dive, final Function<DiveMeasurement, Double> extractor) {
        return new DataRow(
                dive.profiles().stream()
                        .map(profile -> getProfileRow(profile, extractor))
                        .toList());
    }

    private static ProfileRow getProfileRow(
            final DiveProfile profile, final Function<DiveMeasurement, Double> extractor) {
        final var start = profile.start();
        final var end = profile.end();
        final var measurements =
                profile.measurements().stream()
                        .map(m -> new MeasurementData(m.time(), extractor.apply(m)))
                        .toList();
        return new ProfileRow(start, end, measurements);
    }

    private record DataRow(@NotNull List<ProfileRow> data) {}

    private record ProfileRow(
            @NotNull Instant start, @NotNull Instant end, @NotNull List<MeasurementData> data) {
        public double min() {
            return data.stream().mapToDouble(MeasurementData::measurement).min().orElse(0.0);
        }

        public double max() {
            return data.stream().mapToDouble(MeasurementData::measurement).max().orElse(0.0);
        }

        public Stream<Line2D.Double> getLines(
                final DoubleUnaryOperator getXCoordinateFromTime,
                final int height,
                final double padding) {
            final var min = min();
            final var max = max();
            final DoubleUnaryOperator getHeight =
                    min == max
                            ? (_) -> height / 2.0
                            : (a) -> height * (a - min) / (max - min) + padding;

            return Streams.zip(data.stream(), data.stream().skip(1), Pair::of)
                    .filter(Objects::nonNull)
                    .map(
                            pair ->
                                    Pair.of(
                                            Objects.requireNonNull(pair.getLeft()),
                                            Objects.requireNonNull(pair.getRight())))
                    .map(
                            pair ->
                                    new Line2D.Double(
                                            getXCoordinateFromTime.applyAsDouble(
                                                    pair.getLeft().time().toEpochMilli()),
                                            getHeight.applyAsDouble(pair.getLeft().measurement()),
                                            getXCoordinateFromTime.applyAsDouble(
                                                    pair.getRight().time().toEpochMilli()),
                                            getHeight.applyAsDouble(
                                                    pair.getRight().measurement())));
        }
    }

    private record MeasurementData(@NotNull Instant time, @NotNull double measurement) {}
}
