package tulita.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money handling for the budget planner.
 *
 * <p>All monetary values are {@link BigDecimal} with a fixed scale of two and
 * {@link RoundingMode#HALF_UP}. The optimiser converts to integer minor units ("cents") while it
 * searches, because integer arithmetic makes the budget comparison exact and keeps the dynamic
 * program deterministic.</p>
 *
 * <p>Formatting groups the integer part using the Indian convention (last three digits, then pairs)
 * because this is an Indian college project, but the currency code always comes from the catalogue
 * record. A USD amount therefore prints as "USD 1,23,456.00" and is never shown as a rupee
 * amount.</p>
 */
public final class BudgetMoney {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private BudgetMoney() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter an amount.");
        }
        String cleaned = text.trim().replace(",", "").replace(" ", "");
        try {
            return scale(new BigDecimal(cleaned));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text.trim() + "' is not a valid amount.");
        }
    }

    public static long cents(BigDecimal value) {
        return scale(value).movePointRight(SCALE).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }

    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        return scale(scale(unitPrice).multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        return scale(scale(value).multiply(percent).divide(BigDecimal.valueOf(100), 6, ROUNDING));
    }

    /** "USD 1,23,456.00" — Indian grouping, source currency code. */
    public static String format(BigDecimal value, String currency) {
        if (value == null) {
            return "unknown";
        }
        BigDecimal scaled = scale(value);
        boolean negative = scaled.signum() < 0;
        BigDecimal absolute = scaled.abs();
        String integer = absolute.setScale(0, RoundingMode.DOWN).toPlainString();
        String fraction = absolute.subtract(new BigDecimal(integer)).setScale(SCALE, ROUNDING).toPlainString();
        fraction = fraction.substring(fraction.indexOf('.') + 1);
        String grouped = groupIndian(integer);
        String symbol = currency == null || currency.isBlank() ? "" : currency.trim() + " ";
        return (negative ? "-" : "") + symbol + grouped + "." + fraction;
    }

    public static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder out = new StringBuilder();
        int index = rest.length();
        while (index > 2) {
            out.insert(0, "," + rest.substring(index - 2, index));
            index -= 2;
        }
        if (index > 0) {
            out.insert(0, rest.substring(0, index));
        }
        String head = out.toString();
        if (head.startsWith(",")) {
            head = head.substring(1);
        }
        return head + "," + last3;
    }

    /** 95.63 % style, used for budget utilisation. */
    public static String percent(double value) {
        return BigDecimal.valueOf(value).setScale(2, ROUNDING).stripTrailingZeros().toPlainString() + " %";
    }

    public static String score(double value) {
        return BigDecimal.valueOf(value * 100).setScale(1, ROUNDING).toPlainString() + " / 100";
    }

    public static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
