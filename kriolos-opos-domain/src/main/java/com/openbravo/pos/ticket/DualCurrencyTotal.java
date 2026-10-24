package com.openbravo.pos.ticket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Display-only conversion of a ticket total. Payments remain in the base currency. */
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

    public String formatPrimary(double amount, Locale locale) {
        return primary == null ? null : format(amount, primary, locale);
    }

    public String formatSecondary(double amount, Locale locale) {
        if (!hasSecondary()) {
            return "";
        }
        BigDecimal converted = BigDecimal.valueOf(amount).multiply(rate)
                .setScale(Math.max(secondary.getDefaultFractionDigits(), 0), RoundingMode.HALF_UP);
        return format(converted, secondary, locale);
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
