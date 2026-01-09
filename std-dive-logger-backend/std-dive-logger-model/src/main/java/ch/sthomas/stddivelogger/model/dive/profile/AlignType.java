package ch.sthomas.stddivelogger.model.dive.profile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Defines how profile alignment is computed")
public enum AlignType {
    @Schema(description = "Automatically align the second schema using minimum average distance")
    AUTO_MIN_AVG_DISTANCE,
    @Schema(
            description =
                    "Automatically align the second schema using minimum average squared distance")
    AUTO_MIN_AVG_SQ_DISTANCE,
    @Schema(description = "Automatically align the second profile using minimum maximum distance")
    AUTO_MIN_MAX_DISTANCE,
    @Schema(description = "Provide a manual start time for all affected profiles")
    MANUAL;
}
