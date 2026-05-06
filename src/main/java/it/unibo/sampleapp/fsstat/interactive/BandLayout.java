package it.unibo.sampleapp.fsstat.interactive;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines band boundaries for filesystem size distribution.
 */
public final class BandLayout {

    private final List<BandRange> ranges;

    private BandLayout(final List<BandRange> ranges) {
        this.ranges = ranges;
    }

    /**
     * Creates the band layout for a given maximum size and band count.
     *
     * @param maxFileSize the maximum size for bounded bands
     * @param numberOfBands the number of bounded bands
     * @return the band layout
     */
    static BandLayout create(final long maxFileSize, final int numberOfBands) {
        final List<BandRange> ranges = new ArrayList<>(numberOfBands + 1);
        final long inclusiveRange = maxFileSize + 1;
        final long baseBandSize = inclusiveRange / numberOfBands;
        final long remainder = inclusiveRange % numberOfBands;

        long lowerBound = 0;
        for (int index = 0; index < numberOfBands; index++) {
            final long bandSize = baseBandSize + (index < remainder ? 1 : 0);
            final long upperBound = bandSize == 0 ? lowerBound - 1 : lowerBound + bandSize - 1;
            ranges.add(new BandRange(lowerBound, upperBound, false));
            lowerBound = upperBound + 1;
        }

        final long overflowLowerBound = maxFileSize == Long.MAX_VALUE ? Long.MAX_VALUE : maxFileSize + 1;
        ranges.add(new BandRange(overflowLowerBound, Long.MAX_VALUE, true));
        return new BandLayout(ranges);
    }

    /**
     * @return the configured band ranges
     */
    List<BandRange> ranges() {
        return ranges;
    }

    /**
     * Returns the band index for the provided size.
     *
     * @param size the file size
     * @return the band index containing the size
     */
    int bandIndexForSize(final long size) {
        for (int index = 0; index < ranges.size(); index++) {
            final BandRange range = ranges.get(index);
            if (range.matches(size)) {
                return index;
            }
        }
        return ranges.size() - 1;
    }

    record BandRange(long lowerBoundInclusive, long upperBoundInclusive, boolean overflow) {
        boolean matches(final long size) {
            return size >= lowerBoundInclusive && size <= upperBoundInclusive;
        }
    }
}

