package com.openbravo.pos.payment;

import com.openbravo.pos.ticket.DualCurrencyTotal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DualCurrencyCashTest {
    private final DualCurrencyCash calculator = new DualCurrencyCash(new DualCurrencyTotal("USD", "LBP", "90000"));

    @ParameterizedTest
    @CsvSource({
        "13.20, 10, 900000, 20, 0, 5, 162000",
        "13.20, 20, 0, 20, 0, 5, 162000",
        "13.20, 0, 1800000, 20, 0, 5, 162000",
        "13.20, 5, 450000, 10, 3.20, 0, 0",
        "13.20, 10, 288000, 13.20, 0, 0, 0",
        "13.20, 15, 0, 15, 0, 0, 162000",
        "10, 20, 0, 20, 0, 10, 0",
        "13.20, 0, 0, 0, 13.20, 0, 0",
        "0, 0, 0, 0, 0, 0, 0"
    })
    void normalizesTenderAndSplitsReturn(String due, String primary, String secondary, String paid,
                                        String remaining, String returnedPrimary, String returnedSecondary) {
        var result = calculator.calculate(decimal(due), decimal(primary), decimal(secondary));
        equal(paid, result.givenInPrimary());
        equal(remaining, result.remainingInPrimary());
        equal(returnedPrimary, result.returnPrimary());
        equal(returnedSecondary, result.returnSecondary());
        assertEquals(result.remainingInPrimary().signum() == 0, result.complete());
        assertEquals(0, result.appliedInPrimary().compareTo(result.givenInPrimary().min(decimal(due))));
        assertEquals(0, result.returnPrimary().remainder(decimal("5")).signum());
    }

    @Test
    void roundsCombinedTenderOnceAndRejectsNegativeAmounts() {
        var result = new DualCurrencyCash(new DualCurrencyTotal("USD", "EUR", "3"))
                .calculate(decimal("5.10"), decimal("5"), decimal("0.3"));
        assertTrue(result.complete());
        equal("5.10", result.givenInPrimary());
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(decimal("13.20"), decimal("-1"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(decimal("13.20"), BigDecimal.ZERO, decimal("-1")));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    @Test
    void partialPaymentsUseAppliedAmountsAndDoNotCountChangeAsRevenue() {
        var payments = new PaymentServiceImpl();
        var list = payments.createPaymentList();
        var first = calculator.calculate(decimal("23"), decimal("5"), decimal("450000"));
        payments.addPayment(list, new PaymentInfoCash(first.appliedInPrimary().doubleValue(), first.givenInPrimary().doubleValue()));
        assertEquals(13.0, payments.calculateRemaining(list, 23));
        var second = calculator.calculate(decimal("13"), BigDecimal.ZERO, decimal("1800000"));
        payments.addPayment(list, new PaymentInfoCash(second.appliedInPrimary().doubleValue(), second.givenInPrimary().doubleValue()));
        assertEquals(23.0, payments.getTotal(list));
        assertEquals(30.0, payments.getPaidTotal(list));
        assertTrue(payments.isFullyPaid(list, 23));
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, decimal(expected).compareTo(actual), expected + " != " + actual);
    }
}
