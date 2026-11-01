package com.openbravo.pos.reports;

import com.openbravo.data.loader.QBFCompareEnum;
import com.openbravo.format.Formats;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDateDefaultsTest {
    @Test
    void todayIncludesSalesAfterTheReportWasOpenedAndRollsOverOnRevisit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var now = new AtomicReference<>(date("2026-10-31T12:34:00").toInstant());
                Clock clock = mock(Clock.class);
                when(clock.getZone()).thenReturn(ZoneId.systemDefault());
                when(clock.instant()).thenAnswer(call -> now.get());
                var panel = new JParamsDatesInterval(clock);
                panel.setToday();
                Object[] filter = (Object[]) panel.createValue();
                assertEquals(QBFCompareEnum.COMP_GREATEROREQUALS, filter[0]);
                assertEquals(date("2026-10-31T00:00:00"), filter[1]);
                assertEquals(QBFCompareEnum.COMP_LESS, filter[2]);
                assertEquals(date("2026-11-01T00:00:00"), filter[3]);
                assertTrue(date("2026-10-31T18:45:59").before((Date) filter[3]), "new sales must remain inside today's report range");
                now.set(date("2026-11-01T08:30:00").toInstant());
                panel.activate();
                filter = (Object[]) panel.createValue();
                assertEquals(date("2026-11-01T00:00:00"), filter[1]);
                assertEquals(date("2026-11-02T00:00:00"), filter[3]);
                // A user-edited range must survive later navigation.
                var field = JParamsDatesInterval.class.getDeclaredField("jTxtStartDate");
                field.setAccessible(true);
                ((JTextField) field.get(panel)).setText(Formats.TIMESTAMP.formatValue(date("2026-10-01T00:00:00")));
                now.set(date("2026-11-02T08:30:00").toInstant());
                panel.activate();
                filter = (Object[]) panel.createValue();
                assertEquals(date("2026-10-01T00:00:00"), filter[1]);
                assertEquals(date("2026-11-02T00:00:00"), filter[3]);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
    }

    private static Date date(String value) {
        return Date.from(LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant());
    }
}
