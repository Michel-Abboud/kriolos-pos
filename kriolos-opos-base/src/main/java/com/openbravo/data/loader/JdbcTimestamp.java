package com.openbravo.data.loader;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/** Keeps SQLite prepared timestamps compatible with static SQL date filters. */
final class JdbcTimestamp {
    private JdbcTimestamp() { }

    static void bind(PreparedStatement statement, int index, Date value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else if ("SQLite".equals(statement.getConnection().getMetaData().getDatabaseProductName())) {
            // The schema uses TEXT dates. SQLite JDBC's default setTimestamp
            // writes epoch milliseconds, which become unreadable numeric TEXT.
            statement.setString(index, DataWriteUtils.formatTimestamp(value));
        } else {
            statement.setTimestamp(index, new Timestamp(value.getTime()));
        }
    }
}
