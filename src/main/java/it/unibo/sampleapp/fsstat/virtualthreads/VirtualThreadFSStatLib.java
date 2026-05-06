package it.unibo.sampleapp.fsstat.virtualthreads;

import it.unibo.sampleapp.fsstat.FSReport;
import it.unibo.sampleapp.fsstat.FSReportBand;
import it.unibo.sampleapp.fsstat.FSStatLib;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Virtual-thread based implementation of {@link FSStatLib}.
 */
public final class VirtualThreadFSStatLib implements FSStatLib {

    /**
     * Asynchronously computes the filesystem report using virtual threads for file size collection.
     *
     * @param directory the root directory to inspect recursively
     * @param maxFileSize the upper bound, in bytes, for the bounded size bands
     * @param numberOfBands the number of bounded bands to create between 0 and {@code maxFileSize}
     * @return a future that completes with the filesystem report
     */
    @Override
    public CompletableFuture<FSReport> getFSReport(final Path directory, final long maxFileSize, final int numberOfBands) {
        validateArguments(directory, maxFileSize, numberOfBands);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return CompletableFuture.failedFuture(new IOException("Path does not exist: " + directory));
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return CompletableFuture.failedFuture(new IOException("Path is not a directory: " + directory));
        }

        return CompletableFuture
                .supplyAsync(() -> computeReport(directory, maxFileSize, numberOfBands))
                .handle((report, error) -> {
                    if (error == null) {
                        return report;
                    }
                    throw new CompletionException(unwrapError(error));
                });
    }

    private static FSReport computeReport(final Path directory, final long maxFileSize, final int numberOfBands) {
        final List<BandBounds> bounds = createBandBounds(maxFileSize, numberOfBands);
        final AtomicLongArray counts = new AtomicLongArray(bounds.size());
        final LongAdder totalFiles = new LongAdder();
        final List<CompletableFuture<Void>> tasks = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                var paths = Files.walk(directory)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> tasks.add(CompletableFuture.runAsync(() -> {
                        try {
                            final long size = Files.size(path);
                            totalFiles.increment();
                            final int index = bandIndexForSize(size, maxFileSize, numberOfBands);
                            counts.incrementAndGet(index);
                        } catch (final IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    }, executor)));

            for (final CompletableFuture<Void> task : tasks) {
                task.join();
            }
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }

        final List<FSReportBand> bands = new ArrayList<>(bounds.size());
        for (int index = 0; index < bounds.size(); index++) {
            final BandBounds bound = bounds.get(index);
            final long count = counts.get(index);
            bands.add(new FSReportBand(bound.lowerBoundInclusive(), bound.upperBoundInclusive(), count, bound.overflow()));
        }

        return new FSReport(directory, totalFiles.sum(), bands);
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

    private static List<BandBounds> createBandBounds(final long maxFileSize, final int numberOfBands) {
        final List<BandBounds> bounds = new ArrayList<>(numberOfBands + 1);
        final long inclusiveRange = maxFileSize + 1;
        final long baseBandSize = inclusiveRange / numberOfBands;
        final long remainder = inclusiveRange % numberOfBands;

        long lowerBound = 0;
        for (int index = 0; index < numberOfBands; index++) {
            final long bandSize = baseBandSize + (index < remainder ? 1 : 0);
            final long upperBound = bandSize == 0 ? lowerBound - 1 : lowerBound + bandSize - 1;
            bounds.add(new BandBounds(lowerBound, upperBound, false));
            lowerBound = upperBound + 1;
        }

        final long overflowLowerBound = maxFileSize == Long.MAX_VALUE ? Long.MAX_VALUE : maxFileSize + 1;
        bounds.add(new BandBounds(overflowLowerBound, Long.MAX_VALUE, true));
        return bounds;
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

    private record BandBounds(long lowerBoundInclusive, long upperBoundInclusive, boolean overflow) {
    }
}


