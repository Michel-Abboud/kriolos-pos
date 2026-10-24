//    KriolOS POS
//    Copyright (c) 2019-2026 KriolOS
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.openbravo.pos.sales.modern.two;

import com.openbravo.format.Formats;
import com.openbravo.pos.customers.CustomerInfoExt;
import com.openbravo.pos.forms.AppConfig;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import com.openbravo.pos.ui.components.ButtonSize;
import com.openbravo.pos.ui.components.POSButtonFactory;
import com.openbravo.pos.ui.components.UnicodeIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Modern digital receipt order pane for {@link ModernTwoSalesLayout}.
 * Positioned on the {@code LINE_START} side of the screen.
 * <p>
 * Contains:
 * <ul>
 *   <li>Top header with active Customer badge and Parked Orders counter.</li>
 *   <li>Border-free, high-touch Digital Receipt {@link JTable} using {@link ModernTwoTicketCellRenderer}.</li>
 *   <li>Quick utility action bar (Clear, Customer, Hold/Park, Discount).</li>
 *   <li>Selected line item modifiers (+, -, Edit, Delete).</li>
 *   <li>Financial summary (Subtotal, Taxes).</li>
 *   <li>Massive full-width primary Pay Call-to-Action button displaying the Grand Total directly.</li>
 * </ul>
 *
 * @author KriolOS Team
 */
public class ModernTwoTicketPane extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int ROW_HEIGHT = 58;

    private final ModernTwoTicketTableModel tableModel;
    private final JTable table;

    private final JLabel headerTitleLabel;

    // Financial totals
    private final JLabel subtotalLabel;
    private final JLabel taxLabel;
    private final JLabel discountLabel;
    private final JLabel grandTotalTitleLabel;
    private final JLabel grandTotalLabel;
    private final JLabel secondaryTotalTitleLabel;
    private final JLabel secondaryTotalLabel;
    private final JPanel secondaryTotalPanel;

    // Massive Pay Button
    private final JButton btnPay;

    // Callbacks (Only Pay)
    private Runnable onPayClicked;

    public ModernTwoTicketPane() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setPreferredSize(new Dimension(300, 0));
        setMinimumSize(new Dimension(260, 0));
        setName("kriolos:sales:modern-two:ticket-pane");

        // ----------------------------------------------------
        // 1. TOP HEADER: Ticket Title
        // ----------------------------------------------------
        headerTitleLabel = new JLabel(AppLocal.getIntString("label.Ticketsbag"));
        headerTitleLabel.setFont(headerTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerTitleLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
        add(headerTitleLabel, BorderLayout.PAGE_START);

        // ----------------------------------------------------
        // 2. CENTER: Digital Receipt JTable
        // ----------------------------------------------------
        tableModel = new ModernTwoTicketTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(ROW_HEIGHT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setTableHeader(null); // Digital receipt does not show table header
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(TicketLineInfo.class, new ModernTwoTicketCellRenderer());
        table.setFillsViewportHeight(true);
        table.setName("kriolos:sales:modern-two:ticket-table");

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        add(scrollPane, BorderLayout.CENTER);

        // ----------------------------------------------------
        // 3. PAGE_END: Totals Summary + Massive Pay Button (ONLY PAY IN TICKET PANE)
        // ----------------------------------------------------
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 8));
        bottomContainer.setOpaque(false);
        bottomContainer.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));

        JPanel totalsPanel = new JPanel();
        totalsPanel.setLayout(new BoxLayout(totalsPanel, BoxLayout.Y_AXIS));
        totalsPanel.setOpaque(false);
        totalsPanel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        JPanel subtotalRow = new JPanel(new GridLayout(1, 2, 8, 3));
        subtotalRow.setOpaque(false);
        JLabel subtotalTitle = new JLabel(AppLocal.getIntString("label.subtotalcash") + ":");
        subtotalTitle.setFont(subtotalTitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtotalLabel = new JLabel("0.00", SwingConstants.TRAILING);
        subtotalLabel.setFont(subtotalLabel.getFont().deriveFont(Font.PLAIN, 12f));

        subtotalRow.add(subtotalTitle);
        subtotalRow.add(subtotalLabel);

        JPanel taxRow = new JPanel(new GridLayout(1, 2, 8, 3));
        taxRow.setOpaque(false);
        JLabel taxTitle = new JLabel(AppLocal.getIntString("label.taxcash") + ":");
        taxTitle.setFont(taxTitle.getFont().deriveFont(Font.PLAIN, 12f));
        taxLabel = new JLabel("0.00", SwingConstants.TRAILING);
        taxLabel.setFont(taxLabel.getFont().deriveFont(Font.PLAIN, 12f));

        taxRow.add(taxTitle);
        taxRow.add(taxLabel);

        JPanel discountRow = new JPanel(new GridLayout(1, 2, 8, 3));
        discountRow.setOpaque(false);
        JLabel discountTitle = new JLabel(AppLocal.getIntString("label.discount") + ":");
        discountTitle.setFont(discountTitle.getFont().deriveFont(Font.PLAIN, 12f));
        discountLabel = new JLabel("0.00", SwingConstants.TRAILING);
        discountLabel.setFont(discountLabel.getFont().deriveFont(Font.PLAIN, 12f));

        discountRow.add(discountTitle);
        discountRow.add(discountLabel);

        JPanel currencyTotalsRow = new JPanel(new GridLayout(1, 2, 8, 3));
        currencyTotalsRow.setOpaque(false);
        JPanel primaryTotalPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        primaryTotalPanel.setOpaque(false);
        grandTotalTitleLabel = new JLabel(currencyTotalLabel(""), SwingConstants.CENTER);
        grandTotalTitleLabel.setFont(grandTotalTitleLabel.getFont().deriveFont(Font.BOLD, 14f));
        grandTotalTitleLabel.setForeground(new Color(22, 163, 74)); // #16a34a
        grandTotalLabel = new JLabel("0.00", SwingConstants.CENTER);
        grandTotalLabel.setFont(grandTotalLabel.getFont().deriveFont(Font.BOLD, 16f));
        grandTotalLabel.setForeground(new Color(22, 163, 74));
        grandTotalLabel.setName("kriolos:sales:lbl-modern-two-primary-total");
        primaryTotalPanel.add(grandTotalTitleLabel);
        primaryTotalPanel.add(grandTotalLabel);

        secondaryTotalPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        secondaryTotalPanel.setOpaque(false);
        secondaryTotalTitleLabel = new JLabel(currencyTotalLabel(""), SwingConstants.CENTER);
        secondaryTotalTitleLabel.setFont(secondaryTotalTitleLabel.getFont().deriveFont(Font.BOLD, 14f));
        secondaryTotalTitleLabel.setForeground(new Color(22, 163, 74));
        secondaryTotalLabel = new JLabel("", SwingConstants.CENTER);
        secondaryTotalLabel.setFont(secondaryTotalLabel.getFont().deriveFont(Font.BOLD, 16f));
        secondaryTotalLabel.setForeground(new Color(22, 163, 74));
        secondaryTotalLabel.setName("kriolos:sales:lbl-modern-two-secondary-total");
        secondaryTotalPanel.add(secondaryTotalTitleLabel);
        secondaryTotalPanel.add(secondaryTotalLabel);
        secondaryTotalPanel.setVisible(false);

        currencyTotalsRow.add(primaryTotalPanel);
        currencyTotalsRow.add(secondaryTotalPanel);
        totalsPanel.add(subtotalRow);
        totalsPanel.add(taxRow);
        totalsPanel.add(discountRow);
        totalsPanel.add(currencyTotalsRow);
        bottomContainer.add(totalsPanel, BorderLayout.PAGE_START);

        // Massive Pay Button (Image 1 style)
        btnPay = POSButtonFactory.createSuccessButton(
                AppLocal.getIntString("button.pay") + " (F12)",
                UnicodeIcon.PAY,
                ButtonSize.MASSIVE,
                KeyEvent.VK_P,
                AppLocal.getIntString("button.pay.tooltip"),
                e -> {
                    if (onPayClicked != null) onPayClicked.run();
                }
        );
        btnPay.setPreferredSize(new Dimension(0, ButtonSize.MASSIVE.getHeight()));
        btnPay.setMinimumSize(new Dimension(0, ButtonSize.MASSIVE.getHeight()));
        btnPay.setName("kriolos:sales:modern-two:btn-pay");
        bottomContainer.add(btnPay, BorderLayout.PAGE_END);

        add(bottomContainer, BorderLayout.PAGE_END);

        applyComponentOrientation(ComponentOrientation.getOrientation(Locale.getDefault()));
    }

    public void updateTicket(TicketInfo ticket, CustomerInfoExt customer) {
        if (ticket != null) {
            tableModel.setLines(ticket.getLines());

            double subtotal = ticket.getSubTotal();
            double total = ticket.getTotal();
            double tax = total - subtotal;

            subtotalLabel.setText(Formats.CURRENCY.formatValue(subtotal));
            taxLabel.setText(Formats.CURRENCY.formatValue(tax));
            discountLabel.setText(Formats.CURRENCY.formatValue(0.0));
            String secondaryTotal = ticket.printSecondaryTotal();
            String primaryCode = AppConfig.getInstance().getProperty("currency.primary", "");
            String secondaryCode = AppConfig.getInstance().getProperty("currency.secondary", "");
            grandTotalTitleLabel.setText(currencyTotalLabel(primaryCode));
            grandTotalLabel.setText(ticket.printPrimaryTotal());
            secondaryTotalTitleLabel.setText(currencyTotalLabel(secondaryCode));
            secondaryTotalLabel.setText(secondaryTotal);
            secondaryTotalPanel.setVisible(!secondaryTotal.isEmpty());

            // Auto-select latest item
            if (tableModel.getRowCount() > 0) {
                int lastRow = tableModel.getRowCount() - 1;
                table.setRowSelectionInterval(lastRow, lastRow);
                table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
            }
        } else {
            tableModel.clear();
            subtotalLabel.setText("0.00");
            taxLabel.setText("0.00");
            discountLabel.setText("0.00");
            grandTotalLabel.setText("0.00");
            secondaryTotalLabel.setText("");
            secondaryTotalPanel.setVisible(false);
        }
    }

    private static String currencyTotalLabel(String code) {
        return code == null || code.isBlank() ? AppLocal.getIntString("label.totalcash")
                : code.trim().toUpperCase(Locale.ROOT) + " " + AppLocal.getIntString("label.totalcash");
    }

    public int getSelectedLineIndex() {
        return table.getSelectedRow();
    }

    public void setOnPayClicked(Runnable r) { this.onPayClicked = r; }
}
