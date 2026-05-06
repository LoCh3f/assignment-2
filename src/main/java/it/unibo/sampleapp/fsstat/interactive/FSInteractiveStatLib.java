package it.unibo.sampleapp.fsstat.interactive;

import java.nio.file.Path;

/**
 * Interactive API for filesystem statistics with cancellation and live updates.
 */
@FunctionalInterface
public interface FSInteractiveStatLib {

    /**
     * Starts generating a report with progressive updates.
     *
     * @param directory the root directory to inspect recursively
     * @param maxFileSize the upper bound, in bytes, for the bounded size bands
     * @param numberOfBands the number of bounded bands to create between 0 and {@code maxFileSize}
     * @param listener callback invoked with partial report updates
     * @return a task handle for cancellation and completion tracking
     */
    FSReportTask getFSReport(Path directory, long maxFileSize, int numberOfBands, FSReportListener listener);
}

