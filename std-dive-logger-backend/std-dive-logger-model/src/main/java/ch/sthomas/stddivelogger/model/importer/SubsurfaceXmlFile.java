package ch.sthomas.stddivelogger.model.importer;

import static java.time.Duration.ofHours;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.*;
import ch.sthomas.stddivelogger.model.geometry.Location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Streams;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubsurfaceXmlFile(
        @JacksonXmlProperty(isAttribute = true) String program,
        @JacksonXmlProperty(isAttribute = true) Map<String, Object> settings,
        @JacksonXmlProperty(isAttribute = true) String version,
        @JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("divesites")
                List<SubsurfaceDiveSite> diveSites,
        @JacksonXmlElementWrapper(useWrapping = true) @JsonProperty("dives")
                List<SubsurfaceDive> dives) {

    static Duration parseDuration(final String duration) {
        final var hours = Integer.parseInt(duration.split(":")[0]);
        final var minutes = Integer.parseInt(duration.split(":")[1].split(" ")[0]);
        return ofHours(hours).plusMinutes(minutes);
    }

    public static double parsePercent(final @Nullable String percent) {
        if (percent == null) {
            return 0;
        }
        return Double.parseDouble(percent.replace("%", "").trim());
    }

    public static @Nullable CylinderSize parseCylinderSize(final @Nullable String cylinderSize) {
        if (cylinderSize == null) {
            return null;
        }
        final var split = cylinderSize.split(" ");
        final var value = Double.parseDouble(split[0]);
        if ("ℓ".equals(split[1]) || "l".equals(split[1])) {
            return new CylinderSize(CylinderSizeUnit.LITER, value);
        }
        return new CylinderSize(CylinderSizeUnit.CUFT, value);
    }

    public static @Nullable GasContent parseGasContent(final @Nullable String gasContent) {
        if (gasContent == null) {
            return null;
        }
        final var split = gasContent.split(" ");
        final var value = Double.parseDouble(split[0]);
        if ("bar".equalsIgnoreCase(split[1])) {
            return new GasContent(GasContentUnit.BAR, value);
        }
        if ("psi".equalsIgnoreCase(split[1])) {
            return new GasContent(GasContentUnit.PSI, value);
        }
        return null;
    }

    public static double parseUntilSpace(final String s) {
        return Double.parseDouble(getUntilSeparator(s, ' '));
    }

    public static String getUntilSeparator(final String s, final char sep) {
        final var separator = s.indexOf(sep);
        if (separator <= 0) {
            return s;
        }
        return s.substring(0, separator);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceDiveSite(
            @JacksonXmlProperty(isAttribute = true) String uuid,
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) String gps,
            @JacksonXmlProperty(isAttribute = true) String site) {
        public Location location() {
            final var splits = gps.split(" ");
            if (splits.length >= 2) {
                return new Location(Double.parseDouble(splits[0]), Double.parseDouble(splits[1]));
            }
            final var commaSplits = splits[0].split(",");
            if (commaSplits.length < 2) {
                throw new IllegalArgumentException("Invalid subsurface gps: " + gps);
            }
            return new Location(
                    Double.parseDouble(commaSplits[0]), Double.parseDouble(commaSplits[1]));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceDive(
            @JacksonXmlProperty(isAttribute = true, localName = "number") String number,
            @JacksonXmlElementWrapper(useWrapping = false) List<String> buddy,
            @JacksonXmlElementWrapper(useWrapping = false) List<String> divemaster,
            @JacksonXmlProperty(isAttribute = true) @Nullable String sac,
            @JacksonXmlProperty(isAttribute = true) String date,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(isAttribute = true) String duration,
            @JacksonXmlProperty(isAttribute = true) String rating,
            @JacksonXmlProperty(isAttribute = true) String divesiteid,
            @JacksonXmlProperty(isAttribute = true) String visibility,
            @JacksonXmlProperty(isAttribute = true) String current,
            @JacksonXmlProperty String notes,
            @JacksonXmlProperty String suit,
            // otu/cns are XML attributes on <dive> (e.g. otu='12' cns='11%'), not child elements -
            // isAttribute was previously missing here, so these never actually parsed (silently
            // stayed null via Jackson's default-missing-value behavior rather than failing loudly).
            @JacksonXmlProperty(isAttribute = true) @Nullable String otu,
            @JacksonXmlProperty(isAttribute = true) @Nullable String cns,
            @JacksonXmlProperty SubsurfaceTemperature divetemperature,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "weightsystem")
                    List<SubsurfaceWeightSystem> weightSystems,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "cylinder")
                    List<SubsurfaceCylinder> cylinders,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "divecomputer")
                    List<SubsurfaceDiveComputer> diveComputers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceCylinder(
            @NotNull @JacksonXmlProperty(isAttribute = true) String description,
            @JacksonXmlProperty(isAttribute = true) @Nullable String o2,
            @JacksonXmlProperty(isAttribute = true) @Nullable String he,
            @JacksonXmlProperty(isAttribute = true) @Nullable String size,
            @JacksonXmlProperty(isAttribute = true) @Nullable String workpressure,
            @JacksonXmlProperty(isAttribute = true) @Nullable String start,
            @JacksonXmlProperty(isAttribute = true) @Nullable String end,
            @JacksonXmlProperty(isAttribute = true) @Nullable String depth) {
        public Gas toGas() {
            // parsePercent returns the raw percent number (e.g. "32%" -> 32.0), but Gas's
            // fields are 0-1 fractions - dividing by 100 was missing here (unlike the
            // equivalent DiveConfigurationCylinder construction in SubsurfaceXmlReaderService),
            // which fed e.g. 32.0 straight in as o2, driving n2 wildly negative for any
            // gas-switch event referencing this cylinder.
            return new Gas(
                    parsePercent(o2) / 100,
                    parsePercent(he) / 100,
                    parseCylinderSize(size),
                    parseGasContent(start),
                    description);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceDiveComputer(
            @JacksonXmlProperty(isAttribute = true) String model,
            @JacksonXmlProperty(isAttribute = true) String deviceid,
            @JacksonXmlProperty(isAttribute = true) String diveid,
            @JacksonXmlProperty(isAttribute = true) String date,
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(isAttribute = true) String duration,
            @JacksonXmlProperty(isAttribute = true) @Nullable String dctype,
            @JacksonXmlProperty SubsurfaceDepth depth,
            @JacksonXmlProperty SubsurfaceTemperature temperature,
            @JacksonXmlProperty SubsurfaceSurface surface,
            @JacksonXmlProperty SubsurfaceWater water,
            @JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("extradata")
                    List<SubsurfaceExtraData> extraData,
            @JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("event")
                    List<SubsurfaceEvent> events,
            @JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("sample")
                    List<SubsurfaceSample> samples) {
        public static final String timeZone = "Z";

        public Instant start() {
            return Instant.parse(date + "T" + time + timeZone).plus(ofHours(1));
        }

        public Duration toDuration() {
            return parseDuration(duration);
        }

        public Instant end() {
            return start().plus(toDuration());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceDepth(
            @JacksonXmlProperty(isAttribute = true) String max,
            @JacksonXmlProperty(isAttribute = true) String mean) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceTemperature(@JacksonXmlProperty(isAttribute = true) String water) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceSurface(@JacksonXmlProperty(isAttribute = true) String pressure) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceWater(@JacksonXmlProperty(isAttribute = true) String salinity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceExtraData(
            @JacksonXmlProperty(isAttribute = true) String key,
            @JacksonXmlProperty(isAttribute = true) String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceEvent(
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(isAttribute = true) String type,
            @JacksonXmlProperty(isAttribute = true) @Nullable String flags,
            @JacksonXmlProperty(isAttribute = true) String name,
            @JacksonXmlProperty(isAttribute = true) @Nullable String o2,
            @JacksonXmlProperty(isAttribute = true) @Nullable String he,
            @JacksonXmlProperty(isAttribute = true) @Nullable String value,
            @JacksonXmlProperty(isAttribute = true) @Nullable String cylinder) {
        public Duration timeToDuration() {
            return parseDuration(time);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceWeightSystem(
            @JacksonXmlProperty(isAttribute = true) String weight,
            @JacksonXmlProperty(isAttribute = true) String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubsurfaceSample(
            @JacksonXmlProperty(isAttribute = true) String time,
            @JacksonXmlProperty(isAttribute = true) String depth,
            @JacksonXmlProperty(isAttribute = true) @Nullable String temp,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure0,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure1,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure2,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure3,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure4,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure5,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure6,
            @JacksonXmlProperty(isAttribute = true) @Nullable String pressure7,
            @JacksonXmlProperty(isAttribute = true) @Nullable String ndl,
            @JacksonXmlProperty(isAttribute = true) @Nullable String tts,
            @JacksonXmlProperty(isAttribute = true) @Nullable String rbt,
            @JacksonXmlProperty(isAttribute = true, localName = "in_deco") @Nullable String inDeco,
            @JacksonXmlProperty(isAttribute = true) @Nullable String stoptime,
            @JacksonXmlProperty(isAttribute = true) @Nullable String stopdepth
            // @JacksonXmlElementWrapper(useWrapping = false) @Nullable List<String> stoptime,
            // @JacksonXmlElementWrapper(useWrapping = false) @Nullable List<String> stopdepth,
            ) {
        public Duration timeToDuration() {
            return parseDuration(time);
        }

        public @Nullable Duration ndlToDuration() {
            return Optional.ofNullable(ndl).map(SubsurfaceXmlFile::parseDuration).orElse(null);
        }

        public @Nullable List<Duration> stopTimes() {
            if (stoptime == null) {
                return null;
            }
            return List.of(parseDuration(Objects.requireNonNull(stoptime)));
        }

        public @Nullable List<Double> stopDepths() {
            if (stopdepth == null) {
                return null;
            }
            return List.of(parseUntilSpace(stopdepth));
        }

        public List<DecoStop> toDeco() {
            record DepthTime(double depth, @NotNull Duration time) {}
            record DepthTimeNullable(@Nullable Double depth, @Nullable Duration time) {
                public Stream<DepthTime> stream() {
                    if (depth == null
                            || depth == 0
                            || time == null
                            || time.isZero()
                            || time.isNegative()) {
                        return Stream.empty();
                    }
                    return Stream.of(new DepthTime(depth, time));
                }
            }
            final var stopTimes = stopTimes();
            final var stopDepths = stopDepths();
            if (!"1".equals(inDeco)
                    || stopTimes == null
                    || stopDepths == null
                    || stopTimes.isEmpty()
                    || stopDepths.isEmpty()) {
                return List.of();
            }
            if (stopTimes.size() != stopDepths.size()) {
                throw new IllegalArgumentException("stop depth and stop time must have same size");
            }
            return Streams.zip(stopDepths.stream(), stopTimes.stream(), DepthTimeNullable::new)
                    .filter(Objects::nonNull)
                    .flatMap(DepthTimeNullable::stream)
                    .map(pair -> new DecoStop("mandatory", pair.depth(), pair.time().toSeconds()))
                    .toList();
        }
    }
}
