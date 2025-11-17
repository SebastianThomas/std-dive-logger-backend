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
import org.w3c.dom.Element;

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
        final var padding = 20;
        final var fontSize = 6;

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
                    graphics.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                    final var color = getColor(property);
                    final var data = getDataRow(dive, extractorAndType.getLeft()).data().getFirst();
                    graphics.setColor(color);
                    final var lines = data.getLines(widthCalculator, height, padding);
                    lines.forEach(graphics::draw);
                    final var legendType = extractorAndType.getRight();
                    // TODO: Legend based on legend type
                    if (legendType == LegendType.NO_LEGEND) {
                        return;
                    }
                    final var min = data.min();
                    final var max = data.max();
                    logger.info("MinMax for {}: ({}, {})", property, min, max);
                    if (min == max) {
                        // drawSingleLegend(graphics, legendType, min, width, height, padding);
                    } else {
                        final var legendElements =
                                getLegendElements(graphics, legendType, 5, min, max, height);
                        final var legendGroup = document.createElement("g");
                        legendElements.forEach(legendGroup::appendChild);
                        document.getFirstChild().appendChild(legendGroup);
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
        graphics.drawString(
                String.valueOf(val), (int) Math.ceil(legendType.getX(width)), height / 2);
    }

    private static Stream<Element> getLegendElements(
            final SVGGraphics2D graphics,
            final LegendType legendType,
            final int nrOfEntries,
            final double min,
            final double max,
            final int height) {
        return IntStream.rangeClosed(0, nrOfEntries)
                .mapToObj(i -> Pair.of(i, min + (max - min) * i / nrOfEntries))
                .map(
                        p ->
                                createAlignedText(
                                        graphics,
                                        String.valueOf(p.getRight()),
                                        p.getLeft(),
                                        height,
                                        legendType));
    }

    public static Element createAlignedText(
            final SVGGraphics2D svg,
            final String text,
            final double y,
            final double boxHeight,
            final LegendType legendType) {
        final var textPadding = 5;
        final var textEl =
                svg.getDOMFactory().createElementNS("http://www.w3.org/2000/svg", "text");

        switch (legendType) {
            case NO_LEGEND -> throw new IllegalArgumentException("legendType cannot be NO_LEGEND");
            case LEFT -> {
                textEl.setAttribute("text-anchor", "start");
            }
            case RIGHT -> {
                textEl.setAttribute("text-anchor", "end");
            }
        }
        textEl.setAttribute("x", Double.toString(textPadding));

        final var fontMetrics = svg.getFontMetrics();
        final var ascent = fontMetrics.getAscent();
        final var descent = fontMetrics.getDescent();

        final var baselineY = y + (boxHeight / 2.0) + (ascent - (ascent + descent) / 2.0);

        textEl.setAttribute("y", Double.toString(baselineY));

        textEl.setAttribute("font-family", svg.getFont().getFamily());
        textEl.setAttribute("font-size", Integer.toString(svg.getFont().getSize()));
        textEl.setAttribute("fill", svg.getColor().toString());

        textEl.setTextContent(text);

        return textEl;
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
