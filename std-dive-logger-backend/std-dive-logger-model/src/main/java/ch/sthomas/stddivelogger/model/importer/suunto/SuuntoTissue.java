package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Diving.StartTissue / EndTissue: the device's own tissue state before / after the dive. CNS and
 * OLF are fractions (0.069 = 6.9 %), OTU plain units; Nitrogen / Helium one raw value per
 * compartment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoTissue(
        @JsonProperty("CNS") @Nullable Double cns,
        @JsonProperty("OTU") @Nullable Double otu,
        @JsonProperty("OLF") @Nullable Double olf,
        @Nullable List<Double> nitrogen,
        @Nullable List<Double> helium) {}
