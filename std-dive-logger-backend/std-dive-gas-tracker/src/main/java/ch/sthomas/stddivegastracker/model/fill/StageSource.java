package ch.sthomas.stddivegastracker.model.fill;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * API.md §4 source of a stage, on input and output: a vessel or compressor AIR (free, DOMAIN §5.1).
 * {@code label} is output only.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StageSource.Vessel.class, name = "VESSEL"),
    @JsonSubTypes.Type(value = StageSource.Air.class, name = "AIR")
})
@Schema(
        oneOf = {StageSource.Vessel.class, StageSource.Air.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "VESSEL", schema = StageSource.Vessel.class),
            @DiscriminatorMapping(value = "AIR", schema = StageSource.Air.class)
        })
public sealed interface StageSource {

    // Subtypes name the parent in allOf: springdoc 3.2.1 (swagger-core 3) stops inferring it and
    // drops `type` from each subtype's schema (OpenApiSnapshotTest).
    /** An absent {@code vesselId} is 0, which no vessel has (400 "Unknown bank" in the service). */
    @Schema(name = "StageSourceVessel", allOf = StageSource.class)
    record Vessel(long vesselId, @Nullable String label) implements StageSource {}

    @Schema(name = "StageSourceAir", allOf = StageSource.class)
    record Air() implements StageSource {}
}
