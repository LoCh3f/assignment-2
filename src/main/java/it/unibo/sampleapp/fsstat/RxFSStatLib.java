package it.unibo.sampleapp.fsstat;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
import java.util.stream.BaseStream;

/**
 * Reactive implementation of {@link FSStatLib} using RxJava.
 */
public final class RxFSStatLib implements FSStatLib {

    /**
     * Asynchronously computes the filesystem report by walking the directory tree on an Rx scheduler.
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

        return Single.using(
                        () -> Files.walk(directory),
                        stream -> Flowable.fromStream(stream)
                                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                                .map(path -> {
                                    try {
                                        return Files.size(path);
                                    } catch (final IOException exception) {
                                        throw new UncheckedIOException(exception);
                                    }
                                })
                                .collect(() -> new ReportAccumulator(directory, maxFileSize, numberOfBands),
                                        ReportAccumulator::acceptSize)
                                .map(ReportAccumulator::toReport),
                        BaseStream::close)
                .subscribeOn(Schedulers.io())
                .onErrorResumeNext(error -> Single.error(normalizeError(error)))
                .toCompletionStage()
                .toCompletableFuture();
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
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return normalizeError(completionException.getCause());
        }
        if (error instanceof UncheckedIOException uncheckedIOException) {
            return uncheckedIOException.getCause();
        }
        return error;
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

    private static final class ReportAccumulator {

        private final Path directory;
        private final long maxFileSize;
        private final List<FSReportBand> bands;
        private long totalFiles;

        private ReportAccumulator(final Path directory, final long maxFileSize, final int numberOfBands) {
            this.directory = directory;
            this.maxFileSize = maxFileSize;
            this.bands = createBands(maxFileSize, numberOfBands);
        }

        private void acceptSize(final long size) {
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
}

