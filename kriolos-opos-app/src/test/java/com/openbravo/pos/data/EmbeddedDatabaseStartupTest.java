package com.openbravo.pos.data;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.config.AppConfig;
import com.openbravo.pos.spi.config.BootstrapConfig.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedDatabaseStartupTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"sqlite", "hsqldb"})
    void createsMigratesAndReopensMissingDatabaseInFreshJvmWithoutDriverFlags(String engine) throws Exception {
        Path database = directory.resolve("new-store");
        Path log = directory.resolve("startup.log");
        assertFalse(Files.exists(database));
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        if (!Files.exists(Path.of(java))) java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // Each launch gets a separate JVM: the second cannot reuse driver registration
        // or an open connection from the process that created the database.
        for (int launch = 0; launch < 2; launch++) {
            var process = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp",
                    System.getProperty("java.class.path"), Probe.class.getName(), engine, database.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            assertTrue(finished, () -> "Startup timed out: " + log);
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(log); } catch (Exception ex) { return log.toString(); }
            });
        }
        assertTrue(Files.exists(engine.equals("sqlite") ? database : Path.of(database + ".properties")));
    }

    /** Separate JVM: no test or launcher has registered either driver beforehand. */
    public static class Probe {
        public static void main(String[] args) throws Exception {
            String engine = args[0];
            String url = "jdbc:" + engine + (engine.equals("sqlite") ? ":" : ":file:") + args[1];
            String username = engine.equals("hsqldb") ? "SA" : null;
            // Force JDBC auto-discovery first, including all optional bundled drivers.
            java.sql.DriverManager.getDrivers();
            // Cover the login's connection test before its Session and migrations.
            AppConfig.testConnection(new DatabaseConfig("scratch", url, username, ""));
            for (int start = 0; start < 2; start++) {
                Session session = new Session(url, username, "");
                try {
                    DBMigrator.execDBMigration(session);
                    try (var sql = session.getConnection().createStatement();
                         var users = sql.executeQuery("SELECT COUNT(*) FROM people")) {
                        if (!users.next() || users.getInt(1) != 2) throw new AssertionError("Initial users were not seeded");
                    }
                } finally {
                    if (engine.equals("hsqldb")) {
                        try (var sql = session.getConnection().createStatement()) { sql.execute("SHUTDOWN"); }
                    }
                    session.close();
                }
            }
        }
    }
}
