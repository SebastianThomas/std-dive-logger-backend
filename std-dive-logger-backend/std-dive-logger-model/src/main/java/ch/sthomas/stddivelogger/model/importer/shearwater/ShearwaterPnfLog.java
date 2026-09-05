package ch.sthomas.stddivelogger.model.importer.shearwater;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A whole Shearwater "Petrel Native Format" (PNF) binary dive log, decoded - the header/footer
 * figures the device itself recorded plus every sample record. See {@code ShearwaterPnfParser}.
 *
 * <p>{@code start} carries the device's own clock reading with <b>no timezone</b> - the raw value
 * is a wall-clock reading stored as if it were a UTC epoch, exactly like Shearwater's XML/UDDF/DL7
 * exports (confirmed against {@code dive_details.DiveDate}, which is the same local wall clock, for
 * all 95 dives of the reference database). {@code ImportService.correctForUnknownTimezone} fixes
 * this at commit time once a real dive site is known.
 *
 * <p>{@code maxDepthMeters}/{@code diveTime} come from the closing record, i.e. the device's own
 * figures over its own dive window - both are usually slightly different from what the sample list
 * alone yields (the device tracks depth faster than it logs samples, and it excludes the surface
 * time before/after the dive that the samples do cover).
 */
public record ShearwaterPnfLog(
        Instant start,
        Duration diveTime,
        double maxDepthMeters,
        int logVersion,
        DiveComputerMode mode,
        Duration sampleInterval,
        boolean imperialUnits,
        @Nullable Long serialNumber,
        @Nullable Integer model,
        List<ShearwaterPnfSample> samples) {

    /** The device's own dive-mode codes, as stored in opening record 4. */
    public enum DiveComputerMode {
        CC,
        OC_TEC,
        GAUGE,
        PPO2,
        SC,
        CC2,
        OC_REC,
        FREEDIVE,
        AVELO,
        UNKNOWN;

        public static DiveComputerMode fromCode(final int code) {
            return switch (code) {
                case 0 -> CC;
                case 1 -> OC_TEC;
                case 2 -> GAUGE;
                case 3 -> PPO2;
                case 4 -> SC;
                case 5 -> CC2;
                case 6 -> OC_REC;
                case 7 -> FREEDIVE;
                case 12 -> AVELO;
                default -> UNKNOWN;
            };
        }

        public boolean isClosedCircuit() {
            return this == CC || this == CC2 || this == SC;
        }
    }
}
