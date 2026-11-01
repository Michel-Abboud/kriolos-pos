package com.openbravo.pos.forms.components;

import com.openbravo.basic.AppProperties;
import com.openbravo.pos.admin.SecurityService;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.spi.config.BootstrapConfig;
import com.openbravo.pos.spi.config.BootstrapConfig.DatabaseConfig;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginBrandingCacheTest {
    @TempDir Path directory;

    @Test
    void keepsStoresSeparateAndRefreshesCachedImagesAndUnicodeText() throws Exception {
        var cache = new LoginBrandingCache(directory);
        var store = new DatabaseConfig("Shop", "jdbc:sqlite:shop.db", "owner", "secret");
        var other = new DatabaseConfig("Shop", "jdbc:sqlite:other.db", "owner", "secret");
        assertNull(cache.load(store));
        cache.save(store, new LoginBranding(new BufferedImage(7, 9, BufferedImage.TYPE_INT_RGB),
                "<html>أهلاً Café</html>", "Our users", "Our logo"));
        assertEquals("<html>أهلاً Café</html>", cache.load(store).welcome());
        assertEquals(7, cache.load(store).logo().getWidth());
        assertNull(cache.load(other));
        cache.save(store, new LoginBranding(null, "Updated", "", ""));
        assertEquals("Updated", cache.load(store).welcome());
        assertNull(cache.load(store).logo());
        try (var files = Files.list(directory)) {
            Path file = files.findFirst().orElseThrow();
            assertFalse(Files.readString(file).contains("secret"));
        }
    }

    @Test
    void firstFrameIsNeutralAndLoadsStoreCacheBeforeDatabaseIsAvailable() throws Exception {
        var cache = new LoginBrandingCache(directory);
        var store = new DatabaseConfig("Shop", "jdbc:sqlite:shop.db", "owner", "secret");
        cache.save(store, new LoginBranding(null, "<html>My store</html>", "Our users", ""));
        AppView app = mock(AppView.class);
        BootstrapConfig bootstrap = mock(BootstrapConfig.class);
        when(bootstrap.getDatabaseConfigs()).thenReturn(List.of(store));
        when(app.getBootstrapConfig()).thenReturn(bootstrap);
        var holder = new AtomicReference<AuthenticationPanel>();
        CountDownLatch applied = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new AuthenticationPanel(app, mock(SecurityService.class), mock(AppProperties.class), user -> {}, cache);
            holder.set(panel);
            JLabel content = label(panel, "kriolos:copyright:lbl-content");
            assertFalse(content.getText().contains("KriolOS POS"));
            assertNull(label(panel, "kriolos:auth:lbl-vendor-image").getIcon());
            content.addPropertyChangeListener("text", event -> applied.countDown());
            panel.refreshBranding(null);
        });
        assertTrue(applied.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> assertEquals("<html>My store</html>",
                label(holder.get(), "kriolos:copyright:lbl-content").getText()));
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
