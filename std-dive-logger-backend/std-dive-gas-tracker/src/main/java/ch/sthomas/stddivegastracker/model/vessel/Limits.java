package ch.sthomas.stddivegastracker.model.vessel;

import ch.sthomas.stddivegastracker.model.Money;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared input checks of the request records (DOMAIN §3 limits); each throws a 400 message. */
public final class Limits {

    static final int MAX_LABEL_LENGTH = 60;
    private static final double MAX_PRESSURE_BAR = 400;
    private static final double MAX_KG = 1000;
    private static final double MAX_WATER_VOLUME_LITRES = 5000;
    private static final BigDecimal MAX_MONEY = new BigDecimal("1000000000");

    private Limits() {}

    public static String label(final @Nullable String raw) {
        final var label = raw == null ? "" : raw.strip();
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label must be 1-" + MAX_LABEL_LENGTH + " characters.");
        }
        return label;
    }

    /** Water volume in (0, 5000] litres; written as !(in range) so NaN fails too. */
    public static double waterVolumeLitres(final double value) {
        if (!(value > 0 && value <= MAX_WATER_VOLUME_LITRES)) {
            throw new IllegalArgumentException(
                    "Water volume must be above 0 and at most 5000 litres.");
        }
        return value;
    }

    /** Absorbent in (0, 1000] kg; written as !(in range) so NaN fails too. */
    public static double kg(final double value, final String name) {
        if (!(value > 0 && value <= MAX_KG)) {
            throw new IllegalArgumentException(name + " must be above 0 and at most 1000 kg.");
        }
        return value;
    }

    /** Gauge pressure in [0, 400] bar; written as !(in range) so NaN fails too. */
    public static double pressureBar(final @Nullable Double value, final String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        if (!(value >= 0 && value <= MAX_PRESSURE_BAR)) {
            throw new IllegalArgumentException(name + " must be between 0 and 400 bar.");
        }
        return value;
    }

    /** Money above 0, below 1e9, at most 2 decimals (DOMAIN §3); returned with exactly 2. */
    public static BigDecimal money(final @Nullable BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0.");
        }
        if (amount.compareTo(MAX_MONEY) >= 0) {
            throw new IllegalArgumentException("Amount must be below 1,000,000,000.");
        }
        if (amount.stripTrailingZeros().scale() > Money.AMOUNT_SCALE) {
            throw new IllegalArgumentException("Amount can have at most 2 decimals.");
        }
        return amount.setScale(Money.AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    /** Stripped text of at most {@code maxLength} characters; blank counts as absent (null). */
    public static @Nullable String text(
            final @Nullable String raw, final int maxLength, final String name) {
        final var text = raw == null ? "" : raw.strip();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be at most " + maxLength + " characters.");
        }
        return text.isEmpty() ? null : text;
    }
}
