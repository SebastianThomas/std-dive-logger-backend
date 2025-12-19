package ch.sthomas.stddivelogger.model.importer;

import ch.sthomas.stddivelogger.model.dive.DecoStop;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.google.common.collect.MoreCollectors;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

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
    public Optional<Integer> diveNumber() {
        final var number = profileData.repetitionGroup.dive.infoBeforeDive.divenumber;
        try {
            return Optional.of(Integer.parseInt(number));
        } catch (final NumberFormatException e) {
            if (number.contains(".")) {
                return Optional.of(-1 * Integer.parseInt(number.substring(0, number.indexOf('.'))));
            }
            return Optional.empty();
        }
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
    record SurfaceIntervalBeforeDive(int passedtime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EquipmentUsed(@JacksonXmlElementWrapper(useWrapping = false) List<Link> link) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfTankData(
            @JacksonXmlProperty(localName = "tankpressurebegin") double pressureStart,
            @JacksonXmlProperty(localName = "tankpressureend") double pressureEnd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSamples(@JacksonXmlElementWrapper(useWrapping = false) List<UddfSample> waypoint) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UddfSample(
            @JacksonXmlProperty(localName = "batterychargecondition") double battery,
            int cns,
            @JacksonXmlProperty(localName = "calculatedpo2") double po2,
            double depth,
            @JacksonXmlProperty(localName = "divetime") int seconds,
            @Nullable UddfSwitchMix switchmix,
            @JacksonXmlProperty(localName = "temperature") double kelvin,
            @JacksonXmlProperty(localName = "divemode") UddfDiveMode diveMode,
            @JacksonXmlProperty(localName = "nodecotime") int ndl,
            @JacksonXmlElementWrapper(useWrapping = false)
                    @JacksonXmlProperty(localName = "decostop")
                    @Nullable
                    List<UddfDecoStop> decoStop,
            @JacksonXmlProperty(localName = "gradientfactor") int gf) {
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
                            null,
                            (double) gf,
                            null,
                            (double) cns),
                    previousGas);
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
        return diveSite.site().geography().location();
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
