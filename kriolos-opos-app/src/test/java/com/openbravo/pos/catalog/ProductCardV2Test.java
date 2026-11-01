package com.openbravo.pos.catalog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class ProductCardV2Test {
    @ParameterizedTest
    @CsvSource({
        "<html><center>Milk &amp; Honey,MH,Milk & Honey",
        "<html><center><b>Tea</b><br>Green,TG,Tea",
        "<HTML><CENTER>بطاريات Camelion 9V,بC,بطاريات Camelion 9V",
        "<html><center>&lt;Milk&gt;,MI,<Milk>"
    })
    void rendersSavedButtonHtmlAndExtractsVisibleInitials(String html, String initials, String visible) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var card = new ProductCardV2(new CatalogItem(html), null);
            JLabel title = label(card, "kriolos:catalog:card-title");
            assertEquals(html, title.getText());
            assertTrue(renderedText(title).contains(visible));
            assertFalse(renderedText(title).toLowerCase().contains("<html>"));
            assertEquals(initials, label(card, "kriolos:catalog:card-image").getText());
        });
    }

    @Test
    void plainNamesStayLiteralAndImagesPricesAndSelectionStillWork() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CatalogItem item = new CatalogItem("Milk <Large> & Honey");
            item.setPrice("$1.50");
            item.setImage(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
            AtomicBoolean selected = new AtomicBoolean();
            var card = new ProductCardV2(item, event -> selected.set(true));
            JLabel title = label(card, "kriolos:catalog:card-title");
            assertTrue(renderedText(title).contains("Milk <Large> & Honey"));
            assertNotNull(label(card, "kriolos:catalog:card-image").getIcon());
            assertTrue(label(card, "kriolos:catalog:card-image").getText().isEmpty());
            JPanel footer = (JPanel) ((BorderLayout) card.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
            assertEquals("$1.50", ((JLabel) ((BorderLayout) footer.getLayout()).getLayoutComponent(BorderLayout.SOUTH)).getText());
            card.doClick();
            assertTrue(selected.get());
            assertSame(item, card.getCatalogItem());
            var compact = new ProductCardV2(new CatalogItem("<html><b>Tea</b>"), ProductCardV2.ViewMode.COMPACT, null);
            assertEquals("<html><b>Tea</b>", compact.getText());
        });
    }

    @Test
    void blankHtmlUsesFallbackAndUnicodeInitialsRemainWholeCharacters() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var blank = new ProductCardV2(new CatalogItem("<html><center><br>"), null);
            assertEquals("POS", label(blank, "kriolos:catalog:card-image").getText());
            var unicode = new ProductCardV2(new CatalogItem("𐐀 Tea"), null);
            assertEquals("𐐀T", label(unicode, "kriolos:catalog:card-image").getText());
        });
    }

    @Test
    void writesCatalogPreview() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel preview = new JPanel(new GridLayout(1, 3, 10, 0));
            for (String title : new String[]{"<html><center>Milk &amp; Honey", "<html><center>بطاريات<br>Camelion 9V", "Tea <Large>"}) {
                CatalogItem item = new CatalogItem(title);
                item.setPrice("$1.50");
                preview.add(new ProductCardV2(item, null));
            }
            preview.setSize(430, 160);
            layout(preview);
            BufferedImage image = new BufferedImage(430, 160, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                preview.printAll(graphics);
                ImageIO.write(image, "png", Path.of("target", "catalog-cards-preview.png").toFile());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally { graphics.dispose(); }
        });
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) layout(container);
        }
    }

    private static String renderedText(JLabel label) {
        try {
            var document = ((View) label.getClientProperty(BasicHTML.propertyKey)).getDocument();
            return document.getText(0, document.getLength());
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private static JLabel label(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName()) && child instanceof JLabel label) return label;
            if (child instanceof Container container) {
                JLabel found = label(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
