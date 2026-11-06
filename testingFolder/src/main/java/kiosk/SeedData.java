package kiosk;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Generates FAKE queue history so the analytics page has something to show
 * before the real sensor has collected days of data.
 *
 * Every fake row is flagged is_seed = 1, so it can be wiped with
 * POST /api/seed/clear without touching real data.
 *
 * The shape of the fake data (lunch and dinner rushes, quiet weekends)
 * is invented. Change the numbers below to fit your real kiosk.
 */
public class SeedData {

    // Typical queue length by hour of day (index = hour, 0-23). Peaks at lunch and dinner.
    private static final double[] HOURLY_BASE = {
            0, 0, 0, 0, 0, 0, 0, 0,        // 12 AM - 7 AM (closed)
            1.5, 2, 3.5, 7, 10, 8,         // 8 AM - 1 PM  (lunch peak at noon)
            4, 3, 3.5, 6, 7, 4,            // 2 PM - 7 PM  (dinner bump around 5-6)
            2, 0, 0, 0                     // 8 PM - 11 PM
    };

    // Busyness multiplier by day, index 0 = Sunday ... 6 = Saturday
    private static final double[] DAY_FACTOR = {0.4, 1.0, 1.1, 1.0, 0.9, 0.8, 0.5};

    private static final int DAYS_OF_HISTORY = 28;   // four weeks back
    private static final int FIRST_HOUR = 8;         // kiosk opens 8 AM
    private static final int LAST_HOUR = 20;         // last snapshot hour: 8 PM

    /**
     * Wipes any old seed data, then generates fresh fake history
     * (one snapshot every 15 minutes during opening hours).
     * Returns how many rows were created.
     */
    public static int generate(Database db) throws SQLException {
        // Fixed seed number = the same "random" data every time, so demos are repeatable
        Random rng = new Random(42);
        int count = 0;

        // Hold the database lock for the whole run so the live scheduler
        // can't slip rows into the middle of our transaction.
        synchronized (db) {
            db.clearSeedData();
            db.beginBatch();
            try {
                LocalDate today = LocalDate.now();

                // Start 28 days ago and go up to yesterday (today is left for real data)
                for (int d = DAYS_OF_HISTORY; d >= 1; d--) {
                    LocalDate date = today.minusDays(d);
                    int dayIndex = date.getDayOfWeek().getValue() % 7; // 0 = Sunday

                    for (int hour = FIRST_HOUR; hour <= LAST_HOUR; hour++) {
                        for (int minute = 0; minute < 60; minute += 15) {
                            LocalDateTime when = date.atTime(hour, minute);

                            // Base busyness for this hour and day, plus some randomness
                            double expected = HOURLY_BASE[hour] * DAY_FACTOR[dayIndex];
                            int queue = (int) Math.max(0, Math.round(expected + rng.nextGaussian() * 1.2));

                            // Seconds per person: about 40, with a little variation, never under 20
                            double service = Math.max(20, 40 + rng.nextGaussian() * 5);

                            db.logSnapshotAt(when, queue, service, queue * service,
                                    CapacityConfig.getLevel(queue), true);
                            count++;
                        }
                    }
                }
                db.endBatch();   // commit everything in one go
            } catch (SQLException e) {
                db.abortBatch(); // something failed: undo the partial insert
                throw e;
            }
        }
        return count;
    }
}
