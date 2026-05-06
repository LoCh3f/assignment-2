package it.unibo.sampleapp;

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
        LOGGER.info(() -> "FSStat API ready. Concrete implementations will be added in the next tasks."
                + (args.length == 0 ? "" : " Received " + args.length + " argument(s)."));
    }
}
