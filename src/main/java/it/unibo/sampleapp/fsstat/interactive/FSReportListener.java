package it.unibo.sampleapp.fsstat.interactive;

import it.unibo.sampleapp.fsstat.FSReport;

/**
 * Receives progressive updates while a filesystem report is generated.
 */
@FunctionalInterface
public interface FSReportListener {

    /**
     * Called with a partial report snapshot.
     *
     * @param report the current report snapshot
     */
    void onUpdate(FSReport report);
}

