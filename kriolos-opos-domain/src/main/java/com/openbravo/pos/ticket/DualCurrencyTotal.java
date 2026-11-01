package com.openbravo.pos.ticket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Currency conversion and formatting. Accounting amounts remain in the base currency. */
public final class DualCurrencyTotal {

    private final Currency primary;
    private final Currency secondary;
    private final BigDecimal rate;

    public DualCurrencyTotal(String primaryCode, String secondaryCode, String rateText) {
        primary = currency(primaryCode);
        secondary = currency(secondaryCode);
        rate = secondary == null ? null : new BigDecimal(rateText == null ? "" : rateText.trim());
        if (secondary != null && (primary == null || primary.equals(secondary)
                || rate.signum() <= 0)) {
            throw new IllegalArgumentException("Choose different currencies and a positive exchange rate");
        }
    }

    private static Currency currency(String code) {
        return code == null || code.isBlank() ? null : Currency.getInstance(code.trim().toUpperCase(Locale.ROOT));
    }

    public boolean hasSecondary() {
        return secondary != null;
    }

    public String primaryCode() { return primary == null ? "" : primary.getCurrencyCode(); }
    public String secondaryCode() { return secondary == null ? "" : secondary.getCurrencyCode(); }

    public BigDecimal roundPrimary(BigDecimal amount) {
        return amount.setScale(primary == null ? 2 : Math.max(primary.getDefaultFractionDigits(), 0), RoundingMode.HALF_UP);
    }

    public BigDecimal toPrimary(BigDecimal secondaryAmount) {
        if (!hasSecondary()) throw new IllegalStateException("Secondary currency is disabled");
        return secondaryAmount.divide(rate, java.math.MathContext.DECIMAL128);
    }

    public BigDecimal toSecondary(BigDecimal primaryAmount) {
        if (!hasSecondary()) throw new IllegalStateException("Secondary currency is disabled");
        return primaryAmount.multiply(rate).setScale(Math.max(secondary.getDefaultFractionDigits(), 0), RoundingMode.HALF_UP);
    }

    public String formatPrimary(double amount, Locale locale) {
        return formatPrimary(BigDecimal.valueOf(amount), locale);
    }

    public String formatPrimary(BigDecimal amount, Locale locale) {
        return primary == null ? null : format(amount, primary, locale);
    }

    public String formatSecondary(double amount, Locale locale) {
        return formatSecondary(BigDecimal.valueOf(amount), locale);
    }

    public String formatSecondary(BigDecimal amount, Locale locale) {
        if (!hasSecondary()) {
            return "";
        }
        return format(toSecondary(amount), secondary, locale);
    }

    public String formatSecondaryAmount(BigDecimal amount, Locale locale) {
        return hasSecondary() ? format(amount, secondary, locale) : "";
    }

    private static String format(double amount, Currency currency, Locale locale) {
        return format(BigDecimal.valueOf(amount), currency, locale);
    }

    private static String format(BigDecimal amount, Currency currency, Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        number.setMinimumFractionDigits(digits);
        number.setMaximumFractionDigits(digits);
        number.setRoundingMode(RoundingMode.HALF_UP);
        return currency.getCurrencyCode() + " " + number.format(amount);
    }
}
