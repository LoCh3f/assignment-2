package it.unibo.sampleapp.fsstat.interactive;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.FSReportBand;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Accumulates filesystem statistics to produce partial or final reports.
 */
public final class FSReportAccumulator {

    private final Path directory;
    private final BandLayout bandLayout;
    private final AtomicLongArray counts;
    private final LongAdder totalFiles = new LongAdder();

    /**
     * Creates a new accumulator.
     *
     * @param directory the inspected directory
     * @param maxFileSize the maximum size for bounded bands
     * @param numberOfBands the number of bounded bands
     */
    public FSReportAccumulator(final Path directory, final long maxFileSize, final int numberOfBands) {
        this.directory = directory;
        this.bandLayout = BandLayout.create(maxFileSize, numberOfBands);
        this.counts = new AtomicLongArray(bandLayout.ranges().size());
    }

    /**
     * Records a file size in the report.
     *
     * @param size the file size, in bytes
     */
    public void acceptSize(final long size) {
        totalFiles.increment();
        counts.incrementAndGet(bandLayout.bandIndexForSize(size));
    }

    /**
     * @return a snapshot report for the current counts
     */
    public FSReport snapshot() {
        final List<FSReportBand> bands = new ArrayList<>(bandLayout.ranges().size());
        for (int index = 0; index < bandLayout.ranges().size(); index++) {
            final BandLayout.BandRange range = bandLayout.ranges().get(index);
            bands.add(new FSReportBand(range.lowerBoundInclusive(), range.upperBoundInclusive(), counts.get(index),
                    range.overflow()));
        }
        return new FSReport(directory, totalFiles.sum(), bands);
    }

    /**
     * @return the final report for the current counts
     */
    public FSReport toReport() {
        return snapshot();
    }
}

