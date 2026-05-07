package it.unibo.sampleapp.interactive;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.FSReportBand;
import it.unibo.sampleapp.fsstat.eventloop.EventLoopInteractiveFSStatLib;
import it.unibo.sampleapp.fsstat.interactive.FSInteractiveStatLib;
import it.unibo.sampleapp.fsstat.interactive.FSReportListener;
import it.unibo.sampleapp.fsstat.interactive.FSReportTask;
import it.unibo.sampleapp.fsstat.rx.RxInteractiveFSStatLib;
import it.unibo.sampleapp.fsstat.virtualthreads.VirtualThreadInteractiveFSStatLib;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import javax.swing.UIManager;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Minimal Swing GUI to start, stop, and observe filesystem statistics generation.
 */
public final class InteractiveFSStatApp {

    private static final int REPORT_WIDTH = 700;
    private static final int REPORT_HEIGHT = 400;
    private static final int TOP_PANEL_HEIGHT = REPORT_HEIGHT / 2;
    private static final int TOP_PANEL_PADDING = 8;
    private static final int TOP_PANEL_INTERNAL_PADDING = 6;
    private static final int DIRECTORY_ROW = 0;
    private static final int OPTIONS_ROW = 1;
    private static final int ACTIONS_ROW = 2;
    private static final int DIRECTORY_LABEL_COLUMN = 0;
    private static final int DIRECTORY_FIELD_COLUMN = 1;
    private static final int DIRECTORY_BUTTON_COLUMN = 2;
    private static final int MAX_LABEL_COLUMN = 3;
    private static final int MAX_FIELD_COLUMN = 4;
    private static final int BANDS_LABEL_COLUMN = 5;
    private static final int BANDS_FIELD_COLUMN = 6;
    private static final int MODE_LABEL_COLUMN = 7;
    private static final int MODE_BOX_COLUMN = 8;
    private static final int START_BUTTON_COLUMN = 0;
    private static final int STOP_BUTTON_COLUMN = 1;
    private static final int CLEAR_BUTTON_COLUMN = 2;
    private static final int DEFAULT_MAX_SIZE = 1024;
    private static final int DEFAULT_BANDS = 4;

    private InteractiveFSStatApp() {
        // Utility class.
    }

    /**
     * Launches the interactive GUI.
     *
     * @param args ignored
     */
    public static void main(final String[] args) {
        SwingUtilities.invokeLater(InteractiveFSStatApp::createAndShow);
    }

    private static void createAndShow() {
        final JFrame frame = new JFrame("FSStat Interactive");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        // Top: inputs and controls - use GridBagLayout so fields can expand
        final JPanel topPanel = new JPanel(new GridBagLayout());
        // Give the top panel enough height for multiple rows of controls
        topPanel.setPreferredSize(new Dimension(REPORT_WIDTH, TOP_PANEL_HEIGHT));
        final GridBagConstraints gbc = new GridBagConstraints();
        // larger insets and a small ipady to increase touch targets and readability
        gbc.insets = new Insets(TOP_PANEL_PADDING, TOP_PANEL_PADDING, TOP_PANEL_PADDING, TOP_PANEL_PADDING);
        gbc.ipady = TOP_PANEL_INTERNAL_PADDING;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = DIRECTORY_ROW;
        final JTextField directoryField = new JTextField(".", 25);
        final JButton chooseButton = new JButton("…");
        chooseButton.setToolTipText("Choose directory");

        final JTextField maxSizeField = new JTextField(String.valueOf(DEFAULT_MAX_SIZE), 8);
        final JTextField bandsField = new JTextField(String.valueOf(DEFAULT_BANDS), 4);
        final JComboBox<String> implementationBox = new JComboBox<>(new String[] {"EventLoop", "Rx", "VirtualThreads"});

        // Increase font sizes for better readability
        final Font baseFont = UIManager.getFont("Label.font");
        final Font largeFont = (baseFont != null)
                ? baseFont.deriveFont((float) Math.max(baseFont.getSize() + 4, 14))
                : new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        // Also update common UI defaults so all components use larger fonts
        UIManager.put("Label.font", largeFont);
        UIManager.put("Button.font", largeFont);
        UIManager.put("TextField.font", largeFont);
        UIManager.put("ComboBox.font", largeFont);
        UIManager.put("TextArea.font", largeFont);
        directoryField.setFont(largeFont);
        chooseButton.setFont(largeFont);
        maxSizeField.setFont(largeFont);
        bandsField.setFont(largeFont);
        implementationBox.setFont(largeFont);

        final JButton startButton = new JButton("Start");
        final JButton stopButton = new JButton("Stop");
        final JButton clearButton = new JButton("Clear");
        startButton.setFont(largeFont);
        stopButton.setFont(largeFont);
        clearButton.setFont(largeFont);
        stopButton.setEnabled(false);

        // Row 1: directory controls
        gbc.gridx = DIRECTORY_LABEL_COLUMN;
        gbc.weightx = 0.0;
        final JLabel dirLabel = new JLabel("Directory:");
        dirLabel.setFont(largeFont);
        topPanel.add(dirLabel, gbc);

        // Directory field expands to take available horizontal space
        gbc.gridx = DIRECTORY_FIELD_COLUMN;
        gbc.weightx = 1.0;
        topPanel.add(directoryField, gbc);

        // Choose button
        gbc.gridx = DIRECTORY_BUTTON_COLUMN;
        gbc.weightx = 0.0;
        topPanel.add(chooseButton, gbc);

        // Row 2: options
        gbc.gridy = OPTIONS_ROW;

        // Max size label + field
        gbc.gridx = MAX_LABEL_COLUMN;
        final JLabel maxLabel = new JLabel("MaxFS:");
        maxLabel.setFont(largeFont);
        topPanel.add(maxLabel, gbc);

        gbc.gridx = MAX_FIELD_COLUMN;
        gbc.weightx = 0.5;
        topPanel.add(maxSizeField, gbc);

        // Bands label + field
        gbc.gridx = BANDS_LABEL_COLUMN;
        final JLabel bandsLabel = new JLabel("Bands:");
        bandsLabel.setFont(largeFont);
        topPanel.add(bandsLabel, gbc);

        gbc.gridx = BANDS_FIELD_COLUMN;
        gbc.weightx = 0.5;
        topPanel.add(bandsField, gbc);

        // Mode label + box
        gbc.gridx = MODE_LABEL_COLUMN;
        final JLabel modeLabel = new JLabel("Mode:");
        modeLabel.setFont(largeFont);
        topPanel.add(modeLabel, gbc);

        gbc.gridx = MODE_BOX_COLUMN;
        gbc.weightx = 1.0;
        topPanel.add(implementationBox, gbc);

        // Row 3: actions
        gbc.gridy = ACTIONS_ROW;
        gbc.weightx = 0.0;
        gbc.gridx = START_BUTTON_COLUMN;
        topPanel.add(startButton, gbc);

        gbc.gridx = STOP_BUTTON_COLUMN;
        topPanel.add(stopButton, gbc);

        gbc.gridx = CLEAR_BUTTON_COLUMN;
        topPanel.add(clearButton, gbc);

        final JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        // Use monospaced font for the report area to improve readability of tabular data
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, largeFont.getSize()));
        final JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(REPORT_WIDTH, REPORT_HEIGHT));

        final JLabel statusLabel = new JLabel("Idle");
        statusLabel.setFont(largeFont);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        // File chooser
        chooseButton.addActionListener(e -> {
            final var chooser = new javax.swing.JFileChooser(directoryField.getText());
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            final int res = chooser.showOpenDialog(frame);
            if (res == javax.swing.JFileChooser.APPROVE_OPTION) {
                directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        // listener appends snapshots with timestamp and scrolls
        final String nl = System.lineSeparator();
        final FSReportListener listener = report -> SwingUtilities.invokeLater(() -> {
            final String snapshot = formatReport(report);
            outputArea.append("[update] " + java.time.LocalTime.now() + nl + snapshot + nl);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
            statusLabel.setText("Running — updates received");
        });

        final FSReportTask[] currentTask = new FSReportTask[1];

        startButton.addActionListener(event -> {
            final Path directory = Path.of(directoryField.getText().trim());
            final long maxFileSize;
            final int numberOfBands;
            try {
                maxFileSize = Long.parseLong(maxSizeField.getText().trim());
                numberOfBands = Integer.parseInt(bandsField.getText().trim());
            } catch (final NumberFormatException exception) {
                JOptionPane.showMessageDialog(frame, "MaxFS and Bands must be numeric", "Input error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            final FSInteractiveStatLib lib = createLib((String) implementationBox.getSelectedItem());
            if (lib == null) {
                JOptionPane.showMessageDialog(frame, "Unknown implementation selected", "Input error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // disable controls while running
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            implementationBox.setEnabled(false);
            directoryField.setEnabled(false);
            chooseButton.setEnabled(false);
            maxSizeField.setEnabled(false);
            bandsField.setEnabled(false);

            outputArea.append("Starting report..." + nl);
            statusLabel.setText("Running — starting");

            final FSReportTask task = lib.getFSReport(directory, maxFileSize, numberOfBands, listener);
            currentTask[0] = task;
            task.result().whenComplete((report, error) -> SwingUtilities.invokeLater(() -> {
                // re-enable controls
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                implementationBox.setEnabled(true);
                directoryField.setEnabled(true);
                chooseButton.setEnabled(true);
                maxSizeField.setEnabled(true);
                bandsField.setEnabled(true);

                if (error == null) {
                    outputArea.append("[done] " + java.time.LocalTime.now() + nl + formatReport(report) + nl);
                    statusLabel.setText("Completed");
                } else {
                    final Throwable cause = unwrap(error);
                    final String message = cause instanceof CancellationException
                            ? "Report cancelled."
                            : "Error: " + cause.getMessage();
                    outputArea.append("[error] " + java.time.LocalTime.now() + " — " + message + nl);
                    statusLabel.setText(message);
                }
                outputArea.setCaretPosition(outputArea.getDocument().getLength());
            }));
        });

        stopButton.addActionListener(event -> {
            if (currentTask[0] != null) {
                currentTask[0].cancel();
                statusLabel.setText("Cancelling...");
            }
        });

        clearButton.addActionListener(e -> outputArea.setText(""));

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static FSInteractiveStatLib createLib(final String selection) {
        if ("EventLoop".equals(selection)) {
            return new EventLoopInteractiveFSStatLib();
        }
        if ("Rx".equals(selection)) {
            return new RxInteractiveFSStatLib();
        }
        if ("VirtualThreads".equals(selection)) {
            return new VirtualThreadInteractiveFSStatLib();
        }
        return null;
    }

    private static String formatReport(final FSReport report) {
        final String lineSeparator = System.lineSeparator();
        final StringBuilder builder = new StringBuilder(256);
        builder.append("Directory: ")
                .append(report.directory())
                .append(lineSeparator)
                .append("Total files: ")
                .append(report.totalFiles())
                .append(lineSeparator);
        for (final FSReportBand band : report.bands()) {
            builder.append('[')
                    .append(band.lowerBoundInclusive())
                    .append(", ")
                    .append(band.upperBoundInclusive())
                    .append("] -> ")
                    .append(band.fileCount());
            if (band.overflowBand()) {
                builder.append(" (overflow)");
            }
            builder.append(lineSeparator);
        }
        return builder.toString();
    }

    private static Throwable unwrap(final Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return error;
    }
}

