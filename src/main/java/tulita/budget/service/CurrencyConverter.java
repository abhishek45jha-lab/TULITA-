package tulita.budget.service;

import java.math.BigDecimal;

/**
 * Converts between the catalogue's recorded currency and the currency the shopper wants to read and
 * type amounts in (typically INR).
 *
 * <p><strong>Where the rate comes from.</strong> The catalogue records prices in one currency only (the
 * bundled data is USD) and contains no exchange rates. Tulita therefore never supplies, guesses or
 * fetches a rate: the shopper types the rate they want to use, exactly like the delivery fee and discount
 * assumptions. Every converted amount is labelled with that rate so no figure can be mistaken for a
 * retailer's own price.</p>
 *
 * <p><strong>What is converted.</strong> Only the two edges of the workflow: the budget the shopper types
 * (display currency → catalogue currency) and the amounts shown back (catalogue currency → display
 * currency). The optimiser, the arithmetic, the hard budget constraint and the persisted data all stay in
 * the catalogue currency, so a conversion can never change which basket is selected or make a basket
 * look cheaper than it is.</p>
 *
 * <p>Conversion is monotonic, so a total inside the budget in the catalogue currency can never appear
 * above the budget after conversion.</p>
 */
public record CurrencyConverter(String catalogueCurrency, String displayCurrency,
                                BigDecimal unitsPerCatalogueUnit) {

    /** Sane guard rails for a rate the shopper types; not a claim about real exchange rates. */
    public static final BigDecimal MIN_RATE = new BigDecimal("0.0001");
    public static final BigDecimal MAX_RATE = new BigDecimal("1000000");

    public CurrencyConverter {
        if (catalogueCurrency == null || catalogueCurrency.isBlank()) {
            throw new IllegalArgumentException("The catalogue currency is unknown.");
        }
        if (displayCurrency == null || displayCurrency.isBlank()) {
            throw new IllegalArgumentException("Choose the currency you want to see amounts in.");
        }
        if (unitsPerCatalogueUnit != null
            && (unitsPerCatalogueUnit.signum() <= 0
            || unitsPerCatalogueUnit.compareTo(MIN_RATE) < 0
            || unitsPerCatalogueUnit.compareTo(MAX_RATE) > 0)) {
            throw new IllegalArgumentException("The rate must be greater than zero (got "
                + unitsPerCatalogueUnit.toPlainString() + ").");
        }
    }

    /** No conversion: amounts are shown exactly as the catalogue records them. */
    public static CurrencyConverter catalogueOnly(String catalogueCurrency) {
        return new CurrencyConverter(catalogueCurrency, catalogueCurrency, null);
    }

    /**
     * A user-declared rate.
     *
     * @param rateText how many display-currency units one catalogue unit is worth, e.g. {@code "83.50"}
     *                 for 1 USD = 83.50 INR
     */
    public static CurrencyConverter userDeclared(String catalogueCurrency, String displayCurrency,
                                                 String rateText) {
        if (displayCurrency == null || displayCurrency.isBlank()
            || displayCurrency.equalsIgnoreCase(catalogueCurrency)) {
            return catalogueOnly(catalogueCurrency);
        }
        if (rateText == null || rateText.isBlank()) {
            throw new IllegalArgumentException("Enter the " + displayCurrency + " amount you want to use for"
                + " one " + catalogueCurrency + ". Tulita does not supply exchange rates.");
        }
        String cleaned = rateText.trim().replace(",", "");
        BigDecimal rate;
        try {
            rate = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + rateText.trim() + "' is not a valid rate.");
        }
        return new CurrencyConverter(catalogueCurrency, displayCurrency, rate);
    }

    /** True when a conversion is actually applied. */
    public boolean converts() {
        return unitsPerCatalogueUnit != null
            && displayCurrency != null && !displayCurrency.equalsIgnoreCase(catalogueCurrency);
    }

    /** Budget typed in the display currency → the amount the optimiser uses. */
    public BigDecimal toCatalogue(BigDecimal displayAmount) {
        BigDecimal scaled = BudgetMoney.scale(displayAmount);
        if (!converts()) {
            return scaled;
        }
        return BudgetMoney.scale(scaled.divide(unitsPerCatalogueUnit, 6, BudgetMoney.ROUNDING));
    }

    /** An amount recorded in the catalogue currency → the display currency. */
    public BigDecimal toDisplay(BigDecimal catalogueAmount) {
        BigDecimal scaled = BudgetMoney.scale(catalogueAmount);
        if (!converts()) {
            return scaled;
        }
        return BudgetMoney.scale(scaled.multiply(unitsPerCatalogueUnit));
    }

    /** Formats a catalogue-currency amount in the display currency. */
    public String format(BigDecimal catalogueAmount) {
        return BudgetMoney.format(toDisplay(catalogueAmount), displayCurrency);
    }

    /** Formats an amount that is already in the display currency (e.g. the typed budget). */
    public String formatDisplay(BigDecimal displayAmount) {
        return BudgetMoney.format(BudgetMoney.scale(displayAmount), displayCurrency);
    }

    /** Formats a bare number of catalogue units for sliders/axes that must not carry a currency. */
    public BigDecimal rate() {
        return unitsPerCatalogueUnit;
    }

    /** One-line statement of the assumption, shown next to converted amounts. */
    public String describe() {
        if (!converts()) {
            return "Amounts are in " + catalogueCurrency + ", the catalogue's own currency. No conversion is applied.";
        }
        return "Converted at your rate of 1 " + catalogueCurrency + " = "
            + BudgetMoney.format(BudgetMoney.scale(unitsPerCatalogueUnit), displayCurrency)
            + ". Tulita does not supply exchange rates, so the " + displayCurrency
            + " figures are your own assumption; the basket, prices and budget checks use the recorded "
            + catalogueCurrency + " amounts.";
    }

    /** Short label for a chart axis or column heading. */
    public String displayLabel() {
        return converts() ? displayCurrency + " at your rate" : catalogueCurrency;
    }
}
