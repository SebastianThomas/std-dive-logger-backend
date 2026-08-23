package ch.sthomas.stddivelogger.model.importer.dl7;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A parsed DAN DL7 ("Universal Dive Data Format") file - a segment-based, pipe-delimited format
 * (ZRH=device header, ZDH=dive header, ZDP=profile samples, ZDT=dive trailer). No TTS or deco-stop
 * data was identifiable in the profile block for the real export this was built against - see
 * Dl7Sample's doc comment.
 */
public record Dl7Export(
        String deviceModel,
        @Nullable String deviceSerial,
        int diveNumber,
        Instant startTime,
        double maxDepth,
        List<Dl7Sample> samples) {}
