package com.openbravo.pos.imports;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.data.DBMigrator;
import com.openbravo.pos.forms.AppProperties;
import com.openbravo.pos.forms.AppView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComboBox;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductImportsHsqlIntegrationTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    @Test
    void panelsImportProductsWithOptionalSupplierAndCreateMissingStockRow() throws Exception {
        Path databasePath = tempDir.resolve("scratch-pos");
        Session session = new Session("jdbc:hsqldb:file:" + databasePath, "SA", "");
        try {
            DBMigrator.execDBMigration(session);
            String locationId = queryString(session, "SELECT ID FROM LOCATIONS WHERE ID = '0'");
            assertEquals("0", locationId, "fresh schema should seed the default location");

            AppProperties properties = appProperties(tempDir.toFile());
            AppView app = appView(session, properties, locationId);

            JPanelCSVImport productImport = new JPanelCSVImport(app);
            productImport.activate();

            Path noSupplierCsv = tempDir.resolve("products-no-supplier.csv");
            Files.writeString(noSupplierCsv,
                    "Reference,Barcode,Name,Buy,Sell,Tax,Category\n"
                    + "REF-IMPORT-1,998877660001,Imported Product,1.25,2.50,000,Integration Category\n");
            prepareProductImport(productImport, noSupplierCsv, "Category", "");
            invoke(productImport, "ImportCsvFile", new Class<?>[]{String.class}, noSupplierCsv.toString());

            ProductRow optionalSupplierProduct = productByCode(session, "998877660001");
            assertNotNull(optionalSupplierProduct);
            assertEquals("Integration Category", optionalSupplierProduct.categoryName());
            assertNull(optionalSupplierProduct.supplierId(), "supplier is optional and was not mapped");
            assertEquals(0.0, stockUnits(session, locationId, optionalSupplierProduct.productId()));

            String supplierId = "scratch-default-supplier";
            try (PreparedStatement statement = session.getConnection().prepareStatement(
                    "INSERT INTO SUPPLIERS (ID, NAME, SEARCHKEY, VISIBLE) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, supplierId);
                statement.setString(2, "Scratch Default Supplier");
                statement.setString(3, "scratch-default-supplier-key");
                statement.setBoolean(4, true);
                statement.executeUpdate();
            }

            JPanelCSVImport defaultSupplierImport = new JPanelCSVImport(app);
            defaultSupplierImport.activate();
            Path defaultSupplierCsv = tempDir.resolve("products-default-supplier.csv");
            Files.writeString(defaultSupplierCsv,
                    "Reference,Barcode,Name,Buy,Sell,Tax,Category\n"
                    + "REF-IMPORT-2,998877660002,Imported With Default Supplier,2.00,3.00,000,Integration Category\n");
            prepareProductImport(defaultSupplierImport, defaultSupplierCsv, "Category", "[ USE DEFAULT SUPPLIER ]");
            invoke(defaultSupplierImport, "jComboSupplierFocusGained", new Class<?>[]{java.awt.event.FocusEvent.class}, (Object) null);
            JComboBox<?> defaultSupplierCombo = combo(defaultSupplierImport, "jComboDefaultSupplier");
            selectItemByText(defaultSupplierCombo, "Scratch Default Supplier");
            invoke(defaultSupplierImport, "ImportCsvFile", new Class<?>[]{String.class}, defaultSupplierCsv.toString());

            ProductRow defaultSupplierProduct = productByCode(session, "998877660002");
            assertNotNull(defaultSupplierProduct);
            assertEquals(supplierId, defaultSupplierProduct.supplierId());

            JPanelCSVImport defaultCategoryImport = new JPanelCSVImport(app);
            defaultCategoryImport.activate();
            Path defaultCategoryCsv = tempDir.resolve("products-default-category.csv");
            Files.writeString(defaultCategoryCsv,
                    "Reference,Barcode,Name,Buy,Sell,Tax,Supplier\n"
                    + "REF-IMPORT-3,998877660003,Default Category Product,2.50,4.00,000,Supplier From CSV\n");
            prepareProductImport(defaultCategoryImport, defaultCategoryCsv,
                    "[ USE DEFAULT CATEGORY ]", "Supplier");
            selectItemByText(combo(defaultCategoryImport, "jComboDefaultCategory"), "Category Standard");
            selectItemByText(combo(defaultCategoryImport, "jComboDefaultSupplier"), "Scratch Default Supplier");
            invoke(defaultCategoryImport, "ImportCsvFile", new Class<?>[]{String.class}, defaultCategoryCsv.toString());

            ProductRow defaultCategoryProduct = productByCode(session, "998877660003");
            assertNotNull(defaultCategoryProduct);
            assertEquals("Category Standard", defaultCategoryProduct.categoryName());
            assertEquals("Supplier From CSV", queryString(session,
                    "SELECT NAME FROM SUPPLIERS WHERE ID = '" + defaultCategoryProduct.supplierId() + "'"));

            try (PreparedStatement statement = session.getConnection().prepareStatement(
                    "DELETE FROM STOCKCURRENT WHERE LOCATION = ? AND PRODUCT = ?")) {
                statement.setString(1, locationId);
                statement.setString(2, optionalSupplierProduct.productId());
                statement.executeUpdate();
            }

            StockQtyImport scannerImport = new StockQtyImport(app);
            scannerImport.activate();
            try (PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE STOCKCURRENT SET UNITS = 3.0 WHERE LOCATION = ? AND PRODUCT = ?")) {
                statement.setString(1, locationId);
                statement.setString(2, defaultSupplierProduct.productId());
                statement.executeUpdate();
            }
            Path existingStockCsv = tempDir.resolve("scanner-existing-stock.csv");
            Files.writeString(existingStockCsv, "998877660002,2.5\n998877660002,5.0\n");
            invoke(scannerImport, "ImportCsvFile", new Class<?>[]{String.class}, existingStockCsv.toString());
            assertEquals(7.5, stockUnits(session, locationId, defaultSupplierProduct.productId()),
                    "scanner import should replace existing stock with the sum of repeated rows");

            Path scannerCsv = tempDir.resolve("scanner-stock.csv");
            Files.writeString(scannerCsv, "998877660001,7.5\n");
            invoke(scannerImport, "ImportCsvFile", new Class<?>[]{String.class}, scannerCsv.toString());

            assertEquals(7.5, stockUnits(session, locationId, optionalSupplierProduct.productId()));
        } finally {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (Exception ignored) {
                // The database may already have been closed by HSQLDB shutdown.
            }
            session.close();
        }
    }

    private static void prepareProductImport(JPanelCSVImport panel, Path csv,
                                             String categorySelection, String supplierSelection) throws Exception {
        invoke(panel, "GetheadersFromFile", new Class<?>[]{String.class}, csv.toString());
        combo(panel, "jCBiso").setSelectedItem("UTF-8");
        combo(panel, "jComboSeparator").setSelectedItem(",");
        combo(panel, "jComboReference").setSelectedItem("Reference");
        combo(panel, "jComboBarcode").setSelectedItem("Barcode");
        combo(panel, "jComboName").setSelectedItem("Name");
        combo(panel, "jComboBuy").setSelectedItem("Buy");
        combo(panel, "jComboSell").setSelectedItem("Sell");
        combo(panel, "jComboTax").setSelectedItem("Tax");
        combo(panel, "jComboCategory").setSelectedItem(categorySelection);
        if (!supplierSelection.isEmpty()) {
            combo(panel, "jComboSupplier").setSelectedItem(supplierSelection);
        } else {
            combo(panel, "jComboSupplier").setSelectedItem("");
        }
    }

    private static AppProperties appProperties(File configFile) {
        return proxy(AppProperties.class, (proxy, method, args) -> switch (method.getName()) {
            case "getHost" -> "scratch-terminal";
            case "getConfigFile" -> configFile;
            case "getProperty" -> args != null && args.length == 2 ? args[1] : null;
            case "getAll" -> List.of();
            case "getPrimary" -> null;
            case "toString" -> "ScratchAppProperties";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static AppView appView(Session session, AppProperties properties, String locationId) {
        return proxy(AppView.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSession" -> session;
            case "getProperties" -> properties;
            case "getInventoryLocation" -> locationId;
            case "toString" -> "ScratchAppView";
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return 0d;
    }

    private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            if (ex.getCause() instanceof Error error) throw error;
            throw ex;
        }
    }

    private static JComboBox<?> combo(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JComboBox<?>) field.get(target);
    }

    private static void selectItemByText(JComboBox<?> combo, String text) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            if (item != null && text.equals(item.toString())) {
                combo.setSelectedItem(item);
                return;
            }
        }
        fail("No combo item found for " + text);
    }

    private static String queryString(Session session, String sql) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static ProductRow productByCode(Session session, String code) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT p.ID, p.CATEGORY, p.SUPPLIER, c.NAME "
                + "FROM PRODUCTS p LEFT JOIN CATEGORIES c ON c.ID = p.CATEGORY WHERE p.CODE = ?")) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return new ProductRow(resultSet.getString(1), resultSet.getString(3), resultSet.getString(4));
            }
        }
    }

    private static double stockUnits(Session session, String locationId, String productId) throws Exception {
        try (PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT UNITS FROM STOCKCURRENT WHERE LOCATION = ? AND PRODUCT = ?")) {
            statement.setString(1, locationId);
            statement.setString(2, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "expected stockcurrent row");
                return resultSet.getDouble(1);
            }
        }
    }

    private record ProductRow(String productId, String supplierId, String categoryName) {}
}
