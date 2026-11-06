package kiosk;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Every N seconds, saves a snapshot of the live queue into the
 * queue_snapshots table. Over days this builds the history that the
 * analytics (peak times, projections) will be computed from.
 *
 * It runs on its own background thread, separate from the web requests,
 * so it never slows down the Arduino or the website.
 */
public class SnapshotScheduler {

    private final QueueState state;
    private final Database db;
    private final int intervalSeconds;

    public SnapshotScheduler(QueueState state, Database db, int intervalSeconds) {
        this.state = state;
        this.db = db;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        // A daemon thread won't keep the program alive after you stop the
        // server, so Ctrl+C shuts everything down cleanly.
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread t = new Thread(task, "snapshot-scheduler");
                    t.setDaemon(true);
                    return t;
                });

        // Run takeSnapshot() immediately, then again every intervalSeconds
        scheduler.scheduleAtFixedRate(this::takeSnapshot,
                0, intervalSeconds, TimeUnit.SECONDS);

        System.out.println("Snapshots every " + intervalSeconds + " seconds");
    }

    private void takeSnapshot() {
        // IMPORTANT: catch everything. If a scheduled task throws an
        // uncaught exception, Java silently cancels ALL future runs, and
        // snapshots would just stop with no error shown.
        try {
            int queueLength = state.getQueueLength();
            db.logSnapshot(
                    queueLength,
                    state.getAvgServiceSeconds(),
                    state.getEstimatedWaitSeconds(),
                    CapacityConfig.getLevel(queueLength));
        } catch (Exception e) {
            System.out.println("Snapshot failed: " + e.getMessage());
        }
    }
}
