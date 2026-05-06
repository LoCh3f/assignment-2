package it.unibo.sampleapp.fsstat.rx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.interactive.FSReportTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the interactive reactive implementation.
 */
class RxInteractiveFSStatLibTest {

    private static final long MAX_FILE_SIZE = 9L;
    private static final int NUMBER_OF_BANDS = 2;
    private static final int SECOND_FILE_SIZE = 6;
    private static final int LARGE_FILE_COUNT = 300;
    private static final int TIMEOUT_SECONDS = 5;

    private final RxInteractiveFSStatLib lib = new RxInteractiveFSStatLib();

    @Test
    void shouldEmitUpdatesAndComplete() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Path rootDirectory = Files.createTempDirectory("fsstat-interactive-rx");
        Files.write(rootDirectory.resolve("a.bin"), new byte[1]);
        Files.write(rootDirectory.resolve("b.bin"), new byte[SECOND_FILE_SIZE]);

        final List<FSReport> updates = new CopyOnWriteArrayList<>();
        final FSReportTask task = lib.getFSReport(rootDirectory, MAX_FILE_SIZE, NUMBER_OF_BANDS, updates::add);
        final FSReport report = task.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertFalse(updates.isEmpty());
        assertEquals(report.totalFiles(), updates.get(updates.size() - 1).totalFiles());
    }

    @Test
    void shouldCancelReport() throws IOException {
        final Path rootDirectory = Files.createTempDirectory("fsstat-interactive-rx-cancel");
        for (int i = 0; i < LARGE_FILE_COUNT; i++) {
            Files.write(rootDirectory.resolve("file-" + i + ".bin"), new byte[1]);
        }

        final FSReportTask task = lib.getFSReport(rootDirectory, MAX_FILE_SIZE, NUMBER_OF_BANDS, report -> { });
        task.cancel();

        final Exception exception = assertThrows(Exception.class,
                () -> task.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        final Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
        assertInstanceOf(CancellationException.class, cause);
    }
}

