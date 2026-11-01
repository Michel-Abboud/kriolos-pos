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
            pim.createCategory(new Object[]{"image-only-category", "Image category", false});
            var imageCategory = pim.getRootCategories().stream()
                    .filter(item -> "image-only-category".equals(item.getID())).findFirst().orElseThrow();
            assertEquals(false, imageCategory.getCatShowName(), "hiding a name must not hide its category");
            assertEquals(true, imageCategory.getCatalogEnabled(), "new imported categories must enter the catalog");
            new DefaultSaveProvider(pim.getTableCategories()).insertData(new Object[]{
                "disabled-category", "Hidden category", null, null, "", true, null, null, false
            });
            new DefaultSaveProvider(pim.getTableCategories()).insertData(new Object[]{
                "legacy-category", "Legacy category", null, null, "", true, null, null, null
            });
            var catalogIds = pim.getRootCategories().stream().map(item -> item.getID()).toList();
            assertEquals(true, catalogIds.contains("legacy-category"));
            assertEquals(false, catalogIds.contains("disabled-category"));
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
