package it.unibo.sampleapp.fsstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Tests for the shared filesystem statistics API contract.
 */
class FSStatLibTest {

    private static final long MAX_FILE_SIZE = 100L;
    private static final int BAND_COUNT = 1;
    private static final long BAND_LOWER_BOUND = 0L;

    @Test
    void functionalInterfaceShouldAcceptSimpleImplementation() throws IOException {
        final Path rootDirectory = Files.createTempDirectory("fsstat-lib");
        final FSStatLib lib = (directory, maxFileSize, numberOfBands) -> CompletableFuture.completedFuture(
                new FSReport(directory, numberOfBands, java.util.List.of(
                        FSReportBand.bounded(BAND_LOWER_BOUND, maxFileSize, numberOfBands))));

        final FSReport report = lib.getFSReport(rootDirectory, MAX_FILE_SIZE, BAND_COUNT).join();

        assertEquals(rootDirectory, report.directory());
        assertEquals(BAND_COUNT, report.totalFiles());
        assertTrue(report.bands().contains(FSReportBand.bounded(BAND_LOWER_BOUND, MAX_FILE_SIZE, BAND_COUNT)));
    }
}

