package com.openbravo.pos.imports;

import com.csvreader.CsvReader;
import com.openbravo.pos.forms.AppView;
import com.openbravo.pos.forms.JPanelView;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/** CSV category import screen in Tools. */
public final class CategoryCSVImport extends JPanel implements JPanelView {

    private final AppView app;
    private final JTextField fileField = new JTextField(38);
    private final JComboBox<String> encoding = new JComboBox<>(new String[]{"UTF-8", "ISO-8859-1"});
    private final JComboBox<String> delimiter = new JComboBox<>(new String[]{",", ";", "\t"});
    private final Map<String, JComboBox<String>> mapping = new HashMap<>();
    private final JButton readButton = new JButton("Read headers");
    private final JButton importButton = new JButton("Import categories");
    private final JTextArea status = new JTextArea(6, 50);

    public CategoryCSVImport(AppView app) {
        this.app = app;
        setLayout(new BorderLayout(8, 8));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JPanel fileLine = new JPanel(new BorderLayout(6, 0));
        fileLine.add(new JLabel("CSV file"), BorderLayout.LINE_START);
        fileLine.add(fileField, BorderLayout.CENTER);
        JButton browse = new JButton("Browse...");
        fileLine.add(browse, BorderLayout.LINE_END);
        top.add(fileLine, BorderLayout.NORTH);
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEADING));
        options.add(new JLabel("Encoding"));
        options.add(encoding);
        options.add(new JLabel("Separator"));
        options.add(delimiter);
        options.add(readButton);
        top.add(options, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel fieldPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(3, 3, 3, 8);
        constraints.anchor = GridBagConstraints.LINE_START;
        int line = 0;
        for (String field : CategoryCsvImporter.FIELDS) {
            constraints.gridy = line++;
            constraints.gridx = 0;
            fieldPanel.add(new JLabel(field + (field.equals("Name") ? " *" : "")), constraints);
            JComboBox<String> selector = new JComboBox<>(new String[]{""});
            selector.setPreferredSize(new java.awt.Dimension(240, 28));
            constraints.gridx = 1;
            fieldPanel.add(selector, constraints);
            mapping.put(field, selector);
        }
        add(new JScrollPane(fieldPanel), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        importButton.setEnabled(false);
        actions.add(importButton);
        actions.add(new JLabel("Blank optional columns keep existing values on updates."));
        bottom.add(actions, BorderLayout.NORTH);
        status.setEditable(false);
        status.setLineWrap(true);
        bottom.add(new JScrollPane(status), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        browse.addActionListener(event -> chooseFile());
        readButton.addActionListener(event -> readHeaders());
        importButton.addActionListener(event -> runImport());
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (!fileField.getText().isBlank()) chooser.setSelectedFile(new File(fileField.getText()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            fileField.setText(chooser.getSelectedFile().getAbsolutePath());
            readHeaders();
        }
    }

    private void readHeaders() {
        importButton.setEnabled(false);
        try {
            Path file = Path.of(fileField.getText().trim());
            CsvReader csv = new CsvReader(file.toString(), separator(), Charset.forName((String) encoding.getSelectedItem()));
            try {
                if (!csv.readHeaders()) throw new IllegalArgumentException("The CSV has no header row.");
                for (String field : CategoryCsvImporter.FIELDS) {
                    JComboBox<String> selector = mapping.get(field);
                    selector.removeAllItems();
                    selector.addItem("");
                    for (String header : csv.getHeaders()) selector.addItem(header);
                    for (String header : csv.getHeaders()) {
                        if (header.trim().equalsIgnoreCase(field)) {
                            selector.setSelectedItem(header);
                            break;
                        }
                    }
                }
                status.setText("Headers loaded. Map Name and any other columns, then import.\n"
                        + "Parent accepts an existing category ID or name, including a category in this CSV.\n"
                        + "Image accepts a path relative to the CSV file.");
                importButton.setEnabled(true);
            } finally {
                csv.close();
            }
        } catch (Exception ex) {
            status.setText("Could not read CSV: " + ex.getMessage());
        }
    }

    private void runImport() {
        final Path file = Path.of(fileField.getText().trim());
        final Charset charset = Charset.forName((String) encoding.getSelectedItem());
        final char separator = separator();
        final Map<String, String> columns = new HashMap<>();
        mapping.forEach((field, selector) -> columns.put(field, (String) selector.getSelectedItem()));
        importButton.setEnabled(false);
        readButton.setEnabled(false);
        status.setText("Importing categories...");
        new SwingWorker<CategoryCsvImporter.Result, Void>() {
            @Override
            protected CategoryCsvImporter.Result doInBackground() throws Exception {
                return new CategoryCsvImporter(app.getSession()).importFile(file, charset, separator, columns);
            }

            @Override
            protected void done() {
                readButton.setEnabled(true);
                importButton.setEnabled(true);
                try {
                    CategoryCsvImporter.Result result = get();
                    status.setText("Import complete: " + result.created() + " created, "
                            + result.updated() + " updated. Open Categories to see the changes.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText("Import failed; no rows were saved. " + cause.getMessage());
                }
            }
        }.execute();
    }

    private char separator() {
        return ((String) delimiter.getSelectedItem()).charAt(0);
    }

    @Override
    public String getTitle() { return "Categories Import"; }

    @Override
    public javax.swing.JComponent getComponent() { return this; }

    @Override
    public void activate() { }

    @Override
    public boolean deactivate() { return true; }
}
