package com.openbravo.pos.imports;

import com.csvreader.CsvReader;
import com.openbravo.data.loader.Session;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Imports category CSV rows as one transaction, including parent references. */
public final class CategoryCsvImporter {

    public static final List<String> FIELDS = List.of("ID", "Name", "Parent", "Text tip",
            "Show name", "Catalog order", "Catalog color", "In catalog", "Image");

    private final Session session;

    public CategoryCsvImporter(Session session) {
        this.session = session;
    }

    public record Result(int created, int updated) { }

    private static final class Row {
        int line;
        String id;
        String name;
        String parent;
        String tip;
        Boolean showName;
        String order;
        String color;
        Boolean enabled;
        byte[] image;
        boolean existing;
    }

    public Result importFile(Path file, Charset charset, char delimiter,
                             Map<String, String> columns) throws Exception {
        List<Row> rows = readRows(file, charset, delimiter, columns);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The CSV has no category rows.");
        }

        Connection connection = session.getConnection();
        Map<String, String> names = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        Set<String> ambiguousNames = new HashSet<>();
        Set<String> existingIds = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement("SELECT ID, NAME, PARENTID FROM categories");
             ResultSet existing = query.executeQuery()) {
            while (existing.next()) {
                String id = existing.getString(1);
                existingIds.add(id);
                indexName(names, ambiguousNames, existing.getString(2), id);
                if (existing.getString(3) != null) parents.put(id, existing.getString(3));
            }
        }

        Set<String> importedIds = new HashSet<>();
        for (Row row : rows) {
            if (row.id == null) {
                String key = key(row.name);
                if (ambiguousNames.contains(key)) {
                    throw invalid(row, "name matches more than one existing category; provide ID");
                }
                row.id = names.get(key);
            }
            row.existing = row.id != null && existingIds.contains(row.id);
            if (row.id == null) {
                row.id = UUID.randomUUID().toString();
            }
            if (!importedIds.add(row.id)) {
                throw invalid(row, "category appears more than once in the CSV");
            }
            indexName(names, ambiguousNames, row.name, row.id);
        }

        for (Row row : rows) {
            if (row.parent != null) {
                String parentId = existingIds.contains(row.parent) || importedIds.contains(row.parent)
                        ? row.parent : names.get(key(row.parent));
                if (parentId == null || ambiguousNames.contains(key(row.parent))) {
                    throw invalid(row, "parent category not found or ambiguous: " + row.parent);
                }
                if (parentId.equals(row.id)) {
                    throw invalid(row, "a category cannot be its own parent");
                }
                parents.put(row.id, parentId);
            }
        }
        for (String id : parents.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = id;
            while (current != null) {
                if (!visited.add(current)) {
                    throw new IllegalArgumentException("Parent categories form a cycle in the CSV.");
                }
                current = parents.get(current);
            }
        }

        int created = 0;
        int updated = 0;
        session.begin();
        try {
            // Insert without parents first so children may precede parents in the CSV.
            for (Row row : rows) {
                if (row.existing) {
                    update(connection, row);
                    updated++;
                } else {
                    insert(connection, row);
                    created++;
                }
            }
            try (PreparedStatement parentUpdate = connection.prepareStatement(
                    "UPDATE categories SET PARENTID = ? WHERE ID = ?")) {
                for (Row row : rows) {
                    if (row.parent != null) {
                        parentUpdate.setString(1, parents.get(row.id));
                        parentUpdate.setString(2, row.id);
                        parentUpdate.executeUpdate();
                    }
                }
            }
            try (PreparedStatement remove = connection.prepareStatement(
                    "DELETE FROM products_cat WHERE PRODUCT IN (SELECT ID FROM products WHERE CATEGORY = ?)");
                 PreparedStatement add = connection.prepareStatement(
                    "INSERT INTO products_cat (PRODUCT, CATORDER) "
                    + "SELECT ID, NULL FROM products WHERE CATEGORY = ?")) {
                for (Row row : rows) {
                    if (row.existing && row.enabled != null) {
                        remove.setString(1, row.id);
                        remove.executeUpdate();
                        if (row.enabled) {
                            add.setString(1, row.id);
                            add.executeUpdate();
                        }
                    }
                }
            }
            session.commit();
            return new Result(created, updated);
        } catch (Exception ex) {
            session.rollback();
            throw ex;
        }
    }

    private static void insert(Connection connection, Row row) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO categories (ID, NAME, PARENTID, IMAGE, TEXTTIP, CATSHOWNAME, "
                + "CATORDER, CATALOGCOLOR, CATALOGENABLED) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, row.id);
            insert.setString(2, row.name);
            insert.setBytes(3, row.image);
            insert.setString(4, row.tip == null ? "" : row.tip);
            insert.setBoolean(5, row.showName == null || row.showName);
            insert.setString(6, row.order);
            insert.setString(7, row.color);
            insert.setBoolean(8, row.enabled == null || row.enabled);
            insert.executeUpdate();
        }
    }

    private static void update(Connection connection, Row row) throws SQLException {
        // A missing CSV column preserves the existing value.
        StringBuilder sql = new StringBuilder("UPDATE categories SET NAME = ?");
        List<Object> values = new ArrayList<>();
        values.add(row.name);
        if (row.image != null) { sql.append(", IMAGE = ?"); values.add(row.image); }
        if (row.tip != null) { sql.append(", TEXTTIP = ?"); values.add(row.tip); }
        if (row.showName != null) { sql.append(", CATSHOWNAME = ?"); values.add(row.showName); }
        if (row.order != null) { sql.append(", CATORDER = ?"); values.add(row.order); }
        if (row.color != null) { sql.append(", CATALOGCOLOR = ?"); values.add(row.color); }
        if (row.enabled != null) { sql.append(", CATALOGENABLED = ?"); values.add(row.enabled); }
        sql.append(" WHERE ID = ?");
        values.add(row.id);
        try (PreparedStatement update = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) update.setObject(i + 1, values.get(i));
            update.executeUpdate();
        }
    }

    private static List<Row> readRows(Path file, Charset charset, char delimiter,
                                      Map<String, String> columns) throws Exception {
        if (columns.get("Name") == null || columns.get("Name").isBlank()) {
            throw new IllegalArgumentException("Map a CSV column to Name.");
        }
        List<Row> rows = new ArrayList<>();
        CsvReader csv = new CsvReader(file.toString(), delimiter, charset);
        try {
            if (!csv.readHeaders()) throw new IllegalArgumentException("CSV header is missing.");
            int line = 1;
            while (csv.readRecord()) {
                line++;
                Row row = new Row();
                row.line = line;
                row.id = value(csv, columns, "ID");
                row.name = value(csv, columns, "Name");
                if (row.name == null) throw invalid(row, "Name is required");
                row.parent = value(csv, columns, "Parent");
                row.tip = value(csv, columns, "Text tip");
                row.order = value(csv, columns, "Catalog order");
                row.color = value(csv, columns, "Catalog color");
                if (row.color != null) {
                    try { Color.decode(row.color); }
                    catch (NumberFormatException ex) { throw invalid(row, "invalid catalog color"); }
                }
                row.showName = bool(value(csv, columns, "Show name"), row);
                row.enabled = bool(value(csv, columns, "In catalog"), row);
                String imagePath = value(csv, columns, "Image");
                if (imagePath != null) {
                    Path image = file.toAbsolutePath().getParent().resolve(imagePath).normalize();
                    if (!Files.isRegularFile(image)) throw invalid(row, "image not found: " + imagePath);
                    var decoded = ImageIO.read(image.toFile());
                    if (decoded == null) throw invalid(row, "invalid image: " + imagePath);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ImageIO.write(decoded, "png", bytes);
                    row.image = bytes.toByteArray();
                }
                rows.add(row);
            }
        } finally {
            csv.close();
        }
        return rows;
    }

    private static String value(CsvReader csv, Map<String, String> columns, String field) throws IOException {
        String column = columns.get(field);
        if (column == null || column.isBlank()) return null;
        String text = csv.get(column);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static Boolean bool(String text, Row row) {
        if (text == null) return null;
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw invalid(row, "invalid boolean: " + text);
        };
    }

    private static void indexName(Map<String, String> names, Set<String> ambiguous,
                                  String name, String id) {
        String key = key(name);
        String previous = names.putIfAbsent(key, id);
        if (previous != null && !previous.equals(id)) ambiguous.add(key);
    }

    private static String key(String text) {
        return text.toLowerCase(Locale.ROOT).trim();
    }

    private static IllegalArgumentException invalid(Row row, String reason) {
        return new IllegalArgumentException("CSV row " + row.line + ": " + reason);
    }
}
