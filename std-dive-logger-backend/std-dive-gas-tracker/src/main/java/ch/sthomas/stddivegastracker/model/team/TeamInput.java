package ch.sthomas.stddivegastracker.model.team;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * API.md §1 {@code TeamInput} (create and full update). Jackson passes null for absent fields and 0
 * for absent numbers; the constructor applies the defaults and validates (DOMAIN §2, §3). An absent
 * tracking switch stays as it is (on, for a new team).
 */
public record TeamInput(
        String name,
        String currency,
        LossPolicy defaultLossPolicy,
        double lineDeadVolumeLitres,
        double defaultResidualPressureBar,
        @Nullable Boolean gasTrackingEnabled,
        @Nullable Boolean scrubberTrackingEnabled) {

    private static final int MAX_NAME_LENGTH = 60;
    private static final double MAX_LINE_DEAD_VOLUME_LITRES = 10;
    private static final double MAX_PRESSURE_BAR = 400;
    private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");

    public TeamInput {
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Name must be 1-" + MAX_NAME_LENGTH + " characters.");
        }
        currency = currency == null ? "CHF" : currency.strip();
        if (!CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("Currency must be a 3-letter code.");
        }
        currency = currency.toUpperCase(Locale.ROOT);
        if (defaultLossPolicy == null) {
            throw new IllegalArgumentException("Loss policy is required.");
        }
        // Written as !(in range) so NaN fails too.
        if (!(lineDeadVolumeLitres >= 0 && lineDeadVolumeLitres <= MAX_LINE_DEAD_VOLUME_LITRES)) {
            throw new IllegalArgumentException("Line dead volume must be between 0 and 10 litres.");
        }
        if (!(defaultResidualPressureBar >= 0 && defaultResidualPressureBar <= MAX_PRESSURE_BAR)) {
            throw new IllegalArgumentException("Residual pressure must be between 0 and 400 bar.");
        }
    }
}
