package com.openbravo.pos.inventory;

import com.openbravo.data.user.DirtyManager;
import com.openbravo.pos.catalog.CatalogService;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.sales.TaxService;
import com.openbravo.pos.suppliers.SupplierService;
import com.openbravo.pos.ticket.ProductInfoExt;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductsEditorBarcodeTest {
    @Test
    void generateButtonChecksCatalogServiceAndSkipsExistingBarcodes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppView app = mock(AppView.class);
                CatalogService catalog = mock(CatalogService.class);
                when(app.getBean(CatalogService.class)).thenReturn(catalog);
                when(app.getBean(TaxService.class)).thenReturn(mock(TaxService.class));
                when(app.getBean(StockService.class)).thenReturn(mock(StockService.class));
                when(app.getBean(SupplierService.class)).thenReturn(mock(SupplierService.class));
                ProductsEditor editor = new ProductsEditor(app, new DirtyManager());
                editor.activate();
                JButton generate = (JButton) find(editor, "kriolos:products:generate-barcode");
                JTextField barcode = (JTextField) find(editor, "kriolos:products:barcode");
                JComboBox<?> type = (JComboBox<?>) find(editor, "kriolos:products:barcode-type");
                assertNotNull(generate);
                assertFalse(generate.isEnabled(), "no product is being edited yet");
                editor.writeValueInsert();
                assertTrue(generate.isEnabled());
                String existing = Ean13BarcodeGenerator.appendCheckDigit("200000000001");
                String available = Ean13BarcodeGenerator.appendCheckDigit("200000000002");
                when(catalog.getProductInfoByCode(existing)).thenReturn(new ProductInfoExt());
                try (var generator = mockStatic(Ean13BarcodeGenerator.class, CALLS_REAL_METHODS)) {
                    generator.when(Ean13BarcodeGenerator::generate).thenReturn(existing, available);
                    generate.doClick();
                }
                assertEquals(available, barcode.getText());
                assertTrue(Ean13BarcodeGenerator.isValid(barcode.getText()));
                assertEquals("EAN-13", type.getSelectedItem());
                verify(catalog).getProductInfoByCode(existing);
                verify(catalog).getProductInfoByCode(available);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static Component find(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
