package kiosk;

/**
 * Turns a raw queue length into a friendly label for the website:
 * "light", "moderate", or "heavy".
 *
 * The thresholds below are just starting guesses. Once you test at your
 * real kiosk, watch what a "normal busy" line looks like and adjust
 * these two numbers to match.
 */
public class CapacityConfig {

    // Up to this many people in line = "light"
    public static final int LIGHT_MAX = 3;

    // Up to this many = "moderate". Anything above = "heavy"
    public static final int MODERATE_MAX = 8;

    public static String getLevel(int queueLength) {
        if (queueLength <= LIGHT_MAX) return "light";
        if (queueLength <= MODERATE_MAX) return "moderate";
        return "heavy";
    }
}
