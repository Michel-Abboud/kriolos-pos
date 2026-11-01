package com.openbravo.beans;

import com.openbravo.data.gui.modal.PosUIModal;
import com.openbravo.editor.JEditorPassword;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the base editor with the application's image resources on the classpath. */
class JEditorTextPanelEnterTest {
    @Test
    void passwordEnterAcceptsTheCurrentValueExactlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPasswordPanel panel = new JPasswordPanel();
            PosUIModal modal = mock(PosUIModal.class);
            panel.setModalContext(modal);
            JEditorPassword editor = find(panel, JEditorPassword.class);
            editor.setText("synthetic-test-value");
            JTextField keyboard = find(panel, JTextField.class);
            assertNotNull(keyboard);
            keyboard.postActionEvent();
            KeyEvent typedEnter = new KeyEvent(keyboard, KeyEvent.KEY_TYPED, 0, 0,
                    KeyEvent.VK_UNDEFINED, '\n');
            for (var listener : keyboard.getKeyListeners()) listener.keyTyped(typedEnter);
            assertTrue(panel.isAccepted());
            assertEquals("synthetic-test-value", panel.getValue());
            verify(modal).setResult("synthetic-test-value");
            verify(modal, times(1)).close();
        });
    }

    @Test
    void enterAlsoAcceptsWhenFocusIsElsewhereInsideTheEditorPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JEditorTextPanel panel = new JEditorTextPanel();
            Object actionKey = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            assertNotNull(actionKey);
            panel.getActionMap().get(actionKey).actionPerformed(null);
            assertTrue(panel.isAccepted());
        });
    }

    private static <T extends Component> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T found = find(container, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
