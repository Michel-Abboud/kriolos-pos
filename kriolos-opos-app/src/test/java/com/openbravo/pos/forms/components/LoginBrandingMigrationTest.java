package com.openbravo.pos.forms.components;

import com.openbravo.data.loader.Session;
import com.openbravo.data.user.DefaultSaveProvider;
import com.openbravo.pos.admin.DataLogicSystem;
import com.openbravo.pos.data.DBMigrator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class LoginBrandingMigrationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "sqlite"})
    void seedsEditableDefaultsAndPreservesCustomizationsOnUpgradeAndRestart(String engine) throws Exception {
        // Other bundled drivers can interrupt JDBC auto-discovery on JDK 17.
        Class.forName(engine.equals("sqlite") ? "org.sqlite.JDBC" : "org.hsqldb.jdbc.JDBCDriver");
        String url = engine.equals("sqlite") ? "jdbc:sqlite:" + directory.resolve("store.db")
                : "jdbc:hsqldb:file:" + directory.resolve("store");
        Session session = new Session(url, engine.equals("hsqldb") ? "SA" : null, "");
        try {
            DBMigrator.execDBMigration(session);
            DataLogicSystem resources = new DataLogicSystem();
            resources.init(session);
            LoginBranding defaults = LoginBranding.load(resources);
            assertNotNull(defaults.logo());
            assertTrue(defaults.welcome().contains("License: GPL v3"));
            assertEquals("Login", defaults.header());

            // Use the same table save provider as the Resources page.
            var save = new DefaultSaveProvider(resources.getTableResources());
            assertEquals(1, save.updateData(new Object[]{"login-branding-welcome", "Login.Welcome", 0,
                    "<html>Welcome to our store</html>".getBytes(StandardCharsets.UTF_8)}));
            var image = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(7, 9, BufferedImage.TYPE_INT_RGB), "png", image);
            assertEquals(1, save.updateData(new Object[]{"login-branding-logo", "Login.Logo", 1, image.toByteArray()}));
            assertEquals("<html>Welcome to our store</html>", LoginBranding.load(resources).welcome());
            // Simulate upgrading an existing store with a preexisting customized name.
            try (var sql = session.getConnection().createStatement()) {
                sql.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID LIKE '20261101-login-%'");
            }
            DBMigrator.execDBMigration(session);
            assertEquals("<html>Welcome to our store</html>", LoginBranding.load(resources).welcome());
            assertEquals(7, LoginBranding.load(resources).logo().getWidth());
        } finally {
            shutdown(session, engine);
        }
        Session reopened = new Session(url, engine.equals("hsqldb") ? "SA" : null, "");
        try {
            DBMigrator.execDBMigration(reopened);
            DataLogicSystem resources = new DataLogicSystem();
            resources.init(reopened);
            assertEquals("<html>Welcome to our store</html>", LoginBranding.load(resources).welcome());
            assertEquals(7, LoginBranding.load(resources).logo().getWidth());
            try (var sql = reopened.getConnection().createStatement();
                 var result = sql.executeQuery("SELECT COUNT(*) FROM resources WHERE NAME LIKE 'Login.%'")) {
                assertTrue(result.next());
                assertEquals(4, result.getInt(1));
            }
        } finally {
            shutdown(reopened, engine);
        }
    }

    private static void shutdown(Session session, String engine) throws Exception {
        if (engine.equals("hsqldb")) {
            try (var sql = session.getConnection().createStatement()) { sql.execute("SHUTDOWN"); }
        }
        session.close();
    }
}
