package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

// mode isn't guaranteed present by the Divesoft API's own contract - Jackson leaves it null for a
// missing/malformed field, so the type has to admit that rather than claim non-null and let a
// caller dereference it unsafely (see DivesoftReaderService.toMode()'s null guard).
@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftModeSample(long timestamp, @Nullable String mode) {}
