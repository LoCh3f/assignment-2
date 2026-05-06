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
import java.awt.FlowLayout;
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
        frame.setLayout(new BorderLayout());

        final JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JTextField directoryField = new JTextField(".", 25);
        final JTextField maxSizeField = new JTextField(String.valueOf(DEFAULT_MAX_SIZE), 8);
        final JTextField bandsField = new JTextField(String.valueOf(DEFAULT_BANDS), 4);
        final JComboBox<String> implementationBox = new JComboBox<>(new String[] {"EventLoop", "Rx", "VirtualThreads"});
        final JButton startButton = new JButton("Start");
        final JButton stopButton = new JButton("Stop");
        stopButton.setEnabled(false);

        inputPanel.add(new JLabel("Directory:"));
        inputPanel.add(directoryField);
        inputPanel.add(new JLabel("MaxFS:"));
        inputPanel.add(maxSizeField);
        inputPanel.add(new JLabel("Bands:"));
        inputPanel.add(bandsField);
        inputPanel.add(new JLabel("Mode:"));
        inputPanel.add(implementationBox);
        inputPanel.add(startButton);
        inputPanel.add(stopButton);

        final JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        final JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(REPORT_WIDTH, REPORT_HEIGHT));

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        final FSReportListener listener = report -> SwingUtilities.invokeLater(() -> {
            outputArea.setText(formatReport(report));
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

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            outputArea.setText("Starting report...\n");

            final FSReportTask task = lib.getFSReport(directory, maxFileSize, numberOfBands, listener);
            currentTask[0] = task;
            task.result().whenComplete((report, error) -> SwingUtilities.invokeLater(() -> {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                if (error == null) {
                    outputArea.setText(formatReport(report));
                } else {
                    final Throwable cause = unwrap(error);
                    final String message = cause instanceof CancellationException
                            ? "Report cancelled."
                            : "Error: " + cause.getMessage();
                    outputArea.setText(message + "\n" + outputArea.getText());
                }
            }));
        });

        stopButton.addActionListener(event -> {
            if (currentTask[0] != null) {
                currentTask[0].cancel();
            }
        });

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

