package ch.sthomas.stddivelogger.model.dive;

import jakarta.validation.constraints.NotNull;

public record DiveNumber(int number, int fraction) {
    public DiveNumber {
        if (number <= 0 || fraction < 0) {
            throw new IllegalArgumentException(
                    "Dive Number should be >= 1 and fraction >= 0, got: "
                            + number
                            + ", "
                            + fraction);
        }
    }

    public DiveNumber(final int number) {
        this(number, 0);
    }

    public boolean isFractional() {
        return fraction > 0;
    }

    @Override
    @NotNull
    public String toString() {
        return String.format("%d.%d", number, fraction);
    }
}
