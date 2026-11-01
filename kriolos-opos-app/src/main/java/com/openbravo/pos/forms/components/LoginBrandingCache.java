package com.openbravo.pos.forms.components;

import com.openbravo.pos.spi.config.BootstrapConfig.DatabaseConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import javax.imageio.ImageIO;

/** Last known branding per store; the connected database remains authoritative. */
final class LoginBrandingCache {
    private final Path directory;

    LoginBrandingCache(Path directory) { this.directory = directory; }

    LoginBranding load(DatabaseConfig database) throws IOException {
        if (database == null) return null;
        Path file = file(database);
        if (!Files.isRegularFile(file)) return null;
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) { properties.load(input); }
        if (!"1".equals(properties.getProperty("version"))) return null;
        try {
            String png = properties.getProperty("logo", "");
            var image = png.isEmpty() ? null : ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(png)));
            return new LoginBranding(image, properties.getProperty("welcome", ""),
                    properties.getProperty("header", ""), properties.getProperty("vendor", ""));
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid cached login image", ex); }
    }

    void save(DatabaseConfig database, LoginBranding branding) throws IOException {
        if (database == null) return;
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("welcome", branding.welcome());
        properties.setProperty("header", branding.header());
        properties.setProperty("vendor", branding.vendor());
        if (branding.logo() != null) {
            var png = new ByteArrayOutputStream();
            ImageIO.write(branding.logo(), "png", png);
            properties.setProperty("logo", Base64.getEncoder().encodeToString(png.toByteArray()));
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "branding-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { properties.store(output, "Login branding cache"); }
            Files.move(temporary, file(database), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path file(DatabaseConfig database) {
        try {
            String identity = database.name() + "\n" + database.url() + "\n" + database.username();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".properties");
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
