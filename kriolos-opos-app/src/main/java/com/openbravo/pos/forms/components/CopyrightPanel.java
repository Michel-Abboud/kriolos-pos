package com.openbravo.pos.forms.components;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Copyright display panel component refactored to use a clean BorderLayout.
 *
 * @author poolborges
 */
public class CopyrightPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final String OFICIAL_S = "https://github.com/kriolos-obiz/kriolos-pos";

    private javax.swing.JLabel copyRightLabel;

    /**
     * Creates new form CopyrightPanel
     */
    public CopyrightPanel() {
        initComponents();
        setName("kriolos:copyright:panel");
        copyRightLabel.setName("kriolos:copyright:lbl-content");

        try {
            setContent(LoginBranding.defaultWelcome());
        } catch (java.io.IOException ex) {
            setContent("KriolOS POS\n" + OFICIAL_S);
        }
    }

    public void setContent(String content) {
        copyRightLabel.setText(LoginBranding.displayText(content));
    }

    /**
     * This method is called from within the constructor to initialize the form
     * using standard layouts.
     */
    @SuppressWarnings("unchecked")
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(15, 15, 15, 15));

        copyRightLabel = new JLabel();
        copyRightLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        copyRightLabel.setVerticalAlignment(SwingConstants.TOP);
        copyRightLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Wrap inside a JScrollPane to ensure proper scrolling if content exceeds panel boundaries
        JScrollPane scrollPane = new JScrollPane(copyRightLabel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(scrollPane, BorderLayout.CENTER);

        copyRightLabel.getAccessibleContext().setAccessibleName("Copyright and License Information");
    }
}
