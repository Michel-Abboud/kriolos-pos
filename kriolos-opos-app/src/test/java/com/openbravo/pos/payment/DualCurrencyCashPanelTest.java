package com.openbravo.pos.payment;

import com.openbravo.editor.JEditorDoublePositive;
import com.openbravo.pos.config.AppConfig;
import com.openbravo.pos.ticket.DualCurrencyTotal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DualCurrencyCashPanelTest {
    @Test
    void zeroDueDisplaysExactMixedCurrencyReturn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var currency = new DualCurrencyTotal("USD", "LBP", "90000");
            var panel = new DualCurrencyCashPanel(mock(JPaymentNotifier.class),
                    currency, ignored -> { });
            panel.activate(null, 0, null);
            ((JEditorDoublePositive) find(panel, "kriolos:payment:given-primary")).setDoubleValue(10.0);
            ((JEditorDoublePositive) find(panel, "kriolos:payment:given-secondary")).setDoubleValue(150000.0);
            assertEquals(currency.formatPrimary(new BigDecimal("10"), java.util.Locale.getDefault()),
                    ((JLabel) find(panel, "kriolos:payment:return-primary")).getText());
            assertEquals(currency.formatSecondaryAmount(new BigDecimal("150000"), java.util.Locale.getDefault()),
                    ((JLabel) find(panel, "kriolos:payment:return-secondary")).getText());
            assertEquals(0.0, panel.executePayment().getTotal());
        });
    }

    @Test
    void combinesBothEditorsShowsSplitReturnAndPersistsNormalizedPayment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPaymentNotifier notifier = mock(JPaymentNotifier.class);
            AtomicReference<BigDecimal> remaining = new AtomicReference<>();
            var panel = new DualCurrencyCashPanel(notifier, new DualCurrencyTotal("USD", "LBP", "90000"), remaining::set);
            panel.activate(null, 13.20, null);
            verify(notifier).setStatus(true, true);
            var primary = (JEditorDoublePositive) find(panel, "kriolos:payment:given-primary");
            var secondary = (JEditorDoublePositive) find(panel, "kriolos:payment:given-secondary");
            primary.setDoubleValue(10.0);
            assertEquals(3.20, remaining.get().doubleValue());
            secondary.setDoubleValue(900000.0);
            assertEquals(0, remaining.get().signum());
            verify(notifier, atLeastOnce()).setStatus(true, true);
            assertTrue(((JLabel) find(panel, "kriolos:payment:return-primary")).getText().startsWith("USD "));
            assertTrue(((JLabel) find(panel, "kriolos:payment:return-secondary")).getText().startsWith("LBP "));
            PaymentInfo payment = panel.executePayment();
            assertEquals(13.20, payment.getTotal());
            assertEquals(20.0, payment.getPaid());
            assertEquals(20.0, payment.getTendered());
            assertEquals(6.80, payment.getChange(), 0.0000001);
            // A partial cash payment must only apply what was entered, not the full bill.
            panel.activate(null, 13.20, null);
            primary.setDoubleValue(5.0);
            assertEquals(5.0, panel.executePayment().getTotal());
            assertEquals(8.20, remaining.get().doubleValue());
            // Reopening defaults to exact primary tender, without retaining secondary cash.
            panel.activate(null, 13.20, null);
            assertEquals(13.20, primary.getValue());
            assertNull(secondary.getValue());
            assertEquals(13.20, panel.executePayment().getPaid());
            // Backspace on the actual keypad input clears only its selected editor.
            var keyboard = findTextField(panel);
            var backspace = new java.awt.event.KeyEvent(keyboard, java.awt.event.KeyEvent.KEY_TYPED,
                    System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_UNDEFINED, '\b');
            for (var listener : keyboard.getKeyListeners()) listener.keyTyped(backspace);
            assertNull(primary.getValue());
            assertNull(panel.executePayment());
            assertEquals(13.20, remaining.get().doubleValue());
            secondary.setDoubleValue(1188000.0);
            assertEquals(13.20, panel.executePayment().getTotal());
            assertEquals(0, remaining.get().signum());
            secondary.activate();
            for (var listener : keyboard.getKeyListeners()) listener.keyTyped(backspace);
            assertNull(secondary.getValue());
            assertNull(panel.executePayment());
            panel.activate(null, 8.20, null);
            assertEquals(8.20, primary.getValue());
            primary.typeChar('5');
            assertEquals(5.0, primary.getValue());
        });
    }

    @Test
    void summaryAndCashFactoryUseTheConfiguredCurrenciesAndWritePreview() throws Exception {
        var config = AppConfig.getInstance();
        String primary = config.getProperty("currency.primary");
        String secondary = config.getProperty("currency.secondary");
        String rate = config.getProperty("currency.secondary.rate");
        try {
            config.setProperty("currency.primary", "USD");
            config.setProperty("currency.secondary", "LBP");
            config.setProperty("currency.secondary.rate", "90000");
            SwingUtilities.invokeAndWait(() -> {
                var checkout = new JPaymentSelectReceipt();
                checkout.configureCurrencySummary();
                checkout.updateCurrencySummary(13.20, 3.20);
                assertTrue(((JLabel) find(checkout, "kriolos:payment:lbl-total")).getText().startsWith("USD "));
                assertTrue(((JLabel) find(checkout, "kriolos:payment:lbl-total-secondary")).getText().startsWith("LBP "));
                assertTrue(checkout.new JPaymentCashCreator().createJPayment() instanceof DualCurrencyCashPanel);
                var cash = new DualCurrencyCashPanel(mock(JPaymentNotifier.class),
                        new DualCurrencyTotal("USD", "LBP", "90000"),
                        remaining -> checkout.updateCurrencySummary(13.20, remaining.doubleValue()));
                cash.activate(null, 13.20, null);
                ((JEditorDoublePositive) find(cash, "kriolos:payment:given-primary")).setDoubleValue(10.0);
                ((JEditorDoublePositive) find(cash, "kriolos:payment:given-secondary")).setDoubleValue(900000.0);
                checkout.remove(((BorderLayout) checkout.getLayout()).getLayoutComponent(BorderLayout.CENTER));
                checkout.add(cash, BorderLayout.CENTER);
                checkout.setSize(900, 600);
                layout(checkout);
                BufferedImage image = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    checkout.printAll(graphics);
                    ImageIO.write(image, "png", Path.of("target", "dual-currency-payment-preview.png").toFile());
                } catch (Exception ex) { throw new RuntimeException(ex); }
                finally { graphics.dispose(); }
                config.setProperty("currency.secondary", "");
                checkout.configureCurrencySummary();
                assertNull(PaymentCurrency.configured());
            });
        } finally {
            config.setProperty("currency.primary", primary);
            config.setProperty("currency.secondary", secondary);
            config.setProperty("currency.secondary.rate", rate);
        }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }

    private static javax.swing.JTextField findTextField(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof javax.swing.JTextField field) return field;
            if (child instanceof Container container) {
                var found = findTextField(container);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
