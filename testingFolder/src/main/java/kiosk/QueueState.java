package kiosk;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the LIVE state of the line, in memory:
 *   - how many people are in line right now
 *   - the last few service times (for a rolling average)
 *
 * Every method is "synchronized" because the web server handles
 * requests on multiple threads (the Arduino POSTing events while the
 * website polls /api/status). synchronized makes sure only one thread
 * touches the numbers at a time, so they can't get corrupted.
 */
public class QueueState {

    // Average over only the most recent N service times, so the estimate
    // adapts if the kiosk speeds up or slows down. (5-10 is a good range.)
    private static final int ROLLING_WINDOW = 8;

    // Used before we have any real data (e.g. right after server start).
    private static final double DEFAULT_SERVICE_SECONDS = 60.0;

    // If the gap between two "served" events is longer than this, the kiosk
    // was probably just idle (nobody in line), not slow. Ignore those gaps
    // so one lunch lull doesn't wreck the average.
    private static final double MAX_VALID_SERVICE_SECONDS = 600.0; // 10 min

    private int queueLength = 0;

    // Most recent service durations in seconds; oldest gets dropped first
    private final Deque<Double> recentServiceTimes = new ArrayDeque<>();

    // When the previous "served" event happened (-1 = none yet)
    private long lastServedAtMillis = -1;

    /**
     * Update the state based on an event from the Arduino.
     * Throws IllegalArgumentException for unknown event types so the
     * route handler can reply with a 400 error.
     */
    public synchronized void handleEvent(String type) {
        long now = System.currentTimeMillis(); // server clock (see EventPayload)

        switch (type) {
            case "enter":
                queueLength++;
                break;

            case "exit":
                // Math.max stops the count from ever going negative
                // if a sensor misfires
                queueLength = Math.max(0, queueLength - 1);
                break;

            case "served":
                queueLength = Math.max(0, queueLength - 1);
                recordServiceTime(now);
                break;

            default:
                throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    /**
     * Service time = time between two consecutive "served" events.
     * (If people are queued up, that gap is how long each one took.)
     */
    private void recordServiceTime(long nowMillis) {
        if (lastServedAtMillis > 0) {
            double seconds = (nowMillis - lastServedAtMillis) / 1000.0;

            if (seconds <= MAX_VALID_SERVICE_SECONDS) {
                recentServiceTimes.addLast(seconds);

                // Keep only the last ROLLING_WINDOW entries
                if (recentServiceTimes.size() > ROLLING_WINDOW) {
                    recentServiceTimes.removeFirst();
                }
            }
        }
        lastServedAtMillis = nowMillis;
    }

    /** Rolling average service time in seconds (or the default if no data). */
    public synchronized double getAvgServiceSeconds() {
        if (recentServiceTimes.isEmpty()) return DEFAULT_SERVICE_SECONDS;

        double sum = 0;
        for (double t : recentServiceTimes) sum += t;
        return sum / recentServiceTimes.size();
    }

    public synchronized int getQueueLength() {
        return queueLength;
    }

    /** Estimated wait = people in line x average time per person. */
    public synchronized double getEstimatedWaitSeconds() {
        return queueLength * getAvgServiceSeconds();
    }

    /** Manual override for demo day if the count drifts from a misfire. */
    public synchronized void reset() {
        queueLength = 0;
        recentServiceTimes.clear();
        lastServedAtMillis = -1;
    }
}
