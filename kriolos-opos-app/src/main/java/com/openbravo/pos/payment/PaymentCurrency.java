package com.openbravo.pos.payment;

import com.openbravo.pos.config.AppConfig;
import com.openbravo.pos.ticket.DualCurrencyTotal;

final class PaymentCurrency {
    private PaymentCurrency() {}

    static DualCurrencyTotal configured() {
        var config = AppConfig.getInstance();
        try {
            var currency = new DualCurrencyTotal(config.getProperty("currency.primary"),
                    config.getProperty("currency.secondary"), config.getProperty("currency.secondary.rate"));
            return currency.hasSecondary() ? currency : null;
        } catch (IllegalArgumentException ex) { return null; }
    }
}
