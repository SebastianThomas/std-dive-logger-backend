package ch.sthomas.stddivelogger.utils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class FileValidator {
    private static final Instant TWO_THOUSAND =
            OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

    private FileValidator() {}

    public static boolean timeIsValid(final Instant time) {
        return !time.equals(Instant.EPOCH) && !time.equals(TWO_THOUSAND);
    }
}
