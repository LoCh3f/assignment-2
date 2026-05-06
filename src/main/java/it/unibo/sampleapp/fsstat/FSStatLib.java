package it.unibo.sampleapp.fsstat;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Shared asynchronous API for filesystem statistics computation.
 */
@FunctionalInterface
public interface FSStatLib {

    /**
     * Asynchronously computes a report for the given directory.
     *
     * @param directory the root directory to inspect recursively
     * @param maxFileSize the upper bound, in bytes, for the bounded size bands
     * @param numberOfBands the number of bounded bands to create between 0 and {@code maxFileSize}
     * @return a future completing with the filesystem report
     */
    CompletableFuture<FSReport> getFSReport(Path directory, long maxFileSize, int numberOfBands);
}


