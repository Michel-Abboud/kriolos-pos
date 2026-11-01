package com.openbravo.pos.data;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.admin.AppUserPermissionsLoader;
import com.openbravo.pos.admin.DataLogicSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DefaultUsersMigrationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "sqlite"})
    void freshStoreHasOnlyAdminAndRestrictedManagerAndSurvivesRestart(String engine) throws Exception {
        String url = url(engine);
        Session session = open(engine, url);
        try {
            DBMigrator.execDBMigration(session);
            assertFreshUsersAndPermissions(session);
        } finally { shutdown(session, engine); }
        Session reopened = open(engine, url);
        try {
            DBMigrator.execDBMigration(reopened);
            assertFreshUsersAndPermissions(reopened);
        } finally { shutdown(reopened, engine); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "sqlite"})
    void existingLegacyUsersPasswordsAndCustomRolesArePreserved(String engine) throws Exception {
        Session session = open(engine, url(engine));
        try {
            // Execute the actual historical seed without the new fresh-store initializer.
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                    new JdbcConnection(session.getConnection()));
            var resources = new ClassLoaderResourceAccessor();
            new Liquibase("pos_liquidbase/db-changelog-v4_5.xml", resources, database).update("pos-database-update");
            new Liquibase("pos_liquidbase/db-changelog-v4_5__LOAD.xml", resources, database).update("pos-database-update");
            if (!session.getConnection().getAutoCommit()) {
                session.getConnection().commit();
                session.getConnection().setAutoCommit(true);
            }
            String originalAdmin = roleXml(session, "1");
            String customPermissions = "<permissions><class name=\"custom.permission\"/></permissions>";
            try (var statement = session.getConnection().prepareStatement("UPDATE roles SET permissions = ? WHERE id = '2'")) {
                statement.setBytes(1, customPermissions.getBytes(StandardCharsets.UTF_8));
                statement.executeUpdate();
            }
            try (var statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE people SET apppassword = 'plain:store-password' WHERE id = '2'");
                statement.executeUpdate("UPDATE people SET name = 'Store Employee' WHERE id = '3'");
            }
            DBMigrator.execDBMigration(session);
            DBMigrator.execDBMigration(session);
            assertEquals(Set.of("admin", "manager", "Store Employee"), userNames(session));
            assertEquals(3, countRoles(session));
            assertEquals(customPermissions, roleXml(session, "2"));
            assertEquals(originalAdmin, roleXml(session, "1"));
            var system = new DataLogicSystem();
            system.init(session);
            assertTrue(system.listPeopleVisible().stream().filter(user -> user.getName().equals("manager"))
                    .findFirst().orElseThrow().authenticate("store-password"));
        } finally { shutdown(session, engine); }
    }

    private void assertFreshUsersAndPermissions(Session session) throws Exception {
        assertEquals(Set.of("admin", "manager"), userNames(session));
        assertEquals(2, countRoles(session));
        var system = new DataLogicSystem();
        system.init(session);
        var users = system.listPeopleVisible();
        assertEquals(2, users.size());
        var manager = users.stream().filter(user -> user.getName().equals("manager")).findFirst().orElseThrow();
        assertEquals("2", manager.getRole());
        assertTrue(manager.authenticate("manager"));
        var admin = users.stream().filter(user -> user.getName().equals("admin")).findFirst().orElseThrow();
        assertEquals("1", admin.getRole());
        assertTrue(admin.authenticate("admin"));
        var loader = new AppUserPermissionsLoader(system);
        manager.fillPermissions(loader.getPermissionsForRole(manager.getRole()));
        admin.fillPermissions(loader.getPermissionsForRole(admin.getRole()));
        assertEquals(resource("Role.Administrator.xml"), roleXml(session, "1"), "Admin defaults must remain unchanged");
        assertTrue(admin.hasPermission("com.openbravo.pos.admin.RolesPanel"));
        assertTrue(admin.hasPermission("com.openbravo.pos.config.JPanelConfiguration"));
        assertTrue(manager.hasPermission("sales.Total"));
        assertTrue(manager.hasPermission("payment.cash"));
        assertTrue(manager.hasPermission("sales.EditTicket"));
        assertFalse(manager.hasPermission("com.openbravo.pos.admin.RolesPanel"));
        assertFalse(manager.hasPermission("com.openbravo.pos.admin.PeoplePanel"));
        assertFalse(manager.hasPermission("com.openbravo.pos.admin.ResourcesPanel"));

        String menu = resource("Menu.Root.bs");
        Set<String> visible = new HashSet<>();
        var entries = Pattern.compile("(?m)^\\s*(?:group\\.addPanel|submenu\\s*=\\s*group\\.addSubmenu)\\(\"[^\"]*\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"\\)")
                .matcher(menu);
        while (entries.find()) {
            if (manager.hasPermission(entries.group(2))) visible.add(entries.group(1));
        }
        if (manager.hasPermission("Menu.ChangePassword")) visible.add("Menu.ChangePassword");
        if (manager.hasPermission("Menu.Exit")) visible.add("Menu.Exit");
        assertEquals(Set.of("Menu.Ticket", "Menu.TicketEdit", "Menu.StockManagement", "Menu.SalesManagement",
                "Menu.ChangePassword", "Menu.Printer", "Menu.Exit"), visible);
        // Every entry inside the two permitted submenus must actually open.
        String stockAndSales = menu.substring(menu.indexOf("// GROUP STOCK"), menu.indexOf("\"Menu.Maintenance\""));
        var children = Pattern.compile("submenu\\.addPanel\\(\"[^\"]*\",\\s*\"[^\"]*\",\\s*\"([^\"]*)\"\\)")
                .matcher(stockAndSales);
        while (children.find()) assertTrue(manager.hasPermission(children.group(1)), children.group(1));
    }

    private static String resource(String name) throws Exception {
        try (var input = DefaultUsersMigrationTest.class.getResourceAsStream("/com/openbravo/pos/templates/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String roleXml(Session session, String id) throws Exception {
        try (var statement = session.getConnection().prepareStatement("SELECT permissions FROM roles WHERE id = ?")) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                return new String(row.getBytes(1), StandardCharsets.UTF_8);
            }
        }
    }

    private static Set<String> userNames(Session session) throws Exception {
        Set<String> names = new HashSet<>();
        try (var statement = session.getConnection().createStatement(); var rows = statement.executeQuery("SELECT name FROM people")) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    private static int countRoles(Session session) throws Exception {
        try (var statement = session.getConnection().createStatement(); var row = statement.executeQuery("SELECT COUNT(*) FROM roles")) {
            assertTrue(row.next()); return row.getInt(1);
        }
    }

    private String url(String engine) {
        return engine.equals("sqlite") ? "jdbc:sqlite:" + directory.resolve("store.db")
                : "jdbc:hsqldb:file:" + directory.resolve("store");
    }

    private static Session open(String engine, String url) throws Exception {
        Class.forName(engine.equals("sqlite") ? "org.sqlite.JDBC" : "org.hsqldb.jdbc.JDBCDriver");
        return new Session(url, engine.equals("hsqldb") ? "SA" : null, "");
    }

    private static void shutdown(Session session, String engine) throws Exception {
        if (engine.equals("hsqldb")) {
            try (var statement = session.getConnection().createStatement()) { statement.execute("SHUTDOWN"); }
        }
        session.close();
    }
}
