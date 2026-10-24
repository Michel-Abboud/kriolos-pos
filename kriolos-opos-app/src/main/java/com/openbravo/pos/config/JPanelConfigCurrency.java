package com.openbravo.pos.config;

import com.openbravo.pos.forms.AppConfig;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.ticket.DualCurrencyTotal;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Configures display currencies without changing stored prices or payments. */
public final class JPanelConfigCurrency extends JPanel implements PanelConfig {

    private final JTextField primary = new JTextField(12);
    private final JTextField secondary = new JTextField(12);
    private final JTextField rate = new JTextField(12);
    private boolean dirty;

    public JPanelConfigCurrency() {
        super(new GridBagLayout());
        setName("kriolos:config:currency-panel");
        primary.setName("kriolos:config:txt-primary-currency");
        secondary.setName("kriolos:config:txt-secondary-currency");
        rate.setName("kriolos:config:txt-secondary-rate");
        addRow(0, AppLocal.getIntString("label.currency.primary"), primary);
        addRow(1, AppLocal.getIntString("label.currency.secondary"), secondary);
        addRow(2, AppLocal.getIntString("label.currency.rate"), rate);
        GridBagConstraints hint = new GridBagConstraints();
        hint.gridx = 0;
        hint.gridy = 3;
        hint.gridwidth = 2;
        hint.anchor = GridBagConstraints.WEST;
        hint.insets = new Insets(12, 8, 8, 8);
        add(new JLabel(AppLocal.getIntString("label.currency.hint")), hint);
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { dirty = true; }
            @Override public void removeUpdate(DocumentEvent e) { dirty = true; }
            @Override public void changedUpdate(DocumentEvent e) { dirty = true; }
        };
        primary.getDocument().addDocumentListener(listener);
        secondary.getDocument().addDocumentListener(listener);
        rate.getDocument().addDocumentListener(listener);
    }

    private void addRow(int row, String caption, JTextField field) {
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(8, 8, 8, 8);
        add(new JLabel(caption), label);
        GridBagConstraints input = new GridBagConstraints();
        input.gridx = 1;
        input.gridy = row;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(8, 8, 8, 8);
        add(field, input);
    }

    @Override public boolean hasChanged() { return dirty; }
    @Override public Component getConfigComponent() { return this; }

    @Override public void loadProperties(AppConfig config) {
        primary.setText(config.getProperty("currency.primary", ""));
        secondary.setText(config.getProperty("currency.secondary", ""));
        rate.setText(config.getProperty("currency.secondary.rate", ""));
        dirty = false;
    }

    @Override public void saveProperties(AppConfig config) {
        String primaryCode = primary.getText().trim().toUpperCase(java.util.Locale.ROOT);
        String secondaryCode = secondary.getText().trim().toUpperCase(java.util.Locale.ROOT);
        String conversionRate = rate.getText().trim();
        try {
            new DualCurrencyTotal(primaryCode, secondaryCode, conversionRate);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(AppLocal.getIntString("message.currency.invalid"), ex);
        }
        config.setProperty("currency.primary", primaryCode);
        config.setProperty("currency.secondary", secondaryCode);
        config.setProperty("currency.secondary.rate", conversionRate);
        dirty = false;
    }
}
