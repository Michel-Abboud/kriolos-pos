package com.openbravo.pos.forms.components;

import com.openbravo.basic.AppLocal;
import com.openbravo.pos.resources.ResourceService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Store-specific login content, read through the application's resource port. */
record LoginBranding(BufferedImage logo, String welcome, String header, String vendor) {

    static final String WELCOME_PATH = "/com/openbravo/pos/templates/Login.Welcome.html";

    static LoginBranding load(ResourceService resources) throws IOException {
        BufferedImage logo = null;
        if (resources != null) {
            byte[] bytes = resources.getResourceAsBinary("Login.Logo");
            if (bytes != null && bytes.length > 0) {
                // A bad image must not prevent login or discard valid text customizations.
                try {
                    logo = ImageIO.read(new ByteArrayInputStream(bytes));
                } catch (IOException ex) {
                    logo = null;
                }
            }
        }
        return new LoginBranding(logo,
                text(resources, "Login.Welcome", defaultWelcome()),
                text(resources, "Login.Header", AppLocal.getIntString("JAuthPanel.m_LoginLabel.text")),
                text(resources, "Login.Vendor", AppLocal.getIntString("JAuthPanel.m_vendorImageLabel.text")));
    }

    static String defaultWelcome() throws IOException {
        try (var input = LoginBranding.class.getResourceAsStream(WELCOME_PATH)) {
            if (input == null) throw new IOException("Missing bundled login welcome template");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String text(ResourceService resources, String name, String fallback) {
        String value = resources == null ? null : resources.getResourceAsText(name);
        return value == null ? fallback : value.strip();
    }

    /** JLabel understands Swing HTML; escape plain text so newlines also display. */
    static String displayText(String value) {
        if (value == null || value.isEmpty() || value.stripLeading().regionMatches(true, 0, "<html>", 0, 6)) {
            return value;
        }
        return "<html>" + value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\r\n", "\n").replace("\n", "<br>") + "</html>";
    }
}
