package it.unibo.sampleapp.fsstat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

/**
 * Event-loop based implementation of {@link FSStatLib}.
 */
public final class EventLoopFSStatLib implements FSStatLib {

    private static final ExecutorService EVENT_LOOP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "fsstat-event-loop");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Asynchronously computes the filesystem report by walking the directory tree on a single event-loop thread.
     *
     * @param directory the root directory to inspect recursively
     * @param maxFileSize the upper bound, in bytes, for the bounded size bands
     * @param numberOfBands the number of bounded bands to create between 0 and {@code maxFileSize}
     * @return a future that completes with the filesystem report
     */
    @Override
    public CompletableFuture<FSReport> getFSReport(final Path directory, final long maxFileSize, final int numberOfBands) {
        validateArguments(directory, maxFileSize, numberOfBands);

        final CompletableFuture<FSReport> result = new CompletableFuture<>();
        final ReportState state = new ReportState(directory, maxFileSize, numberOfBands);
        final EventLoop eventLoop = new EventLoop();

        eventLoop.submit(() -> scanDirectory(directory, state, eventLoop, result));
        return result;
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

    private static void scanDirectory(final Path directory, final ReportState state, final EventLoop eventLoop,
            final CompletableFuture<FSReport> result) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            result.completeExceptionally(new IOException("Path does not exist: " + directory));
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            result.completeExceptionally(new IOException("Path is not a directory: " + directory));
            return;
        }

        final Queue<Path> pendingDirectories = new ArrayDeque<>();
        pendingDirectories.add(directory);
        processNextDirectory(state, eventLoop, result, pendingDirectories);
    }

    private static void processNextDirectory(final ReportState state, final EventLoop eventLoop,
            final CompletableFuture<FSReport> result, final Queue<Path> pendingDirectories) {
        final Path currentDirectory = pendingDirectories.poll();
        if (currentDirectory == null) {
            result.complete(state.toReport());
            return;
        }

        try (var entries = Files.newDirectoryStream(currentDirectory)) {
            for (final Path entry : entries) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    pendingDirectories.add(entry);
                } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    state.acceptFile(entry);
                }
            }
        } catch (final IOException exception) {
            result.completeExceptionally(exception);
            return;
        }

        eventLoop.submit(() -> processNextDirectory(state, eventLoop, result, pendingDirectories));
    }

    private static List<FSReportBand> createBands(final long maxFileSize, final int numberOfBands) {
        final List<FSReportBand> bands = new ArrayList<>(numberOfBands + 1);
        final long inclusiveRange = maxFileSize + 1;
        final long baseBandSize = inclusiveRange / numberOfBands;
        final long remainder = inclusiveRange % numberOfBands;

        long lowerBound = 0;
        for (int index = 0; index < numberOfBands; index++) {
            final long bandSize = baseBandSize + (index < remainder ? 1 : 0);
            final long upperBound = bandSize == 0 ? lowerBound - 1 : lowerBound + bandSize - 1;
            bands.add(FSReportBand.bounded(lowerBound, upperBound, 0));
            lowerBound = upperBound + 1;
        }

        final long overflowLowerBound = maxFileSize == Long.MAX_VALUE ? Long.MAX_VALUE : maxFileSize + 1;
        bands.add(FSReportBand.overflow(overflowLowerBound, 0));
        return bands;
    }

    private static final class ReportState {

        private final Path directory;
        private final long maxFileSize;
        private final List<FSReportBand> bands;
        private long totalFiles;

        private ReportState(final Path directory, final long maxFileSize, final int numberOfBands) {
            this.directory = directory;
            this.maxFileSize = maxFileSize;
            this.bands = createBands(maxFileSize, numberOfBands);
        }

        private void acceptFile(final Path file) throws IOException {
            final long size = Files.size(file);
            totalFiles++;
            final int bandIndex = bandIndexForSize(size, maxFileSize, bands.size() - 1);
            final FSReportBand currentBand = bands.get(bandIndex);
            bands.set(bandIndex, new FSReportBand(currentBand.lowerBoundInclusive(), currentBand.upperBoundInclusive(),
                    currentBand.fileCount() + 1, currentBand.overflowBand()));
        }

        private FSReport toReport() {
            return new FSReport(directory, totalFiles, bands);
        }

        private static int bandIndexForSize(final long size, final long maxFileSize, final int numberOfBands) {
            final long inclusiveRange = maxFileSize + 1;
            final long baseBandSize = inclusiveRange / numberOfBands;
            final long remainder = inclusiveRange % numberOfBands;

            long lowerBound = 0;
            for (int index = 0; index < numberOfBands; index++) {
                final long bandSize = baseBandSize + (index < remainder ? 1 : 0);
                final long upperBound = bandSize == 0 ? lowerBound - 1 : lowerBound + bandSize - 1;
                if (size >= lowerBound && size <= upperBound) {
                    return index;
                }
                lowerBound = upperBound + 1;
            }
            return numberOfBands;
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

