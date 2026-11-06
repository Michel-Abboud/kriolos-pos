package com.openbravo.pos.payment;

import com.openbravo.pos.ticket.DualCurrencyTotal;
import java.math.BigDecimal;

/** Mixed cash tender normalized to the accounting currency, with a split cash return. */
public final class DualCurrencyCash {
    private static final BigDecimal PRIMARY_RETURN_UNIT = new BigDecimal("5");
    private final DualCurrencyTotal currency;

    public DualCurrencyCash(DualCurrencyTotal currency) {
        if (currency == null || !currency.hasSecondary()) throw new IllegalArgumentException("Two currencies are required");
        this.currency = currency;
    }

    public record Result(BigDecimal givenInPrimary, BigDecimal appliedInPrimary,
                         BigDecimal remainingInPrimary, BigDecimal returnPrimary, BigDecimal returnSecondary) {
        public boolean complete() { return remainingInPrimary.signum() == 0; }
    }

    public Result calculate(BigDecimal due, BigDecimal givenPrimary, BigDecimal givenSecondary) {
        if (due.signum() < 0 || givenPrimary.signum() < 0 || givenSecondary.signum() < 0) {
            throw new IllegalArgumentException("Cash amounts must not be negative");
        }
        due = currency.roundPrimary(due);
        BigDecimal exactTender = givenPrimary.add(currency.toPrimary(givenSecondary));
        BigDecimal paid = currency.roundPrimary(exactTender);
        // Keep the original secondary tender precision when calculating change.
        // Converting rounded accounting cash back to secondary currency can
        // invent change (e.g. LBP 150,000 -> USD 1.67 -> LBP 150,300).
        BigDecimal change = exactTender.subtract(due).max(BigDecimal.ZERO);
        BigDecimal primaryReturn = change.divideToIntegralValue(PRIMARY_RETURN_UNIT).multiply(PRIMARY_RETURN_UNIT);
        return new Result(paid, paid.min(due), due.subtract(paid).max(BigDecimal.ZERO),
                primaryReturn, currency.toSecondary(change.subtract(primaryReturn)));
    }
}
