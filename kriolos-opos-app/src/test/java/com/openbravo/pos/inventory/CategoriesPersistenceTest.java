package com.openbravo.pos.inventory;

import com.openbravo.data.loader.Session;
import com.openbravo.data.user.DefaultSaveProvider;
import com.openbravo.pos.data.DBMigrator;
import com.openbravo.pos.pim.DataLogicPIM;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoriesPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void savedCategorySurvivesDatabaseRestart() throws Exception {
        String url = "jdbc:hsqldb:file:" + tempDir.resolve("scratch-pos");
        Session session = new Session(url, "SA", "");
        try {
            DBMigrator.execDBMigration(session);
            assertEquals(true, session.getConnection().getAutoCommit(),
                    "database migration must leave the application connection in auto-commit mode");
            DataLogicPIM pim = new DataLogicPIM();
            pim.init(session);
            Object[] category = {
                "test-category", "Saved Category", null, null, "", true,
                null, null, true
            };
            assertEquals(1, new DefaultSaveProvider(pim.getTableCategories()).insertData(category));
            assertEquals("Saved Category", categoryName(session));
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("SHUTDOWN");
            }
        } finally {
            session.close();
        }

        Session reopened = new Session(url, "SA", "");
        try {
            assertEquals("Saved Category", categoryName(reopened));
        } finally {
            try (Statement statement = reopened.getConnection().createStatement()) {
                statement.execute("SHUTDOWN");
            }
            reopened.close();
        }
    }

    private static String categoryName(Session session) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT NAME FROM CATEGORIES WHERE ID = ?")) {
            statement.setString(1, "test-category");
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
