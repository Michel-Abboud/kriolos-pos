package com.openbravo.data.loader;

import java.sql.SQLException;

/** Loads the selected embedded driver independently of JDBC service discovery. */
public final class JdbcDriverLoader {
    private JdbcDriverLoader() { }

    public static void loadFor(String url) throws SQLException {
        String driver = url != null && url.startsWith("jdbc:sqlite:") ? "org.sqlite.JDBC"
                : url != null && url.startsWith("jdbc:hsqldb:") ? "org.hsqldb.jdbc.JDBCDriver" : null;
        if (driver == null) return;
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException | LinkageError ex) {
            throw new SQLException("Cannot load embedded database driver " + driver
                    + ". Check that the application's runtime dependencies and Java version match this build.", ex);
        }
    }
}
