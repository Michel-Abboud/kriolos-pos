package com.openbravo.pos.sales;

import com.openbravo.basic.AppProperties;
import com.openbravo.pos.admin.AppUser;
import com.openbravo.pos.admin.AppUserView;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.payment.JPaymentSelect;
import com.openbravo.pos.payment.JPaymentSelectReceipt;
import com.openbravo.pos.sales.shared.JTicketsBagShared;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesWorkflowRegressionTest {
    @Test
    void deletesSaleWithOverrideDisabledAndRemovesSuspendedCopy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppView app = mock(AppView.class);
                AppProperties properties = mock(AppProperties.class);
                when(app.getProperties()).thenReturn(properties);
                when(properties.getProperty("override.check")).thenReturn("false");
                when(app.hasPermission(anyString())).thenReturn(true);
                SharedTicketService shared = mock(SharedTicketService.class);
                when(app.getBean(SharedTicketService.class)).thenReturn(shared);
                when(shared.getSharedTicketList()).thenReturn(List.of());
                AuditService audit = mock(AuditService.class);
                when(app.getBean(AuditService.class)).thenReturn(audit);
                AppUserView userView = mock(AppUserView.class);
                when(app.getAppUserView()).thenReturn(userView);
                when(userView.getUser()).thenReturn(new AppUser("test", "Cashier", null, null, "role", null));
                TicketInfo original = ticketWithLine();
                AtomicReference<TicketInfo> active = new AtomicReference<>(original);
                TicketsEditor editor = new TicketsEditor() {
                    public TicketInfo getActiveTicket() { return active.get(); }
                    public void setActiveTicket(TicketInfo ticket, String ext) { active.set(ticket); }
                };
                JTicketsBagShared bag = new JTicketsBagShared(app, editor);
                JButton delete = findDeleteButton(bag);
                assertNotNull(delete);
                delete.doClick();
                assertNotSame(original, active.get());
                assertEquals(0, active.get().getLinesCount());
                verify(shared).deleteSharedTicket(original.getId());
                verify(audit).addTicketDeleted("Cashier");

                when(app.hasPermission("sales.DeleteTicket")).thenReturn(false);
                TicketInfo replacement = ticketWithLine();
                active.set(replacement);
                delete.doClick();
                assertSame(replacement, active.get(), "permission denial must leave the sale intact");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    void lineDeletionIsImmediateAndStillAudited() {
        AppView app = mock(AppView.class);
        when(app.hasPermission("sales.DeleteLines")).thenReturn(true);
        AppUserView userView = mock(AppUserView.class);
        when(app.getAppUserView()).thenReturn(userView);
        when(userView.getUser()).thenReturn(new AppUser("test", "Cashier", null, null, "role", null));
        AuditService audit = mock(AuditService.class);
        SalesService service = new SalesServiceImpl(new TaxesLogic(List.of()));
        TicketInfo ticket = ticketWithLine();
        TicketLineController controller = new TicketLineController(app, service, audit);
        assertTrue(controller.deleteLineWithAudit(new JPanel(), ticket, 0));
        assertEquals(0, ticket.getLinesCount());
        verify(audit).addTicketLineRemoved("Cashier", "Void", "product", "Test product", 1.0);
    }

    @Test
    void checkoutDefaultsHonorPrinterOffAndAllowManualOverride() {
        AppView app = mock(AppView.class);
        AppProperties properties = mock(AppProperties.class);
        when(app.getProperties()).thenReturn(properties);
        when(properties.getProperty("till.receiptprintoff")).thenReturn("true");
        JPaymentSelect payment = JPaymentSelectReceipt.getDialog(new JPanel());
        payment.init(app, null);
        payment.setDefaultPrintSelected(true);
        assertFalse(payment.isPrintSelected());
        payment.setPrintSelected(true);
        assertTrue(payment.isPrintSelected(), "cashier can print this receipt explicitly");
        payment.setDefaultPrintSelected(true);
        assertFalse(payment.isPrintSelected(), "next checkout must restore the configured default");
        when(properties.getProperty("till.receiptprintoff")).thenReturn("false");
        payment.setDefaultPrintSelected(false);
        assertFalse(payment.isPrintSelected(), "Ticket.Buttons printselected=false must also be respected");
        payment.setDefaultPrintSelected(true);
        assertTrue(payment.isPrintSelected());
    }

    private static TicketInfo ticketWithLine() {
        TicketInfo ticket = new TicketInfo();
        ticket.addLine(new TicketLineInfo("product", "Test product", "tax", 1.0, 10.0, null));
        return ticket;
    }

    private static JButton findDeleteButton(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && "kriolos:sales:delete-sale".equals(button.getName())) {
                return button;
            }
            if (component instanceof Container container) {
                JButton found = findDeleteButton(container);
                if (found != null) return found;
            }
        }
        return null;
    }
}
