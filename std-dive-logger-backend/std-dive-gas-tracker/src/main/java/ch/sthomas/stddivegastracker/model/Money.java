package ch.sthomas.stddivegastracker.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** API-boundary rounding of money (DOMAIN §3): amounts 2 dp, unit prices 4 dp, HALF_EVEN. */
public final class Money {

    public static final int AMOUNT_SCALE = 2;
    private static final int UNIT_PRICE_SCALE = 4;

    private Money() {}

    public static BigDecimal amount(final BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal unitPrice(final BigDecimal value) {
        return value.setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_EVEN);
    }
}
