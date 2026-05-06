package it.unibo.sampleapp.fsstat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Result of a filesystem statistics computation.
 *
 * @param directory the root directory that was analyzed
 * @param totalFiles total number of files found recursively under the directory
 * @param bands the size-band distribution, including the overflow band
 */
public record FSReport(Path directory, long totalFiles, List<FSReportBand> bands) {

    /**
     * Creates a filesystem report.
     */
    public FSReport {
        directory = Objects.requireNonNull(directory, "directory");
        if (totalFiles < 0) {
            throw new IllegalArgumentException("Total files must be non-negative");
        }
        bands = Objects.requireNonNull(bands, "bands");
        bands = List.copyOf(bands);
    }
}

