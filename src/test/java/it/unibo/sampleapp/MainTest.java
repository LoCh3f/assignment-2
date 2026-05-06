package it.unibo.sampleapp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Tests for the application entry point.
 */
class MainTest {

    @Test
    void mainShouldRunWithoutErrors() {
        assertDoesNotThrow(() -> Main.main(new String[0]));
        assertDoesNotThrow(() -> Main.main(new String[] {"alpha", "beta"}));
    }
}

