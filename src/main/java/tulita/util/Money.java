package tulita.util;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
public final class Money {
    private Money() { }
    public static String inr(BigDecimal value) {
        if (value == null) return "Unknown";
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        f.setMinimumFractionDigits(value.stripTrailingZeros().scale() > 0 ? 2 : 0);
        f.setMaximumFractionDigits(2);
        return "₹" + f.format(value);
    }
}
