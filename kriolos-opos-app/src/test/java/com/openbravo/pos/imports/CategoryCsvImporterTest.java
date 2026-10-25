package com.openbravo.pos.imports;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.data.DBMigrator;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CategoryCsvImporterTest {

    @TempDir Path tempDir;

    @Test
    void importsParentsUpdatesAndRejectsInvalidRowsAtomically() throws Exception {
        Session session = new Session("jdbc:hsqldb:file:" + tempDir.resolve("scratch-pos"), "SA", "");
        try {
            DBMigrator.execDBMigration(session);
            CategoryCsvImporter importer = new CategoryCsvImporter(session);
            Path file = tempDir.resolve("categories.csv");
            Map<String, String> columns = Map.of("Name", "Name", "Parent", "Parent",
                    "Text tip", "Text tip", "In catalog", "In catalog", "Image", "Image");
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png",
                    tempDir.resolve("icon.png").toFile());
            Files.writeString(file, "Name,Parent,Text tip,In catalog,Image\n"
                    + "Child,Parent,Child tip,yes,icon.png\nParent,,Parent tip,no,\n");
            assertEquals(new CategoryCsvImporter.Result(2, 0),
                    importer.importFile(file, StandardCharsets.UTF_8, ',', columns));
            assertEquals("Parent", parentName(session, "Child"));
            assertEquals(false, enabled(session, "Parent"));
            assertTrue(hasImage(session, "Child"));

            Files.writeString(file, "Name,Parent,Text tip,In catalog,Image\n"
                    + "Child,Parent,Changed tip,yes,\n");
            assertEquals(new CategoryCsvImporter.Result(0, 1),
                    importer.importFile(file, StandardCharsets.UTF_8, ',', columns));
            assertEquals("Changed tip", tip(session, "Child"));
            assertTrue(hasImage(session, "Child"));

            Files.writeString(file, "Name,Parent,Text tip,In catalog,Image\n"
                    + "Would be new,,,yes,\nInvalid,Unknown parent,,yes,\n");
            assertThrows(IllegalArgumentException.class,
                    () -> importer.importFile(file, StandardCharsets.UTF_8, ',', columns));
            assertNull(tip(session, "Would be new"));
        } finally {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("SHUTDOWN");
            }
            session.close();
        }
    }

    private static String parentName(Session session, String name) throws Exception {
        try (PreparedStatement query = session.getConnection().prepareStatement(
                "SELECT p.NAME FROM categories c JOIN categories p ON c.PARENTID = p.ID WHERE c.NAME = ?")) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static Boolean enabled(Session session, String name) throws Exception {
        try (PreparedStatement query = session.getConnection().prepareStatement(
                "SELECT CATALOGENABLED FROM categories WHERE NAME = ?")) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getBoolean(1) : null; }
        }
    }

    private static String tip(Session session, String name) throws Exception {
        try (PreparedStatement query = session.getConnection().prepareStatement(
                "SELECT TEXTTIP FROM categories WHERE NAME = ?")) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static boolean hasImage(Session session, String name) throws Exception {
        try (PreparedStatement query = session.getConnection().prepareStatement(
                "SELECT IMAGE FROM categories WHERE NAME = ?")) {
            query.setString(1, name);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() && rows.getBytes(1) != null;
            }
        }
    }
}
