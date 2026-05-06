package it.unibo.sampleapp.fsstat.virtualthreads;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.interactive.FSInteractiveStatLib;
import it.unibo.sampleapp.fsstat.interactive.FSReportAccumulator;
import it.unibo.sampleapp.fsstat.interactive.FSReportListener;
import it.unibo.sampleapp.fsstat.interactive.FSReportTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Virtual-thread interactive implementation with live updates and cancellation.
 */
public final class VirtualThreadInteractiveFSStatLib implements FSInteractiveStatLib {

    @Override
    public FSReportTask getFSReport(final Path directory, final long maxFileSize, final int numberOfBands,
            final FSReportListener listener) {
        validateArguments(directory, maxFileSize, numberOfBands);
        Objects.requireNonNull(listener, "listener");

        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new ReportTask(CompletableFuture.failedFuture(new IOException("Path does not exist: " + directory)));
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new ReportTask(CompletableFuture.failedFuture(new IOException("Path is not a directory: " + directory)));
        }

        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final CompletableFuture<FSReport> result = CompletableFuture
                .supplyAsync(() -> computeReport(directory, maxFileSize, numberOfBands, listener, cancelled))
                .handle((report, error) -> {
                    if (error == null) {
                        return report;
                    }
                    throw new CompletionException(unwrapError(error));
                });

        return new ReportTask(result, cancelled);
    }

    private static FSReport computeReport(final Path directory, final long maxFileSize, final int numberOfBands,
            final FSReportListener listener, final AtomicBoolean cancelled) {
        final FSReportAccumulator accumulator = new FSReportAccumulator(directory, maxFileSize, numberOfBands);
        final List<CompletableFuture<Void>> tasks = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                var paths = Files.walk(directory)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> tasks.add(CompletableFuture.runAsync(() -> {
                        if (cancelled.get()) {
                            return;
                        }
                        try {
                            final long size = Files.size(path);
                            accumulator.acceptSize(size);
                            listener.onUpdate(accumulator.snapshot());
                        } catch (final IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    }, executor)));

            for (final CompletableFuture<Void> task : tasks) {
                if (cancelled.get()) {
                    throw new CancellationException("Cancelled");
                }
                task.join();
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return accumulator.toReport();
    }

    private static void validateArguments(final Path directory, final long maxFileSize, final int numberOfBands) {
        Objects.requireNonNull(directory, "directory");
        if (maxFileSize < 0) {
            throw new IllegalArgumentException("maxFileSize must be non-negative");
        }
        if (numberOfBands <= 0) {
            throw new IllegalArgumentException("numberOfBands must be positive");
        }
        if (maxFileSize != Long.MAX_VALUE && numberOfBands > maxFileSize + 1) {
            throw new IllegalArgumentException("numberOfBands cannot exceed the number of size values in range");
        }
    }

    private static Throwable unwrapError(final Throwable error) {
        final Throwable cause = error instanceof CompletionException ? error.getCause() : error;
        if (cause instanceof UncheckedIOException uncheckedIOException) {
            return uncheckedIOException.getCause();
        }
        return cause;
    }

    private static final class ReportTask implements FSReportTask {

        private final CompletableFuture<FSReport> result;
        private final AtomicBoolean cancelled;

        private ReportTask(final CompletableFuture<FSReport> result) {
            this(result, new AtomicBoolean(false));
        }

        private ReportTask(final CompletableFuture<FSReport> result, final AtomicBoolean cancelled) {
            this.result = result;
            this.cancelled = cancelled;
        }

        @Override
        public CompletableFuture<FSReport> result() {
            return result;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                result.completeExceptionally(new CancellationException("Cancelled"));
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}

