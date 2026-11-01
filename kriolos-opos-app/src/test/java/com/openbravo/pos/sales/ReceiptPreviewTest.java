package com.openbravo.pos.sales;

import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.panels.JPanelPrinter;
import com.openbravo.pos.printer.DefaultDeviceTicket;
import com.openbravo.pos.printer.screen.JTicketContainer;
import com.openbravo.pos.spi.hardware.DeviceTicket;
import com.openbravo.pos.spi.hardware.printer.DevicePrinter;
import com.openbravo.pos.admin.SystemService;
import com.openbravo.pos.resources.ResourceService;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketParser;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptPreviewTest {
    private static final String RECEIPT = "<output><ticket printer=\"2\"><line><text>Receipt ${ticket.getTicketId()}</text></line></ticket>"
            + "<display><line><text>Customer display</text></line></display><opendrawer/><play>unused.wav</play>"
            + "<fiscalreceipt/><fiscalxreport/><fiscalzreport/></output>";

    @Test
    void previewOnlyRendersReceiptAndNeverOperatesHardwareOrLogsDrawer() throws Exception {
        DeviceTicket devices = mock(DeviceTicket.class);
        DevicePrinter preview = mock(DevicePrinter.class);
        when(devices.getReceiptPreviewPrinter()).thenReturn(preview);
        SystemService system = mock(SystemService.class);
        var ticket = new TicketInfo();
        new TicketParser(devices, system).previewTicket(RECEIPT, ticket);
        verify(preview).reset();
        verify(preview).beginReceipt();
        verify(preview).printText(0, "Receipt ${ticket.getTicketId()}");
        verify(preview).endReceipt();
        verify(devices).getReceiptPreviewPrinter();
        verifyNoMoreInteractions(devices);
        verifyNoInteractions(system);
        verify(preview, never()).openDrawer();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void checkoutPreviewsEvaluatedFullReceiptAndDispatchesOnlySelectedTemplate(boolean print) throws Exception {
        AppView app = mock(AppView.class);
        TicketParser parser = mock(TicketParser.class);
        String full = "<output><ticket><line><text>Receipt ${ticket.getTicketId()}</text></line></ticket></output>";
        String off = "<output><display><line><text>Paid</text></line></display></output>";
        var coordinator = new SalesPeripheralCoordinator(app, (ResourceService) null, parser,
                key -> null, key -> "Printer.Ticket".equals(key) ? full : off);
        var ticket = new TicketInfo();
        ticket.setTicketId(42);
        assertTrue(coordinator.printTicket(print ? "Printer.Ticket" : "Printer.Ticket2",
                ticket, null, null, false, "0", null));
        String rendered = full.replace("${ticket.getTicketId()}", "42");
        verify(parser).previewTicket(rendered, ticket);
        verify(parser).printTicket(print ? rendered : off, ticket);
        verifyNoMoreInteractions(parser);
    }

    @Test
    void dedicatedPreviewKeepsOnlyLatestReceiptAndIsAvailableOnPrintersScreen() throws Exception {
        var devices = new DefaultDeviceTicket();
        var parser = new TicketParser(devices, (SystemService) null);
        var ticket = new TicketInfo();
        // Background rendering must enqueue reset/add in order on the Swing thread.
        parser.previewTicket("<ticket><line><text>Old receipt</text></line></ticket>", ticket);
        parser.previewTicket("<ticket><line><text>Latest receipt USD 3.30 / LBP 297,000</text></line></ticket>", ticket);
        SwingUtilities.invokeAndWait(() -> {
            var preview = (Container) devices.getReceiptPreviewPrinter();
            var receipts = findContainer(preview);
            assertNotNull(receipts);
            assertEquals(1, receipts.getComponentCount());
            AppView app = mock(AppView.class);
            when(app.getDeviceTicket()).thenReturn(devices);
            var screen = new JPanelPrinter(app);
            assertTrue(SwingUtilities.isDescendingFrom(preview, screen));
            screen.setSize(1100, 750);
            layout(screen);
            var image = new BufferedImage(1100, 750, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                screen.printAll(graphics);
                ImageIO.write(image, "png", Path.of("target", "last-receipt-preview.png").toFile());
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { graphics.dispose(); }
        });
    }

    private static JTicketContainer findContainer(Container parent) {
        if (parent instanceof JTicketContainer tickets) return tickets;
        for (Component child : parent.getComponents()) {
            if (child instanceof Container nested) {
                var found = findContainer(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }
}
