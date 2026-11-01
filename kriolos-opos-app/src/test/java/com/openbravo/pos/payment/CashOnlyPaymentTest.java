package com.openbravo.pos.payment;

import com.openbravo.basic.AppProperties;
import com.openbravo.pos.config.AppConfig;
import com.openbravo.pos.config.JPanelConfigPayment;
import com.openbravo.pos.panels.AppView;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashOnlyPaymentTest {
    @TempDir Path directory;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"true", "false"})
    void filtersSalesAndRefundsWithoutBypassingRolePermissions(String configured) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppView app = mock(AppView.class);
            AppProperties properties = mock(AppProperties.class);
            when(app.getProperties()).thenReturn(properties);
            when(properties.getProperty("payment.cashonly")).thenReturn(configured);
            when(app.hasPermission(anyString())).thenReturn(true);
            var panel = new JPaymentSelectReceipt();
            panel.init(app, mock(PaymentService.class));
            var tabs = (JTabbedPane) find(panel, "kriolos:payment:tabs");
            String[] keys = {"payment.cash", "payment.cheque", "payment.voucher", "payment.magcard",
                    "payment.free", "payment.debt", "payment.bank", "payment.slip",
                    "refund.cash", "refund.cheque", "refund.voucher", "refund.magcard"};
            for (String key : keys) {
                var creator = mock(JPaymentSelect.JPaymentCreator.class);
                when(creator.getKey()).thenReturn(key);
                when(creator.getLabelKey()).thenReturn("tab.cash");
                when(creator.getIconKey()).thenReturn("/com/openbravo/images/cash.png");
                when(creator.createJPayment()).thenReturn(new TestPayment());
                panel.addTabPayment(creator);
                boolean visible = "false".equals(configured) || key.equals("payment.cash") || key.equals("refund.cash");
                verify(creator, times(visible ? 1 : 0)).createJPayment();
            }
            int expected = "false".equals(configured) ? keys.length : 2;
            assertEquals(expected, tabs.getTabCount());
            when(app.hasPermission("payment.cash")).thenReturn(false);
            var denied = mock(JPaymentSelect.JPaymentCreator.class);
            when(denied.getKey()).thenReturn("payment.cash");
            panel.addTabPayment(denied);
            verify(denied, never()).createJPayment();
            assertEquals(expected, tabs.getTabCount());
        });
    }

    @Test
    void configurationDefaultsToCashOnlyAndRetainsExplicitOptOut() throws Exception {
        var config = new AppConfig(directory.resolve("settings.properties").toFile());
        config.setProperty("payment.cashonly", null);
        config.setProperty("payment.gateway", "external");
        SwingUtilities.invokeAndWait(() -> {
            var panel = new JPanelConfigPayment();
            panel.loadProperties(config);
            var checkbox = (JCheckBox) find(panel, "kriolos:config:payment-cash-only");
            assertNotNull(checkbox);
            assertTrue(checkbox.isSelected());
            assertFalse(panel.hasChanged());
            checkbox.doClick();
            assertTrue(panel.hasChanged());
            panel.saveProperties(config);
            assertEquals("false", config.getProperty("payment.cashonly"));
            assertFalse(panel.hasChanged());
            var reopened = new JPanelConfigPayment();
            reopened.loadProperties(config);
            assertFalse(((JCheckBox) find(reopened, "kriolos:config:payment-cash-only")).isSelected());
            checkbox.doClick();
            panel.saveProperties(config);
            assertEquals("true", config.getProperty("payment.cashonly"));
        });
    }

    private static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class TestPayment extends JPanel implements JPaymentInterface {
        @Override public void activate(com.openbravo.pos.customers.CustomerInfoExt customer, double total, String transaction) { }
        @Override public PaymentInfo executePayment() { return null; }
        @Override public Component getComponent() { return this; }
    }
}
