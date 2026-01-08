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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.google.common.collect.MoreCollectors;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public Optional<DiveNumber> diveNumber() {
        final var number = profileData.repetitionGroup.dive.infoBeforeDive.divenumber;
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

    public DiveGasConsumption getGasConsumption() {
        final var tankData = profileData.repetitionGroup.dive.tankdata;
        final var usedPerTank =
                tankData.stream().map(t -> t.pressureStart - t.pressureEnd).toList();
        return DiveGasConsumption.EMPTY;
    }

    public DiveConfiguration getConfiguration() {
        return DiveConfiguration.EMPTY;
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
    record UddfOwner(UddfOwnerEquipment equipment) {}

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
    record UddfProfileData(
            @JacksonXmlProperty(localName = "repetitiongroup")
                    UddfProfileRepetitionGroup repetitionGroup) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfProfileRepetitionGroup(UddfProfileDataDive dive) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfProfileDataDive(
            String id,
            Object applicationdata,
            @JacksonXmlProperty(localName = "informationbeforedive")
                    UddfInfoBeforeDive infoBeforeDive,
            @JacksonXmlElementWrapper(useWrapping = false) List<UddfTankData> tankdata,
            UddfSamples samples,
            @JacksonXmlProperty(localName = "informationafterdive")
                    UddfInfoAfterDive infoAfterDive) {}

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
            UddfNotes notes,
            @JacksonXmlProperty(localName = "anysymptoms") NotesContainer anySymptoms,
            @JacksonXmlProperty(localName = "diveduration") int duration,
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
            @JacksonXmlProperty(localName = "tankpressurebegin") double pressureStart,
            @JacksonXmlProperty(localName = "tankpressureend") double pressureEnd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSamples(@JacksonXmlElementWrapper(useWrapping = false) List<UddfSample> waypoint) {}

    /** <a href="https://www.streit.cc/extern/uddf_v321/en/waypoint.html">Waypoint Standard<a> */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSample(
            @JacksonXmlProperty(localName = "batterychargecondition") double battery,
            @JacksonXmlProperty(localName = "calculatedpo2") double calcPO2,
            int cns,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "decostop")
                    @Nullable
                    List<UddfDecoStop> decoStop,
            double depth,
            @JacksonXmlProperty(localName = "divetime") int seconds,
            @JacksonXmlProperty(localName = "gradientfactor") int gf,
            @JacksonXmlProperty(localName = "heading") double compassHeading,
            @JacksonXmlProperty(localName = "measuredpo2") double measuredPO2,
            @JacksonXmlProperty(localName = "divemode") UddfDiveMode diveMode,
            @JacksonXmlProperty(localName = "nodecotime") int ndl,
            @JacksonXmlProperty(localName = "otu") double otu,
            @JacksonXmlProperty(localName = "setpo2") double setPO2,
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
            final var po2 = new PO2(setPO2, measuredPO2, calcPO2);
            return Pair.of(
                    new DiveMeasurement(
                            start.plusSeconds(seconds),
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

    public Instant exportStart() {
        return profileData.repetitionGroup.dive.infoBeforeDive.datetime;
    }

    public Instant exportEnd() {
        return exportStart().plusSeconds(profileData.repetitionGroup.dive.infoAfterDive.duration);
    }

    public String exportDiveComputerManufacturer() {
        return diver.owner.equipment.diveComputer.manufacturer.name;
    }

    public String exportDiveComputerName() {
        return diver.owner.equipment.diveComputer.name;
    }

    public String exportDiveComputerSerialNumber() {
        return diver.owner.equipment.diveComputer.serialNumber;
    }

    public List<DiveMeasurement> exportMeasurements() {
        final var measurements = profileData.repetitionGroup.dive.samples.waypoint;
        final var list = new ArrayList<DiveMeasurement>(measurements.size());
        Gas previousGas = null;
        for (final var m : measurements) {
            final var res = m.toRecord(exportStart(), gasDefinitions, previousGas);
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

    public String getNotes() {
        return String.join(
                "\n", profileData.repetitionGroup.dive.infoAfterDive.notes().parameters());
    }

    public Optional<Visibility> getVisibility() {
        final var s = profileData.repetitionGroup.dive.infoAfterDive.visibility;
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
}
