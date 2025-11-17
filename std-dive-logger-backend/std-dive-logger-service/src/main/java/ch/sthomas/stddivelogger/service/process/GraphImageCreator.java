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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(GraphImageCreator.class);

    private static final int PADDING = 20;
    private static final int FONT_SIZE = 6;
    private static final double FONT_ASCENT_MULTIPLIER = 1.0 / 3;
    private static final int TEXT_PADDING = FONT_SIZE - 1;

    public static void fromDive(
            final Dive dive,
            final Writer writer,
            final Map<
                            DiveMeasurement.DiveMeasurementProperty,
                            Pair<Function<DiveMeasurement, Double>, LegendType>>
                    extractorsAndLegendTypes,
            final Dimension widthHeight)
            throws IOException {
        final var width = (int) widthHeight.getWidth();
        final var height = (int) widthHeight.getHeight();

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
                (a) -> width * (a - start) / (end - start) + PADDING;
        final var canvasSize =
                new Dimension(
                        (int) Math.ceil(widthCalculator.applyAsDouble(end) + PADDING),
                        height + 2 * PADDING);
        graphics.setSVGCanvasSize(canvasSize);
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, canvasSize.width, canvasSize.height);

        extractorsAndLegendTypes.forEach(
                (property, extractorAndType) -> {
                    graphics.setFont(new Font("SansSerif", Font.BOLD, FONT_SIZE));
                    final var color = getColor(property);
                    final var data = getDataRow(dive, extractorAndType.getLeft()).data().getFirst();
                    graphics.setColor(color);
                    final var lines = data.getLines(widthCalculator, height, PADDING);
                    lines.forEach(graphics::draw);
                    final var legendType = extractorAndType.getRight();
                    if (legendType == LegendType.NO_LEGEND) {
                        return;
                    }
                    final var min = data.min();
                    final var max = data.max();
                    logger.info("MinMax for {}: ({}, {})", property, min, max);
                    if (min == max) {
                        drawSingleLegend(graphics, legendType, min, canvasSize.width, height);
                    } else {
                        drawLegend(graphics, legendType, 5, min, max, canvasSize.width, height);
                    }
                });

        graphics.stream(writer, true);
    }

    private static void drawSingleLegend(
            final SVGGraphics2D graphics,
            final LegendType legendType,
            final double val,
            final int width,
            final int height) {
        final var fontMetrics = graphics.getFontMetrics();
        final var string = String.valueOf(val);
        graphics.drawString(
                string,
                (int)
                        Math.ceil(
                                legendType.getX(
                                        width,
                                        fontMetrics.stringWidth(string),
                                        PADDING,
                                        TEXT_PADDING)),
                height / 2 + (int) (fontMetrics.getAscent() * FONT_ASCENT_MULTIPLIER));
    }

    private static void drawLegend(
            final SVGGraphics2D graphics,
            final LegendType legendType,
            final int nrOfEntries,
            final double min,
            final double max,
            final int width,
            final int height) {
        IntStream.rangeClosed(0, nrOfEntries)
                .mapToObj(i -> Pair.of(i, min + (max - min) * i / nrOfEntries))
                .forEach(
                        p -> {
                            final var string = String.valueOf(p.getRight());
                            final var fontMetrics = graphics.getFontMetrics();
                            final var y =
                                    (int)
                                            ((double) p.getLeft() / nrOfEntries * height
                                                    + fontMetrics.getAscent()
                                                            * FONT_ASCENT_MULTIPLIER
                                                    + PADDING);
                            final var x =
                                    legendType.getX(
                                            width,
                                            fontMetrics.stringWidth(string),
                                            PADDING,
                                            TEXT_PADDING);
                            graphics.drawString(string, (int) x, y);
                        });
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
