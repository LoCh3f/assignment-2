package it.unibo.sampleapp.fsstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the event-loop based filesystem statistics implementation.
 */
class EventLoopFSStatLibTest {

    private static final long MAX_FILE_SIZE = 9L;
    private static final int NUMBER_OF_BANDS = 2;
    private static final long TOTAL_FILES = 3L;
    private static final long OVERFLOW_SIZE = 11L;
    private static final long FIRST_FILE_SIZE = 1L;
    private static final long SECOND_FILE_SIZE = 6L;
    private static final long EMPTY_DIRECTORY_FILE_COUNT = 0L;
    private static final long FIRST_BAND_START = 0L;
    private static final long FIRST_BAND_END = 4L;
    private static final long SECOND_BAND_START = 5L;
    private static final long SECOND_BAND_END = 9L;
    private static final long OVERFLOW_BAND_START = 10L;
    private static final long ZERO_COUNT = 0L;
    private static final long ONE_COUNT = 1L;
    private static final long INVALID_MAX_FILE_SIZE = -1L;
    private static final int IMPOSSIBLE_BAND_COUNT = 2;
    private static final int ZERO_BAND_COUNT = 0;

    private final EventLoopFSStatLib lib = new EventLoopFSStatLib();

    @Test
    void shouldCountFilesInNestedDirectories()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Path rootDirectory = Files.createTempDirectory("fsstat-event-loop-nested");
        final Path nestedDirectory = Files.createDirectory(rootDirectory.resolve("nested"));
        final Path deeperDirectory = Files.createDirectory(nestedDirectory.resolve("deeper"));
        Files.write(rootDirectory.resolve("root.bin"), new byte[(int) FIRST_FILE_SIZE]);
        Files.write(nestedDirectory.resolve("nested.bin"), new byte[(int) SECOND_FILE_SIZE]);
        Files.write(deeperDirectory.resolve("overflow.bin"), new byte[(int) OVERFLOW_SIZE]);

        final FSReport report = lib.getFSReport(rootDirectory, MAX_FILE_SIZE, NUMBER_OF_BANDS)
                .get(5, TimeUnit.SECONDS);

        assertEquals(rootDirectory, report.directory());
        assertEquals(TOTAL_FILES, report.totalFiles());
        assertEquals(List.of(
                FSReportBand.bounded(FIRST_BAND_START, FIRST_BAND_END, ONE_COUNT),
                FSReportBand.bounded(SECOND_BAND_START, SECOND_BAND_END, ONE_COUNT),
                FSReportBand.overflow(OVERFLOW_BAND_START, ONE_COUNT)), report.bands());
    }

    @Test
    void shouldReturnZeroCountsForEmptyDirectory()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Path rootDirectory = Files.createTempDirectory("fsstat-event-loop-empty");

        final FSReport report = lib.getFSReport(rootDirectory, MAX_FILE_SIZE, NUMBER_OF_BANDS)
                .get(5, TimeUnit.SECONDS);

        assertEquals(rootDirectory, report.directory());
        assertEquals(EMPTY_DIRECTORY_FILE_COUNT, report.totalFiles());
        assertEquals(List.of(
                FSReportBand.bounded(FIRST_BAND_START, FIRST_BAND_END, ZERO_COUNT),
                FSReportBand.bounded(SECOND_BAND_START, SECOND_BAND_END, ZERO_COUNT),
                FSReportBand.overflow(OVERFLOW_BAND_START, ZERO_COUNT)), report.bands());
    }

    @Test
    void shouldFailForMissingDirectory() {
        final Path missingDirectory = Path.of("missing-event-loop-directory");

        final CompletionException exception = assertThrows(CompletionException.class,
                () -> lib.getFSReport(missingDirectory, MAX_FILE_SIZE, NUMBER_OF_BANDS).join());

        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void shouldValidateArguments() {
        final Path rootDirectory = Path.of("root");

        assertThrows(NullPointerException.class, () -> lib.getFSReport(null, MAX_FILE_SIZE, NUMBER_OF_BANDS));
        assertThrows(IllegalArgumentException.class,
                () -> lib.getFSReport(rootDirectory, INVALID_MAX_FILE_SIZE, NUMBER_OF_BANDS));
        assertThrows(IllegalArgumentException.class,
                () -> lib.getFSReport(rootDirectory, MAX_FILE_SIZE, ZERO_BAND_COUNT));
    }

    @Test
    void shouldRejectImpossibleBandCounts() {
        final Path rootDirectory = Path.of("root");

        assertThrows(IllegalArgumentException.class,
                () -> lib.getFSReport(rootDirectory, ZERO_COUNT, IMPOSSIBLE_BAND_COUNT));
    }
}

