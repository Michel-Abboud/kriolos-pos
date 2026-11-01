package com.openbravo.pos.panels;

import com.openbravo.data.user.EditorRecord;
import com.openbravo.data.user.ListProvider;
import com.openbravo.data.user.SaveProvider;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JPanelTableNavigationTest {
    @Test
    void reopeningMaintenancePageKeepsOneEditorAndOneSetOfControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var editor = mock(EditorRecord.class);
                var editorPanel = new JPanel();
                when(editor.getComponent()).thenReturn(editorPanel);
                var provider = mock(ListProvider.class);
                when(provider.loadData()).thenReturn(List.of());
                var saver = mock(SaveProvider.class);
                JPanelTable page = new JPanelTable() {
                    public String getTitle() { return "Maintenance"; }
                    protected void init() { }
                    public EditorRecord getEditor() { return editor; }
                    public ListProvider getListProvider() { return provider; }
                    public SaveProvider getSaveProvider() { return saver; }
                    public ListCellRenderer getListCellRenderer() { return new DefaultListCellRenderer(); }
                };
                page.init(mock(AppView.class));
                page.activate();
                Container originalViewport = editorPanel.getParent();
                int initialCount = countComponents(page);
                for (int i = 0; i < 8; i++) {
                    assertTrue(page.deactivate());
                    page.activate();
                    assertEquals(initialCount, countComponents(page), "reopening must not accumulate hidden wrappers or controls");
                    assertSame(originalViewport, editorPanel.getParent(), "editor must not be moved to another viewport");
                }
                verify(provider, times(9)).loadData();
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
    }

    private static int countComponents(Container parent) {
        int result = 0;
        for (Component child : parent.getComponents()) {
            result++;
            if (child instanceof Container container) result += countComponents(container);
        }
        return result;
    }
}
