package com.openbravo.pos.payment;

import com.openbravo.data.loader.Session;
import com.openbravo.pos.catalog.CatalogServiceImpl;
import com.openbravo.pos.data.DBMigrator;
import com.openbravo.pos.sales.DataLogicSales;
import com.openbravo.pos.ticket.DualCurrencyTotal;
import com.openbravo.pos.ticket.TaxInfo;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import com.openbravo.pos.ticket.TicketTaxInfo;
import com.openbravo.pos.ticket.UserInfo;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DualCurrencyCashPersistenceTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"hsqldb", "sqlite"})
    void savesMixedCashAsOnePaymentInTheAccountingCurrency(String engine) throws Exception {
        Class.forName(engine.equals("sqlite") ? "org.sqlite.JDBC" : "org.hsqldb.jdbc.JDBCDriver");
        String url = engine.equals("sqlite") ? "jdbc:sqlite:" + directory.resolve("store.db")
                : "jdbc:hsqldb:file:" + directory.resolve("store");
        Session session = new Session(url, engine.equals("hsqldb") ? "SA" : null, "");
        try {
            DBMigrator.execDBMigration(session);
            String user;
            try (var sql = session.getConnection().createStatement(); var row = sql.executeQuery("SELECT ID FROM people LIMIT 1")) {
                assertTrue(row.next()); user = row.getString(1);
            }
            try (var sql = session.getConnection().createStatement()) {
                sql.executeUpdate("INSERT INTO closedcash (MONEY, HOST, HOSTSEQUENCE, DATESTART)"
                        + " VALUES ('mixed-cash', 'test', 1, '2026-11-01 00:00:00.000')");
            }
            var ticket = new TicketInfo();
            ticket.setUser(new UserInfo(user, "Test cashier"));
            ticket.setActiveCash("mixed-cash");
            ticket.setDate(Timestamp.valueOf("2026-11-01 12:00:00"));
            var catalog = new CatalogServiceImpl(); catalog.init(session);
            var tax = new TaxInfo("001", "Tax Standard", "001", null, null, 0.15, false, 0);
            ticket.addLine(new TicketLineInfo(catalog.getProductInfoByCode("xxx999"), 2, 10, tax, null));
            var taxLine = new TicketTaxInfo(tax); taxLine.add(20);
            ticket.setTaxes(List.of(taxLine));
            assertEquals(23.0, ticket.getTotal());
            var tender = new DualCurrencyCash(new DualCurrencyTotal("USD", "LBP", "90000"))
                    .calculate(BigDecimal.valueOf(ticket.getTotal()), new BigDecimal("10"), new BigDecimal("1800000"));
            ticket.setPayments(List.of(new PaymentInfoCash(tender.appliedInPrimary().doubleValue(),
                    tender.givenInPrimary().doubleValue(), tender.givenInPrimary().doubleValue())));
            var sales = new DataLogicSales(); sales.init(session);
            sales.saveTicket(ticket, "0");
            try (var sql = session.getConnection().prepareStatement("SELECT PAYMENT, TOTAL, TENDERED FROM payments WHERE RECEIPT = ?")) {
                sql.setString(1, ticket.getId());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("cash", row.getString(1));
                    assertEquals(23.0, row.getDouble(2));
                    assertEquals(30.0, row.getDouble(3));
                    assertFalse(row.next());
                }
            }
            var loaded = sales.loadTicket(TicketInfo.RECEIPT_NORMAL, ticket.getTicketId());
            assertEquals(23.0, loaded.getPayments().stream().mapToDouble(PaymentInfo::getTotal).sum());
        } finally {
            if (engine.equals("hsqldb")) {
                try (var sql = session.getConnection().createStatement()) { sql.execute("SHUTDOWN"); }
            }
            session.close();
        }
    }
}
