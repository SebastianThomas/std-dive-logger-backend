package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

/**
 * A sample's Events mix shapes - only {@code GasSwitch} is consumed (State transitions exist but
 * unused). One nullable field per kind beats a polymorphic deserializer here, at the cost of not
 * enforcing "exactly one" at the type level.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoEvent(@Nullable SuuntoGasSwitchEvent gasSwitch) {}
