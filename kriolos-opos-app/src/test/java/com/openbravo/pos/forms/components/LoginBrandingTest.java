package com.openbravo.pos.forms.components;

import com.openbravo.basic.AppProperties;
import com.openbravo.pos.admin.SecurityService;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.resources.ResourceService;
import com.openbravo.pos.spi.config.BootstrapConfig;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginBrandingTest {
    @Test
    void missingOrInvalidImageKeepsWelcomeCustomizationAndEscapesPlainText() throws Exception {
        ResourceService resources = mock(ResourceService.class);
        when(resources.getResourceAsBinary("Login.Logo")).thenReturn(new byte[]{1, 2, 3});
        when(resources.getResourceAsText("Login.Welcome")).thenReturn("Welcome <shop>\nChoose a user");
        LoginBranding branding = LoginBranding.load(resources);
        assertNull(branding.logo());
        assertEquals("<html>Welcome &lt;shop&gt;<br>Choose a user</html>",
                LoginBranding.displayText(branding.welcome()));
        assertTrue(LoginBranding.load(null).welcome().contains("License: GPL v3"));
        assertEquals("<html><b>Shop</b></html>",
                LoginBranding.displayText("<html><b>Shop</b></html>"));
    }

    @Test
    void refreshReadsSavedContentAgainAndFitsLogoWithoutChangingLoginControls() throws Exception {
        ResourceService resources = mock(ResourceService.class);
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB), "png", output);
        when(resources.getResourceAsBinary("Login.Logo")).thenReturn(output.toByteArray());
        when(resources.getResourceAsText("Login.Welcome")).thenReturn("<html>Our shop</html>");
        when(resources.getResourceAsText("Login.Header")).thenReturn("Select cashier");
        when(resources.getResourceAsText("Login.Vendor")).thenReturn("Our brand");
        AppView app = mock(AppView.class);
        BootstrapConfig bootstrap = mock(BootstrapConfig.class);
        when(bootstrap.getDatabaseConfigs()).thenReturn(List.of());
        when(app.getBootstrapConfig()).thenReturn(bootstrap);
        var holder = new AtomicReference<AuthenticationPanel>();
        SwingUtilities.invokeAndWait(() -> holder.set(new AuthenticationPanel(app,
                mock(SecurityService.class), mock(AppProperties.class), user -> {})));
        AuthenticationPanel panel = holder.get();
        CountDownLatch applied = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            JLabel welcome = label(panel, "kriolos:copyright:lbl-content");
            welcome.addPropertyChangeListener("text", event -> applied.countDown());
            panel.refreshBranding(resources);
        });
        assertTrue(applied.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("<html>Our shop</html>", label(panel, "kriolos:copyright:lbl-content").getText());
            assertEquals("<html>Select cashier</html>", label(panel, "kriolos:auth:lbl-login-header").getText());
            ImageIcon logo = (ImageIcon) label(panel, "kriolos:auth:lbl-vendor-image").getIcon();
            assertEquals(240, logo.getIconWidth());
            assertEquals(120, logo.getIconHeight());
            assertNotNull(panel.getBtnConfigure());
        });
        when(resources.getResourceAsText("Login.Welcome")).thenReturn("<html>Updated welcome</html>");
        CountDownLatch refreshed = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            label(panel, "kriolos:copyright:lbl-content").addPropertyChangeListener("text", event -> refreshed.countDown());
            panel.refreshBranding(resources);
        });
        assertTrue(refreshed.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> assertEquals("<html>Updated welcome</html>",
                label(panel, "kriolos:copyright:lbl-content").getText()));
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
