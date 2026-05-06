package it.unibo.sampleapp;

import it.unibo.sampleapp.interactive.InteractiveFSStatApp;
import java.util.logging.Logger;

/**
 * Application entry point.
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private Main() {
        // Utility class.
    }

    /**
     * Launches the sample application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        LOGGER.info(() -> "Launching FSStat interactive menu.");
        InteractiveFSStatApp.main(new String[0]);
    }
}
