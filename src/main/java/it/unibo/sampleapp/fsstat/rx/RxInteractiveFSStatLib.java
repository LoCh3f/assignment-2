package it.unibo.sampleapp.fsstat.rx;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reactive interactive implementation with live updates and cancellation.
 */
public final class RxInteractiveFSStatLib implements FSInteractiveStatLib {

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
        final CompletableFuture<FSReport> result = new CompletableFuture<>();
        final FSReportAccumulator accumulator = new FSReportAccumulator(directory, maxFileSize, numberOfBands);

        final Single<FSReport> single = Single.fromCallable(() -> {
                    try (var paths = Files.walk(directory)) {
                        paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                                .forEach(path -> {
                                    if (cancelled.get()) {
                                        throw new CancellationException("Cancelled");
                                    }
                                    try {
                                        final long size = Files.size(path);
                                        accumulator.acceptSize(size);
                                        listener.onUpdate(accumulator.snapshot());
                                    } catch (final IOException exception) {
                                        throw new UncheckedIOException(exception);
                                    }
                                });
                    }
                    return accumulator.toReport();
                })
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(error -> Single.error(normalizeError(error)));

        final Disposable disposable = single.subscribe(result::complete, result::completeExceptionally);
        return new ReportTask(result, cancelled, disposable);
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

    private static Throwable normalizeError(final Throwable error) {
        final Throwable cause = error instanceof CompletionException ? error.getCause() : error;
        if (cause instanceof UncheckedIOException uncheckedIOException) {
            return uncheckedIOException.getCause();
        }
        return cause;
    }

    private static final class ReportTask implements FSReportTask {

        private final CompletableFuture<FSReport> result;
        private final AtomicBoolean cancelled;
        private final Disposable disposable;

        private ReportTask(final CompletableFuture<FSReport> result) {
            this(result, new AtomicBoolean(false), null);
        }

        private ReportTask(final CompletableFuture<FSReport> result, final AtomicBoolean cancelled,
                final Disposable disposable) {
            this.result = result;
            this.cancelled = cancelled;
            this.disposable = disposable;
        }

        @Override
        public CompletableFuture<FSReport> result() {
            return result;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                if (disposable != null) {
                    disposable.dispose();
                }
                result.completeExceptionally(new CancellationException("Cancelled"));
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}

