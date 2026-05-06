package it.unibo.sampleapp.fsstat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for filesystem report bands.
 */
class FSReportBandTest {

    private static final long LOWER_BOUND = 0L;
    private static final long UPPER_BOUND = 99L;
    private static final long FILE_COUNT = 3L;
    private static final long OVERFLOW_LOWER_BOUND = 100L;
    private static final long OVERFLOW_FILE_COUNT = 7L;
    private static final long INVALID_LOWER_BOUND = -1L;
    private static final long VALID_UPPER_BOUND = 10L;
    private static final long VALID_FILE_COUNT = 1L;
    private static final long INVALID_UPPER_BOUND = 9L;

    @Test
    void boundedShouldCreateExpectedBand() {
        final FSReportBand band = FSReportBand.bounded(LOWER_BOUND, UPPER_BOUND, FILE_COUNT);

        assertEquals(LOWER_BOUND, band.lowerBoundInclusive());
        assertEquals(UPPER_BOUND, band.upperBoundInclusive());
        assertEquals(FILE_COUNT, band.fileCount());
        assertFalse(band.overflowBand());
    }

    @Test
    void overflowShouldCreateOverflowBand() {
        final FSReportBand band = FSReportBand.overflow(OVERFLOW_LOWER_BOUND, OVERFLOW_FILE_COUNT);

        assertEquals(OVERFLOW_LOWER_BOUND, band.lowerBoundInclusive());
        assertEquals(Long.MAX_VALUE, band.upperBoundInclusive());
        assertEquals(OVERFLOW_FILE_COUNT, band.fileCount());
        assertTrue(band.overflowBand());
    }

    @Test
    void constructorShouldRejectInvalidBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new FSReportBand(INVALID_LOWER_BOUND, VALID_UPPER_BOUND, VALID_FILE_COUNT, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FSReportBand(VALID_UPPER_BOUND, INVALID_UPPER_BOUND, VALID_FILE_COUNT, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FSReportBand(LOWER_BOUND, VALID_UPPER_BOUND, -VALID_FILE_COUNT, false));
    }
}

