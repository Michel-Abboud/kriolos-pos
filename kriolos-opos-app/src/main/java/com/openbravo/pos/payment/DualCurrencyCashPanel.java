package com.openbravo.pos.payment;

import com.openbravo.basic.AppLocal;
import com.openbravo.editor.JEditorDoublePositive;
import com.openbravo.editor.JEditorKeys;
import com.openbravo.pos.customers.CustomerInfoExt;
import com.openbravo.pos.ticket.DualCurrencyTotal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Cash entry in both currencies, using the shared POS keypad. */
public final class DualCurrencyCashPanel extends JPanel implements JPaymentInterface {
    private final JPaymentNotifier notifier;
    private final DualCurrencyTotal currency;
    private final DualCurrencyCash calculator;
    private final Consumer<BigDecimal> remainingChanged;
    private final JEditorDoublePositive primaryGiven = new JEditorDoublePositive();
    private final JEditorDoublePositive secondaryGiven = new JEditorDoublePositive();
    private final JLabel primaryReturn = amountLabel("kriolos:payment:return-primary");
    private final JLabel secondaryReturn = amountLabel("kriolos:payment:return-secondary");
    private BigDecimal due = BigDecimal.ZERO;
    private volatile DualCurrencyCash.Result result;
    private boolean resetting;

    public DualCurrencyCashPanel(JPaymentNotifier notifier, DualCurrencyTotal currency,
                                Consumer<BigDecimal> remainingChanged) {
        super(new BorderLayout(24, 0));
        this.notifier = notifier;
        this.currency = currency;
        this.calculator = new DualCurrencyCash(currency);
        this.remainingChanged = remainingChanged;
        setName("kriolos:payment:dual-cash");
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var keys = new JEditorKeys();
        keys.addActionListener(event -> {
            var root = javax.swing.SwingUtilities.getRootPane(this);
            if (root != null && root.getDefaultButton() != null && root.getDefaultButton().isEnabled()) {
                root.getDefaultButton().doClick();
            }
        });
        primaryGiven.addEditorKeys(keys);
        secondaryGiven.addEditorKeys(keys);
        primaryGiven.setName("kriolos:payment:given-primary");
        secondaryGiven.setName("kriolos:payment:given-secondary");
        primaryGiven.setFont(primaryGiven.getFont().deriveFont(20f));
        secondaryGiven.setFont(secondaryGiven.getFont().deriveFont(20f));
        primaryGiven.addPropertyChangeListener("Edition", event -> recalculate());
        secondaryGiven.addPropertyChangeListener("Edition", event -> recalculate());
        primaryGiven.addPropertyChangeListener("Text", event -> recalculate());
        secondaryGiven.addPropertyChangeListener("Text", event -> recalculate());

        JPanel amounts = new JPanel(new GridBagLayout());
        addRow(amounts, 0, AppLocal.getIntString("payment.given.primary", currency.primaryCode()), primaryGiven);
        addRow(amounts, 1, AppLocal.getIntString("payment.given.secondary", currency.secondaryCode()), secondaryGiven);
        JLabel title = new JLabel(AppLocal.getIntString("payment.return.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        GridBagConstraints heading = new GridBagConstraints();
        heading.gridx = 0; heading.gridy = 2; heading.gridwidth = 2;
        heading.anchor = GridBagConstraints.LINE_START;
        heading.insets = new Insets(26, 0, 10, 0);
        amounts.add(title, heading);
        addRow(amounts, 3, AppLocal.getIntString("payment.return.primary", currency.primaryCode()), primaryReturn);
        addRow(amounts, 4, AppLocal.getIntString("payment.return.secondary", currency.secondaryCode()), secondaryReturn);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = 5; filler.weighty = 1;
        amounts.add(new JPanel(), filler);
        add(amounts, BorderLayout.CENTER);
        add(keys, BorderLayout.LINE_END);
    }

    private static JLabel amountLabel(String name) {
        JLabel label = new JLabel("", SwingConstants.TRAILING);
        label.setName(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return label;
    }

    private static void addRow(JPanel panel, int row, String title, Component field) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row; constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(6, 0, 6, 12);
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
        label.setLabelFor(field);
        panel.add(label, constraints);
        constraints.gridx = 1; constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(6, 0, 6, 0);
        field.setPreferredSize(new Dimension(230, 42));
        panel.add(field, constraints);
    }

    @Override public void activate(CustomerInfoExt customer, double total, String transactionId) {
        due = currency.roundPrimary(BigDecimal.valueOf(total).max(BigDecimal.ZERO));
        resetting = true;
        primaryGiven.reset();
        secondaryGiven.reset();
        resetting = false;
        primaryGiven.activate();
        recalculate();
    }

    private static BigDecimal value(JEditorDoublePositive editor) {
        String text = editor.getText();
        return text == null || text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    private void recalculate() {
        if (resetting) return;
        try {
            result = calculator.calculate(due, value(primaryGiven), value(secondaryGiven));
            primaryReturn.setText(currency.formatPrimary(result.returnPrimary(), Locale.getDefault()));
            secondaryReturn.setText(currency.formatSecondaryAmount(result.returnSecondary(), Locale.getDefault()));
            if (remainingChanged != null) remainingChanged.accept(result.remainingInPrimary());
            notifier.setStatus(result.givenInPrimary().signum() > 0, result.complete());
        } catch (IllegalArgumentException ex) {
            result = null;
            primaryReturn.setText(""); secondaryReturn.setText("");
            if (remainingChanged != null) remainingChanged.accept(due);
            notifier.setStatus(false, false);
        }
    }

    @Override public PaymentInfo executePayment() {
        DualCurrencyCash.Result snapshot = result;
        if (snapshot == null || (!snapshot.complete() && snapshot.givenInPrimary().signum() == 0)) return null;
        return new PaymentInfoCash(snapshot.appliedInPrimary().doubleValue(),
                snapshot.givenInPrimary().doubleValue(), snapshot.givenInPrimary().doubleValue());
    }

    @Override public Component getComponent() { return this; }
}
