package ch.sthomas.stddivelogger.model.importer.dl7;

import org.jspecify.annotations.Nullable;

/**
 * One row of a DL7 {@code ZDP{...}ZDP}} profile block: pipe-delimited, leading empty field, then
 * elapsed minutes and depth (meters) at fixed positions. Only these two plus temperature are
 * confidently identified against a real export (cross-checked against the same dive's Shearwater
 * native XML) - several other pipe-delimited slots are populated but their meaning wasn't
 * confidently identifiable from one real file alone (values didn't match NDL, TTS, or PPO2 from the
 * cross-referenced XML), so they're deliberately left unparsed rather than guessed at.
 */
public record Dl7Sample(
        double elapsedMinutes, double depthMeters, @Nullable Double temperatureCelsius) {}
