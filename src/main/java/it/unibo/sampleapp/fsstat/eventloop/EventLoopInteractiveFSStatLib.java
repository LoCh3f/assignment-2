package it.unibo.sampleapp.fsstat.eventloop;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.interactive.FSInteractiveStatLib;
import it.unibo.sampleapp.fsstat.interactive.FSReportAccumulator;
import it.unibo.sampleapp.fsstat.interactive.FSReportListener;
import it.unibo.sampleapp.fsstat.interactive.FSReportTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event-loop interactive implementation with live updates and cancellation.
 */
public final class EventLoopInteractiveFSStatLib implements FSInteractiveStatLib {

    private static final String CANCELLED_MESSAGE = "Cancelled";
    private static final ExecutorService EVENT_LOOP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "fsstat-interactive-event-loop");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public FSReportTask getFSReport(final Path directory, final long maxFileSize, final int numberOfBands,
            final FSReportListener listener) {
        validateArguments(directory, maxFileSize, numberOfBands);
        Objects.requireNonNull(listener, "listener");

        final CompletableFuture<FSReport> result = new CompletableFuture<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final EventLoop eventLoop = new EventLoop();
        final FSReportAccumulator accumulator = new FSReportAccumulator(directory, maxFileSize, numberOfBands);
        final ReportTask task = new ReportTask(result, cancelled, eventLoop);

        eventLoop.submit(() -> scanDirectory(directory, accumulator, listener, task));
        return task;
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

    private static void scanDirectory(final Path directory, final FSReportAccumulator accumulator,
            final FSReportListener listener, final ReportTask task) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            task.fail(new IOException("Path does not exist: " + directory));
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            task.fail(new IOException("Path is not a directory: " + directory));
            return;
        }

        final Queue<Path> pendingDirectories = new ArrayDeque<>();
        pendingDirectories.add(directory);
        processNextDirectory(accumulator, listener, task, pendingDirectories);
    }

    private static void processNextDirectory(final FSReportAccumulator accumulator, final FSReportListener listener,
            final ReportTask task, final Queue<Path> pendingDirectories) {
        if (task.isCancelled()) {
            task.fail(new CancellationException(CANCELLED_MESSAGE));
            return;
        }

        final Path currentDirectory = pendingDirectories.poll();
        if (currentDirectory == null) {
            task.complete(accumulator.toReport());
            return;
        }

        try (var entries = Files.newDirectoryStream(currentDirectory)) {
            for (final Path entry : entries) {
                if (task.isCancelled()) {
                    task.fail(new CancellationException(CANCELLED_MESSAGE));
                    return;
                }
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    pendingDirectories.add(entry);
                } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    accumulator.acceptSize(Files.size(entry));
                    listener.onUpdate(accumulator.snapshot());
                }
            }
        } catch (final IOException exception) {
            task.fail(exception);
            return;
        }

        task.eventLoop().submit(() -> processNextDirectory(accumulator, listener, task, pendingDirectories));
    }

    private static final class ReportTask implements FSReportTask {

        private final CompletableFuture<FSReport> result;
        private final AtomicBoolean cancelled;
        private final EventLoop eventLoop;

        private ReportTask(final CompletableFuture<FSReport> result, final AtomicBoolean cancelled, final EventLoop eventLoop) {
            this.result = result;
            this.cancelled = cancelled;
            this.eventLoop = eventLoop;
        }

        @Override
        public CompletableFuture<FSReport> result() {
            return result;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                eventLoop.submit(() -> fail(new CancellationException(CANCELLED_MESSAGE)));
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        private void complete(final FSReport report) {
            result.complete(report);
        }

        private void fail(final Throwable error) {
            result.completeExceptionally(error);
        }

        private EventLoop eventLoop() {
            return eventLoop;
        }
    }

    private static final class EventLoop {

        private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        private void submit(final Runnable task) {
            queue.add(task);
            schedule();
        }

        private void schedule() {
            if (running.compareAndSet(false, true)) {
                EVENT_LOOP_EXECUTOR.execute(this::drain);
            }
        }

        private void drain() {
            try {
                Runnable task = queue.poll();
                while (task != null) {
                    task.run();
                    task = queue.poll();
                }
            } finally {
                running.set(false);
                if (!queue.isEmpty()) {
                    schedule();
                }
            }
        }
    }
}

