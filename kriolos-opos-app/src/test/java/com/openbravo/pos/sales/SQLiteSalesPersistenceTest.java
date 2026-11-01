package com.openbravo.pos.sales;

import com.openbravo.basic.BasicException;
import com.openbravo.data.loader.*;
import com.openbravo.pos.catalog.CatalogServiceImpl;
import com.openbravo.pos.data.DBMigrator;
import com.openbravo.pos.payment.PaymentInfoCash;
import com.openbravo.pos.reports.FinancialReportServiceImpl;
import com.openbravo.pos.reports.PanelReportBean;
import com.openbravo.pos.reports.JPanelReport;
import com.openbravo.pos.panels.AppView;
import com.openbravo.pos.scripting.ScriptFactory;
import com.openbravo.pos.ticket.*;
import java.nio.file.Path;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SQLiteSalesPersistenceTest {
    @TempDir Path tempDir;

    @Test
    void migrationRestoresExistingMillisecondDatesWithoutChangingSales() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("upgrade.db");
        Session session = new Session(url, null, null);
        Date date = Timestamp.valueOf("2026-10-31 15:23:45.123");
        int ticketId;
        try {
            DBMigrator.execDBMigration(session);
            DataLogicSales sales = new DataLogicSales();
            sales.init(session);
            TicketInfo ticket = sale(session, date);
            sales.saveTicket(ticket, "0");
            ticketId = ticket.getTicketId();
            try (Statement sql = session.getConnection().createStatement()) {
                sql.executeUpdate("UPDATE receipts SET DATENEW='" + date.getTime() + "'");
                sql.executeUpdate("DELETE FROM DATABASECHANGELOG WHERE ID='20261031-sqlite-timestamps'");
            }
        } finally {
            session.close();
        }
        Session reopened = new Session(url, null, null);
        try {
            DBMigrator.execDBMigration(reopened);
            DataLogicSales sales = new DataLogicSales();
            sales.init(reopened);
            assertSearchAndReport(reopened, sales, ticketId, date);
            // A second startup must preserve the converted dates and financial records.
            DBMigrator.execDBMigration(reopened);
            assertSearchAndReport(reopened, sales, ticketId, date);
        } finally {
            reopened.close();
        }
    }

    @Test
    void completedSaleIsSearchableLoadableAndReportedAfterRestart() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("scratch-pos.db");
        Session session = new Session(url, null, null);
        int ticketId;
        Date saleDate = Timestamp.valueOf("2026-10-31 15:23:45.123");
        try {
            DBMigrator.execDBMigration(session);
            DataLogicSales sales = new DataLogicSales();
            sales.init(session);
            TicketInfo ticket = sale(session, saleDate);
            sales.saveTicket(ticket, "0");
            ticketId = ticket.getTicketId();
            assertSearchAndReport(session, sales, ticketId, saleDate);
        } finally {
            session.close();
        }
        Session reopened = new Session(url, null, null);
        try {
            DBMigrator.execDBMigration(reopened);
            DataLogicSales sales = new DataLogicSales();
            sales.init(reopened);
            assertSearchAndReport(reopened, sales, ticketId, saleDate);
        } finally {
            reopened.close();
        }
    }

    @Test
    void failedSaleRollsBackReceiptTicketAndLines() throws Exception {
        Session session = new Session("jdbc:sqlite:" + tempDir.resolve("rollback.db"), null, null);
        try {
            DBMigrator.execDBMigration(session);
            try (Statement sql = session.getConnection().createStatement()) {
                sql.execute("CREATE TRIGGER fail_payment BEFORE INSERT ON payments BEGIN SELECT RAISE(ABORT, 'test payment failure'); END");
            }
            DataLogicSales sales = new DataLogicSales();
            sales.init(session);
            assertThrows(BasicException.class, () -> sales.saveTicket(sale(session, new Date()), "0"));
            for (String table : List.of("receipts", "tickets", "ticketlines", "payments", "stockdiary", "stockcurrent", "taxlines")) {
                try (Statement sql = session.getConnection().createStatement(); var rs = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "failed payment must roll back " + table);
                }
            }
        } finally {
            session.close();
        }
    }

    private static TicketInfo sale(Session session, Date date) throws Exception {
        String user;
        try (Statement sql = session.getConnection().createStatement(); var rs = sql.executeQuery("SELECT ID FROM people LIMIT 1")) {
            assertTrue(rs.next());
            user = rs.getString(1);
        }
        try (Statement sql = session.getConnection().createStatement()) {
            sql.executeUpdate("INSERT OR IGNORE INTO closedcash (MONEY, HOST, HOSTSEQUENCE, DATESTART) VALUES ('test-cash', 'test', 1, '2026-10-31 00:00:00.000')");
            sql.executeUpdate("UPDATE products SET ISSERVICE=0 WHERE CODE='xxx999'");
        }
        var ticket = new TicketInfo();
        ticket.setUser(new UserInfo(user, "Test cashier"));
        ticket.setActiveCash("test-cash");
        ticket.setDate(date);
        var tax = new TaxInfo("001", "Tax Standard", "001", null, null, 0.15, false, 0);
        var catalog = new CatalogServiceImpl();
        catalog.init(session);
        ticket.addLine(new TicketLineInfo(catalog.getProductInfoByCode("xxx999"), 2, 10, tax, null));
        var taxLine = new TicketTaxInfo(tax);
        taxLine.add(20);
        ticket.setTaxes(List.of(taxLine));
        ticket.setPayments(List.of(new PaymentInfoCash(23, 23)));
        return ticket;
    }

    private static void assertSearchAndReport(Session session, DataLogicSales sales, int ticketId, Date date) throws Exception {
        Object[] filter = new Object[14];
        for (int i = 0; i < filter.length; i += 2) filter[i] = QBFCompareEnum.COMP_NONE;
        filter[6] = QBFCompareEnum.COMP_GREATEROREQUALS;
        filter[7] = Timestamp.valueOf("2026-10-31 00:00:00.000");
        filter[8] = QBFCompareEnum.COMP_LESS;
        filter[9] = Timestamp.valueOf("2026-11-01 00:00:00.000");
        var found = sales.getTicketsList().list(filter);
        assertEquals(List.of(ticketId), found.stream().map(FindTicketsInfo::getTicketId).toList());
        TicketInfo loaded = sales.loadTicket(TicketInfo.RECEIPT_NORMAL, ticketId);
        assertEquals(date.getTime(), loaded.getDate().getTime());
        assertEquals(1, loaded.getLinesCount());
        assertEquals(23, loaded.getPayments().stream().mapToDouble(p -> p.getTotal()).sum(), 0.00001);
        assertEquals(23, loaded.getTotal(), 0.00001);
        // Execute the actual Transactions report definition, including its field readers.
        BaseSentence report = transactionsReport(session);
        var rows = report.list(new Object[]{Arrays.copyOfRange(filter, 6, 10)});
        assertEquals(1, rows.size());
        assertEquals(date.getTime(), ((Date) ((Object[]) rows.get(0))[0]).getTime());
        assertEquals(23.0, ((Object[]) rows.get(0))[4]);
        var cashReport = new FinancialReportServiceImpl(session).getFinancialReport(
                "test-cash", Timestamp.valueOf("2026-10-31 00:00:00.000"), null);
        assertEquals(1, cashReport.getPaymentCount());
        assertEquals(23, cashReport.getPaymentTotal(), 0.00001);
        assertEquals(1, cashReport.getSalesCount());
        assertEquals(3, cashReport.getSalesTaxes(), 0.00001);
        try (Statement sql = session.getConnection().createStatement(); var rs = sql.executeQuery("SELECT UNITS FROM stockcurrent")) {
            assertTrue(rs.next());
            assertEquals(-2, rs.getDouble(1), 0.00001);
        }
        try (Statement sql = session.getConnection().createStatement(); var rs = sql.executeQuery("SELECT BASE, AMOUNT FROM taxlines")) {
            assertTrue(rs.next());
            assertEquals(20, rs.getDouble(1), 0.00001);
            assertEquals(3, rs.getDouble(2), 0.00001);
        }
    }

    private static BaseSentence transactionsReport(Session session) throws Exception {
        var result = new AtomicReference<PanelReportBean>();
        try (var stream = SQLiteSalesPersistenceTest.class.getResourceAsStream("/com/openbravo/reports/sales_cashregisterlog.bs")) {
            assertNotNull(stream);
            String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set((PanelReportBean) ScriptFactory.getScriptEngine(ScriptFactory.BEANSHELL).eval(script));
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            });
        }
        AppView app = mock(AppView.class);
        when(app.getSession()).thenReturn(session);
        var appField = JPanelReport.class.getDeclaredField("m_App");
        appField.setAccessible(true);
        appField.set(result.get(), app);
        var queryMethod = PanelReportBean.class.getDeclaredMethod("getSentence");
        queryMethod.setAccessible(true);
        return (BaseSentence) queryMethod.invoke(result.get());
    }
}
