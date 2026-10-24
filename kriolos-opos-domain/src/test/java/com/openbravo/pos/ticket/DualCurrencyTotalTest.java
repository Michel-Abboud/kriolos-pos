package com.openbravo.pos.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class DualCurrencyTotalTest {

    @Test
    void convertsAndRoundsDisplayAmount() {
        DualCurrencyTotal total = new DualCurrencyTotal("USD", "EUR", "0.925");
        assertEquals("USD 10.01", total.formatPrimary(10.01, Locale.US));
        assertEquals("EUR 9.26", total.formatSecondary(10.01, Locale.US));
    }

    @Test
    void respectsCurrenciesWithoutFractionDigits() {
        DualCurrencyTotal total = new DualCurrencyTotal("USD", "JPY", "150.5");
        assertEquals("JPY 151", total.formatSecondary(1.0, Locale.US));
    }

    @Test
    void secondaryIsOptionalAndInvalidRatesAreRejected() {
        DualCurrencyTotal disabled = new DualCurrencyTotal("USD", "", "");
        assertFalse(disabled.hasSecondary());
        assertEquals("", disabled.formatSecondary(5.0, Locale.US));
        assertTrue(new DualCurrencyTotal("USD", "EUR", "1").hasSecondary());
        assertThrows(IllegalArgumentException.class, () -> new DualCurrencyTotal("USD", "EUR", "0"));
        assertThrows(IllegalArgumentException.class, () -> new DualCurrencyTotal("USD", "USD", "1"));
        assertThrows(IllegalArgumentException.class, () -> new DualCurrencyTotal("INVALID", "EUR", "1"));
    }
}
