package ch.sthomas.stddivelogger.model.importer;

import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.utils.FileValidator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.google.common.collect.MoreCollectors;
import com.vdurmont.semver4j.Semver;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "uddf")
public record UddfFile(
        String version,
        UddfGenerator generator,
        UddfDiver diver,
        @JacksonXmlProperty(localName = "divesite") UddfDiveSite diveSite,
        @JacksonXmlProperty(localName = "gasdefinitions") List<UddfGasMix> gasDefinitions,
        @JacksonXmlProperty(localName = "decomodel") Map<String, UddfDecoModel> decoModel,
        @JacksonXmlProperty(localName = "profiledata") UddfProfileData profileData,
        @JacksonXmlProperty(localName = "tablegeneration") UddfTableGeneration tableGeneration) {
    private static final Logger logger = LoggerFactory.getLogger(UddfFile.class);
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final BigDecimal BD_NANOS_PER_SECOND =
            BigDecimal.valueOf((double) NANOS_PER_SECOND);

    public static boolean validate(final UddfFile uddfFile, final int entry) {
        final var version = new Semver(uddfFile.version, Semver.SemverType.LOOSE);
        if (version.isLowerThan("3.0.0") || version.isGreaterThanOrEqualTo("4.0.0")) {
            throw new IllegalArgumentException(
                    MessageFormat.format("UDDF file version {0} not supported", version));
        }

        if (uddfFile.profileData == null || uddfFile.profileData.repetitionGroup == null) {
            throw new IllegalArgumentException("Profile Data is null, cannot extract dive.");
        }
        if (entry > uddfFile.getEntries()) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "Failed to extract entry {0} from file, only has {1} entries",
                            entry, uddfFile.getEntries()));
        }
        if (uddfFile.profileData.repetitionGroup.get(entry).dive.samples.waypoint().size() <= 2) {
            logger.info(
                    "Repetition group {} has too few waypoints: {}, skipping.",
                    entry,
                    uddfFile.profileData.repetitionGroup.get(entry).dive.samples);
            return false;
        }
        return true;
    }

    public int getEntries() {
        return profileData.repetitionGroup.size();
    }

    public Optional<DiveNumber> exportDiveNumber(final int entry) {
        return diveNumber(Objects.requireNonNull(profileData.repetitionGroup.get(entry).dive));
    }

    public Instant exportStart(final int entry) {
        return getStart(Objects.requireNonNull(profileData.repetitionGroup.get(entry)).dive);
    }

    public Instant exportEnd(final int entry) {
        return getEnd(Objects.requireNonNull(profileData.repetitionGroup.get(entry)).dive);
    }

    public String exportNotes(final int entry) {
        return getNotes(profileData.repetitionGroup.get(entry).dive);
    }

    public Optional<Visibility> exportVisibility(final int entry) {
        return getVisibility(profileData.repetitionGroup.get(entry).dive);
    }

    public DiveGasConsumption exportGasConsumption(final int entry) {
        return getGasConsumption(profileData.repetitionGroup.get(entry).dive);
    }

    public List<DiveMeasurement> exportMeasurements(final int entry) {
        return getMeasurements(profileData.repetitionGroup.get(entry).dive, gasDefinitions);
    }

    static Optional<DiveNumber> diveNumber(final UddfProfileDataDive dive) {
        final var number = dive.infoBeforeDive.divenumber;
        if (number == null || number.isBlank()) {
            return Optional.empty();
        }
        if (number.startsWith("+")) {
            final var anyNonZeroFraction = 1;
            return Optional.of(
                    new DiveNumber(Integer.parseInt(number.substring(1)), anyNonZeroFraction));
        }
        try {
            return Optional.of(new DiveNumber(Integer.parseInt(number)));
        } catch (final NumberFormatException e) {
            if (number.contains(".")) {
                return Optional.of(
                        new DiveNumber(
                                Integer.parseInt(number.substring(0, number.indexOf('.'))),
                                Integer.parseInt(number.substring(number.indexOf('.') + 2))));
            }
            return Optional.empty();
        }
    }

    static DiveGasConsumption getGasConsumption(final UddfProfileDataDive dive) {
        final var tankData = dive.tankdata;
        if (tankData == null) {
            return DiveGasConsumption.EMPTY;
        }
        final var usedPerTank =
                tankData.stream()
                        .map(
                                t -> {
                                    final var pressure =
                                            Optional.ofNullable(t.pressureStart)
                                                    .flatMap(
                                                            s ->
                                                                    Optional.ofNullable(
                                                                                    t.pressureEnd)
                                                                            .map(e -> e - s));
                                    return Pair.of(
                                            pressure.orElse(null),
                                            Optional.ofNullable(t.breathingVolume)
                                                    .or(() -> pressure.map(p -> p * t.tankVolume))
                                                    .orElse(null));
                                })
                        .toList();
        final var totalLiters = usedPerTank.stream().mapToDouble(Pair::getRight).sum();
        return new DiveGasConsumption(0, 0, totalLiters);
    }

    public DiveConfiguration getConfiguration(final User user) {
        return DiveConfiguration.createEmpty(user);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfGenerator(
            String name,
            String type,
            UddfManufacturer manufacturer,
            String version,
            Instant datetime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfManufacturer(String id, String name, UddfAddress address, UddfContact contact) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfAddress(
            String street, String city, String postcode, String country, String province) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfContact(String language, String phone, String fax, String email, String homepage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfDiver(@JacksonXmlProperty(localName = "owner") UddfOwner owner, UddfBuddy buddy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfOwner(@Nullable UddfOwnerEquipment equipment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfOwnerEquipment(
            @JacksonXmlProperty(localName = "divecomputer")
                    UddfOwnerEquipmentDiveComputer diveComputer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfOwnerEquipmentDiveComputer(
            String id,
            String name,
            UddfManufacturer manufacturer,
            String model,
            @JacksonXmlProperty(localName = "serialnumber") String serialNumber,
            UddfNotes notes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfNotes(
            @JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(localName = "para")
                    List<String> parameters) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfBuddy(
            String id, @JacksonXmlProperty(localName = "personal") UddfPersonal personal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfPersonal(@Nullable String firstname, @Nullable String lastname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfDiveSite(UddfSite site) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSite(String id, UddfGeography geography) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfGeography(String location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfGasMix(
            String id,
            String name,
            double o2,
            double he,
            @JacksonXmlProperty(localName = "maximumpo2") double maximumPO2) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfDecoModel(
            @JacksonXmlProperty(isAttribute = true) String id,
            @JacksonXmlProperty(localName = "gradientfactorhigh") Integer gfHigh,
            @JacksonXmlProperty(localName = "gradientfactorlow") Integer gfLow) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfTableGeneration(
            @JacksonXmlProperty(localName = "calculateprofile")
                    UddfTableGenerationCalculateProfile calculateProfile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfTableGenerationCalculateProfile(UddfTableGenerationProfile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfTableGenerationProfile(String id, int density, String decomodel) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UddfProfileData(
            @NotNull
                    @JacksonXmlProperty(localName = "repetitiongroup")
                    @JacksonXmlElementWrapper(useWrapping = false)
                    List<UddfProfileRepetitionGroup> repetitionGroup) {
        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof UddfProfileData(final var group))) return false;
            return new EqualsBuilder().append(repetitionGroup, group).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(repetitionGroup).toHashCode();
        }

        public UddfProfileRepetitionGroup getData(final int i) {
            return Objects.requireNonNull(repetitionGroup.get(i));
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this).append("repetitionGroup", repetitionGroup).toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UddfProfileRepetitionGroup(
            @Nullable @JacksonXmlProperty(isAttribute = true) String id, UddfProfileDataDive dive) {
        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof UddfProfileRepetitionGroup(final var id1, final var dive1)))
                return false;
            return new EqualsBuilder().append(id, id1).append(dive, dive1).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(id).append(dive).toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this).append("id", id).append("dive", dive).toString();
        }

        public boolean timeIsValid() {
            final var time = dive.infoBeforeDive.datetime;
            return FileValidator.timeIsValid(time);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfProfileDataDive(
            String id,
            Object applicationdata,
            @JacksonXmlProperty(localName = "informationbeforedive")
                    UddfInfoBeforeDive infoBeforeDive,
            @Nullable @JacksonXmlElementWrapper(useWrapping = false) List<UddfTankData> tankdata,
            UddfSamples samples,
            @JacksonXmlProperty(localName = "informationafterdive")
                    UddfInfoAfterDive infoAfterDive) {
        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o
                    instanceof
                    UddfProfileDataDive(
                            final var id1,
                            final var applicationdata1,
                            final var beforeDive,
                            final var _,
                            final var samples1,
                            final var afterDive))) return false;
            return new EqualsBuilder()
                    .append(id, id1)
                    .append(samples.waypoint.toArray(), samples1.waypoint.toArray())
                    .append(applicationdata, applicationdata1)
                    .append(infoAfterDive, afterDive)
                    .append(infoBeforeDive, beforeDive)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(id)
                    .append(samples.waypoint.size())
                    .toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("id", id)
                    .append("samples.size", samples.waypoint().size())
                    .append("applicationdata", applicationdata)
                    .append("infoBeforeDive", infoBeforeDive)
                    .append("infoAfterDive", infoAfterDive)
                    .toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfInfoBeforeDive(
            @JacksonXmlElementWrapper(useWrapping = false) List<Link> link,
            String divenumber,
            Instant datetime,
            double airtemperature,
            SurfaceIntervalBeforeDive surfaceintervalbeforedive,
            EquipmentUsed equipmentused,
            double surfacepressure) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfInfoAfterDive(
            @JacksonXmlProperty(localName = "greatestdepth") double maxDepth,
            String visibility,
            @Nullable UddfNotes notes,
            @JacksonXmlProperty(localName = "anysymptoms") NotesContainer anySymptoms,
            @JacksonXmlProperty(localName = "diveduration") double duration,
            NotesContainer observations,
            @JacksonXmlProperty(localName = "averagedepth") double avgDepth) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NotesContainer(UddfNotes notes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Link(@JacksonXmlProperty(isAttribute = true) String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SurfaceIntervalBeforeDive(Integer passedtime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EquipmentUsed(@JacksonXmlElementWrapper(useWrapping = false) List<Link> link) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfTankData(
            @JacksonXmlProperty(localName = "link") Link link,
            @JacksonXmlProperty(localName = "tankvolume") Double tankVolume,
            @JacksonXmlProperty(localName = "tankpressurebegin") Double pressureStart,
            @JacksonXmlProperty(localName = "tankpressureend") Double pressureEnd,
            @JacksonXmlProperty(localName = "breathingconsumptionvolume") Double breathingVolume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSamples(@JacksonXmlElementWrapper(useWrapping = false) List<UddfSample> waypoint) {
        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof UddfSamples(final var waypoint1))) return false;
            logger.info("Comparing sizes: {}, {}", waypoint, waypoint1);
            if (waypoint.size() != waypoint1.size()) return false;
            var builder = new EqualsBuilder();
            for (var i = 0; i < waypoint.size(); i++) {
                builder = builder.append(waypoint.get(i), waypoint1.get(i));
            }
            return builder.isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(waypoint.size()).toHashCode();
        }
    }

    /** <a href="https://www.streit.cc/extern/uddf_v321/en/waypoint.html">Waypoint Standard<a> */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSample(
            @JacksonXmlProperty(localName = "batterychargecondition") double battery,
            @JacksonXmlProperty(localName = "calculatedpo2") Double calcPO2,
            int cns,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "decostop")
                    @Nullable
                    List<UddfDecoStop> decoStop,
            double depth,
            @JacksonXmlProperty(localName = "divetime") double seconds,
            @JacksonXmlProperty(localName = "gradientfactor") int gf,
            @JacksonXmlProperty(localName = "heading") double compassHeading,
            @JacksonXmlProperty(localName = "measuredpo2") Double measuredPO2,
            @JacksonXmlProperty(localName = "divemode") UddfDiveMode diveMode,
            @JacksonXmlProperty(localName = "nodecotime") int ndl,
            @JacksonXmlProperty(localName = "otu") double otu,
            @JacksonXmlProperty(localName = "setpo2") Double setPO2,
            @JacksonXmlProperty(localName = "remainingo2time") double remainingO2Seconds,
            @Nullable UddfSwitchMix switchmix,
            @JacksonXmlProperty(localName = "tankpressure") double tankPressure,
            @JacksonXmlProperty(localName = "temperature") double kelvin) {
        public Pair<DiveMeasurement, Gas> toRecord(
                final Instant start, final List<UddfGasMix> mixes, final Gas previousGas) {
            final var gas =
                    Optional.ofNullable(switchmix)
                            .flatMap(
                                    mix ->
                                            mixes.stream()
                                                    .filter(
                                                            candidate ->
                                                                    candidate.id().equals(mix.ref))
                                                    .collect(MoreCollectors.toOptional()))
                            .map(mix -> new Gas(mix.o2, mix.he))
                            .orElse(previousGas);
            // TODO: RMV
            final var po2 = PO2.fromOrNull(setPO2, measuredPO2, calcPO2);
            final var time = getDurationFromSeconds(seconds);
            return Pair.of(
                    new DiveMeasurement(
                            start.plus(time),
                            new Temperature(kelvin, Temperature.TemperatureUnit.KELVIN).asCelsius(),
                            depth,
                            Duration.ofSeconds(ndl),
                            Optional.ofNullable(decoStop).stream()
                                    .flatMap(List::stream)
                                    .map(UddfDecoStop::toRecord)
                                    .toList(),
                            gas,
                            po2,
                            null,
                            (double) gf,
                            null,
                            (double) cns),
                    gas);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfDecoStop(
            @JacksonXmlProperty(isAttribute = true) String kind,
            @JacksonXmlProperty(isAttribute = true) int decodepth,
            @JacksonXmlProperty(localName = "duration", isAttribute = true) String seconds) {
        public DecoStop toRecord() {
            return new DecoStop(kind, decodepth, Long.parseLong(seconds));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSwitchMix(@JacksonXmlProperty(isAttribute = true) String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfDiveMode(@JacksonXmlProperty(isAttribute = true) String type) {}

    public String exportSite() {
        return Optional.ofNullable(diveSite.site())
                .map(UddfSite::geography)
                .map(UddfGeography::location)
                .orElse(null);
    }

    public String exportBuddyString() {
        if (diver.buddy() == null) {
            return "";
        }
        final var firstName = diver.buddy().personal().firstname();
        final var lastName = diver.buddy().personal().lastname();
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }

        return firstName + " " + lastName;
    }

    static Instant getStart(final UddfProfileDataDive dive) {
        return dive.infoBeforeDive.datetime;
    }

    static Instant getEnd(final UddfProfileDataDive dive) {
        return getStart(dive).plus(getDurationFromSeconds(dive.infoAfterDive.duration));
    }

    public String exportDiveComputerManufacturer() {
        if (diver.owner.equipment == null) {
            return "Unknown Manufacturer";
        }

        return diver.owner.equipment.diveComputer.manufacturer.name;
    }

    public String exportDiveComputerName() {
        if (diver.owner.equipment == null) {
            return "Unknown Dive Computer";
        }
        return diver.owner.equipment.diveComputer.name;
    }

    public String exportDiveComputerSerialNumber() {
        if (diver.owner.equipment == null) {
            return "Unknown Dive Computer Serial Number";
        }
        return diver.owner.equipment.diveComputer.serialNumber;
    }

    static List<DiveMeasurement> getMeasurements(
            final UddfProfileDataDive dive, final List<UddfGasMix> gasDefinitions) {
        final var measurements = dive.samples.waypoint;
        final var list = new ArrayList<DiveMeasurement>(measurements.size());
        Gas previousGas = null;
        for (final var m : measurements) {
            final var res = m.toRecord(getStart(dive), gasDefinitions, previousGas);
            list.add(res.getLeft());
            previousGas = res.getRight();
        }
        return list;
    }

    public List<String> getBuddies() {
        final var buddies = exportBuddyString();
        if (buddies == null) {
            return List.of();
        }

        return getSeparator(buddies)
                .map(
                        separator ->
                                Arrays.stream(buddies.trim().split(separator))
                                        .map(String::trim)
                                        .filter(String::isBlank)
                                        .toList())
                .orElseGet(() -> List.of(buddies));
    }

    static String getNotes(final UddfProfileDataDive dive) {
        if (dive.infoAfterDive.notes == null) {
            return "";
        }
        return String.join("\n", dive.infoAfterDive.notes().parameters());
    }

    static Optional<Visibility> getVisibility(final UddfProfileDataDive dive) {
        final var s = dive.infoAfterDive.visibility;
        if (s == null) {
            return Optional.empty();
        }
        final var string = s.trim();
        if (string.endsWith("m")) {
            try {
                return Optional.of(
                        new Visibility(
                                Double.parseDouble(string.substring(0, string.indexOf('m'))),
                                string,
                                null));
            } catch (final NumberFormatException e) {
                logger.info(
                        "String {} looks like a visibility in meters, but could not parse",
                        string,
                        e);
            } catch (final IllegalArgumentException e) {
                logger.info("String {} is an invalid visibility in meters", string, e);
            }
        }
        final var feeling = VisibilityFeeling.from(string);
        return Optional.of(new Visibility(null, string, feeling));
    }

    private Optional<String> getSeparator(final String s) {
        if (s.contains(",")) {
            return Optional.of(",");
        }
        if (s.contains("\n")) {
            return Optional.of("\n");
        }
        if (s.contains(";")) {
            return Optional.of(";");
        }
        return Optional.empty();
    }

    public static Duration getDurationFromSeconds(final double seconds) {
        return Duration.ofNanos(
                BigDecimal.valueOf(seconds).multiply(BD_NANOS_PER_SECOND).longValue());
    }
}
