package com.openbravo.pos.sales;

import com.openbravo.basic.AppProperties;
import com.openbravo.pos.admin.AppUser;
import com.openbravo.pos.admin.AppUserView;
import com.openbravo.pos.catalog.CatalogService;
import com.openbravo.pos.customers.CustomerService;
import com.openbravo.pos.inventory.InventoryService;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.resources.ResourceService;
import com.openbravo.pos.ticket.TicketLineInfo;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesEnterKeyTest {
    @Test
    void emptyKeyboardEnterOpensCheckoutOnceWhileBarcodeEnterKeepsScanning() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppView app = mock(AppView.class);
                AppProperties properties = mock(AppProperties.class);
                when(app.getProperties()).thenReturn(properties);
                when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
                when(properties.getProperty("machine.ticketsbag")).thenReturn("standard");
                AppUserView userView = mock(AppUserView.class);
                when(app.getAppUserView()).thenReturn(userView);
                when(userView.getUser()).thenReturn(new AppUser("test", "Test cashier", null, null, "role", null));
                when(app.hasPermission("sales.Total")).thenReturn(true);
                when(app.getBean(TaxService.class)).thenReturn(mock(TaxService.class));
                when(app.getBean(TicketLifecycleService.class)).thenReturn(mock(TicketLifecycleService.class));
                when(app.getBean(InventoryService.class)).thenReturn(mock(InventoryService.class));
                when(app.getBean(CustomerService.class)).thenReturn(mock(CustomerService.class));
                when(app.getBean(CatalogService.class)).thenReturn(mock(CatalogService.class));
                when(app.getBean(SharedTicketService.class)).thenReturn(mock(SharedTicketService.class));
                when(app.getBean(AuditService.class)).thenReturn(mock(AuditService.class));
                ResourceService resources = mock(ResourceService.class);
                when(app.getBean(ResourceService.class)).thenReturn(resources);
                try (var xml = getClass().getResourceAsStream("/com/openbravo/pos/templates/Ticket.Line.xml")) {
                    assertNotNull(xml);
                    when(resources.getResourceAsXML("Ticket.Line")).thenReturn(new String(xml.readAllBytes(), StandardCharsets.UTF_8));
                }
                when(resources.getResourceAsXML("Ticket.Buttons")).thenReturn("<configuration/>");
                JPanelTicketSalesClassic sales = new JPanelTicketSalesClassic(app);
                sales.activate();
                var ticket = sales.getActiveTicket();
                ticket.addLine(new TicketLineInfo("Test product", "tax", 1.0, 10.0, null));
                sales.setActiveTicket(ticket, null);
                SalesPaymentCoordinator checkout = mock(SalesPaymentCoordinator.class);
                replace(sales, "salesPaymentCoordinator", checkout);
                sales.getInputPane().getKeyFactory().postActionEvent();
                assertEquals(1, mockingDetails(checkout).getInvocations().stream()
                        .filter(call -> call.getMethod().getName().equals("processPaymentAndClose")).count());

                clearInvocations(checkout);
                SalesBarcodeScanCoordinator scanner = mock(SalesBarcodeScanCoordinator.class);
                replace(sales, "salesBarcodeScanCoordinator", scanner);
                var field = sales.getInputPane().getKeyFactory();
                for (char character : "2000000000015".toCharArray()) {
                    KeyEvent event = new KeyEvent(field, KeyEvent.KEY_TYPED, 0, 0,
                            KeyEvent.VK_UNDEFINED, character);
                    for (var listener : field.getKeyListeners()) listener.keyTyped(event);
                }
                field.postActionEvent();
                verifyNoInteractions(checkout);
                var calls = mockingDetails(scanner).getInvocations();
                assertEquals(1, calls.size());
                assertEquals("2000000000015", calls.iterator().next().getArgument(1));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void replace(JPanelTicket sales, String name, Object value) throws Exception {
        Field field = JPanelTicket.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(sales, value);
    }
}
