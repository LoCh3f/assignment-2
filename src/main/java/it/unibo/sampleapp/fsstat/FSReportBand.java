package it.unibo.sampleapp.fsstat;

/**
 * A single size band in the filesystem report.
 *
 * <p>Bounded bands cover sizes inclusively between {@code lowerBoundInclusive} and
 * {@code upperBoundInclusive}. The overflow band represents files larger than the
 * configured maximum size.
 *
 * @param lowerBoundInclusive the inclusive lower bound of the band, in bytes
 * @param upperBoundInclusive the inclusive upper bound of the band, in bytes
 * @param fileCount the number of files in the band
 * @param overflowBand whether this is the overflow band for files larger than the maximum
 */
public record FSReportBand(
        long lowerBoundInclusive,
        long upperBoundInclusive,
        long fileCount,
        boolean overflowBand) {

    /**
     * Validates the band definition.
     */
    public FSReportBand {
        if (lowerBoundInclusive < 0) {
            throw new IllegalArgumentException("Lower bound must be non-negative");
        }
        if (upperBoundInclusive < lowerBoundInclusive) {
            throw new IllegalArgumentException("Upper bound must be greater than or equal to lower bound");
        }
        if (fileCount < 0) {
            throw new IllegalArgumentException("File count must be non-negative");
        }
    }

    /**
     * Creates a bounded, non-overflow band.
     *
     * @param lowerBoundInclusive the inclusive lower bound of the band, in bytes
     * @param upperBoundInclusive the inclusive upper bound of the band, in bytes
     * @param fileCount the number of files in the band
     * @return a bounded report band
     */
    public static FSReportBand bounded(final long lowerBoundInclusive, final long upperBoundInclusive, final long fileCount) {
        return new FSReportBand(lowerBoundInclusive, upperBoundInclusive, fileCount, false);
    }

    /**
     * Creates the overflow band for files larger than the maximum configured size.
     *
     * @param lowerBoundInclusive the lower bound from which files are considered overflow, in bytes
     * @param fileCount the number of files in the band
     * @return an overflow report band
     */
    public static FSReportBand overflow(final long lowerBoundInclusive, final long fileCount) {
        return new FSReportBand(lowerBoundInclusive, Long.MAX_VALUE, fileCount, true);
    }
}

