package it.unibo.sampleapp.fsstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for filesystem reports.
 */
class FSReportTest {

    private static final long INITIAL_TOTAL_FILES = 2L;
    private static final long NEGATIVE_TOTAL_FILES = -1L;
    private static final long BOUNDARY_MAX_SIZE = 99L;
    private static final long BOUNDARY_FILE_COUNT = 2L;
    private static final String TEMP_DIRECTORY_PREFIX = "fsstat-report";
    private static final Path REPORT_DIRECTORY = Path.of("report-test");

    @Test
    void reportShouldExposeImmutableCopyOfBands() throws IOException {
        final Path directory = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX);
        final List<FSReportBand> bands = new ArrayList<>();
        bands.add(FSReportBand.bounded(0, BOUNDARY_MAX_SIZE, BOUNDARY_FILE_COUNT));

        final FSReport report = new FSReport(directory, INITIAL_TOTAL_FILES, bands);
        bands.add(FSReportBand.overflow(100, 1));

        assertEquals(directory, report.directory());
        assertEquals(INITIAL_TOTAL_FILES, report.totalFiles());
        assertEquals(1, report.bands().size());
        assertEquals(FSReportBand.bounded(0, BOUNDARY_MAX_SIZE, BOUNDARY_FILE_COUNT), report.bands().getFirst());
    }

    @Test
    void reportShouldRejectInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new FSReport(null, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new FSReport(REPORT_DIRECTORY, NEGATIVE_TOTAL_FILES, List.of()));
        assertThrows(NullPointerException.class, () -> new FSReport(REPORT_DIRECTORY, 0, null));
    }
}

