package it.unibo.sampleapp.fsstat.interactive;

import it.unibo.sampleapp.fsstat.FSReport;
import java.util.concurrent.CompletableFuture;

/**
 * Handle returned by an interactive filesystem report computation.
 */
public interface FSReportTask {

    /**
     * @return the future that completes with the final report
     */
    CompletableFuture<FSReport> result();

    /**
     * Requests cancellation of the ongoing computation.
     */
    void cancel();

    /**
     * @return whether the computation has been cancelled
     */
    boolean isCancelled();
}

