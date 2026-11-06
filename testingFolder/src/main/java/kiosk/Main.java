package kiosk;

import io.javalin.Javalin;

import java.util.Map;
import java.sql.SQLException;

/**
 * Entry point. Starts the web server and defines the routes (URLs).
 *
 * Run with:   mvn compile exec:java
 *
 * Routes (Stage 4):
 *   POST /api/event   <- the Arduino sends sensor events here
 *   GET  /api/status  <- the website polls this for wait time + capacity
 *   POST /api/reset   <- manual reset for demo day
 *   GET  /api/events/recent <- last 20 logged events (debugging)
 *   GET  /api/snapshots/recent <- last 20 queue snapshots (debugging)
 *   GET  /api/analytics/peak <- busiest times of the week
 *   GET  /api/analytics/projection <- projected wait for the next hours
 *   POST /api/seed <- load fake history for demos
 *   POST /api/seed/clear <- remove the fake history again
 */
public class Main {

    // One shared live-state object for the whole server
    static final QueueState state = new QueueState();

    public static void main(String[] args) throws SQLException {

        // Opens kiosk.db (created automatically on first run).
        // If this fails the server stops right away, which is what we
        // want: better to know at startup than halfway through a demo.
        Database db = new Database("kiosk.db");

        // Start the background task that saves a snapshot of the queue
        // every 120 seconds. For quick testing, lower this to 10.
        new SnapshotScheduler(state, db, 120).start();

        // Reads the stored snapshots and computes peak times + projections
        AnalyticsService analytics = new AnalyticsService(db);

        Javalin app = Javalin.create(config -> {
            // CORS: lets a website served from a different origin (e.g. a
            // file:// page or another port) call our API without the browser
            // blocking it. "anyHost" is fine for a class project on a LAN;
            // you'd lock this down in a real deployment.
            config.bundledPlugins.enableCors(cors ->
                    cors.addRule(rule -> rule.anyHost()));
        });

        // ---------- Arduino -> server ----------
        app.post("/api/event", ctx -> {
            EventPayload event = ctx.bodyAsClass(EventPayload.class);

            // Basic validation so a bad request can't crash us
            if (event == null || event.type == null) {
                ctx.status(400).json(Map.of("error", "Missing 'type' field"));
                return;
            }

            try {
                // 1) Update the live in-memory state FIRST. This is what the
                //    website sees, and it must never depend on the database.
                state.handleEvent(event.type);
                System.out.println("Event: " + event.type
                        + " | queue = " + state.getQueueLength());

                // 2) Then save the event to the database for history.
                //    If the DB write fails we log it but still reply OK,
                //    so a database hiccup can't break live counting.
                try {
                    db.logEvent(event.type);
                } catch (SQLException dbError) {
                    System.out.println("DB write failed: " + dbError.getMessage());
                }
                ctx.status(200).json(Map.of("ok", true));
            } catch (IllegalArgumentException e) {
                // Unknown event type
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        // ---------- Website -> server ----------
        app.get("/api/status", ctx -> {
            int queueLength = state.getQueueLength();
            double waitSeconds = state.getEstimatedWaitSeconds();

            ctx.json(Map.of(
                    "queueLength", queueLength,
                    "waitSeconds", Math.round(waitSeconds),
                    "waitMinutes", Math.round(waitSeconds / 60.0 * 10) / 10.0, // 1 decimal
                    "avgServiceSeconds", Math.round(state.getAvgServiceSeconds()),
                    "capacityLevel", CapacityConfig.getLevel(queueLength)
            ));
        });

        // ---------- Debugging: peek at what's been logged ----------
        app.get("/api/events/recent", ctx -> {
            ctx.json(db.getRecentEvents(20));
        });

        app.get("/api/snapshots/recent", ctx -> {
            ctx.json(db.getRecentSnapshots(20));
        });

        // ---------- Analytics (website analytics page) ----------
        app.get("/api/analytics/peak", ctx -> {
            ctx.json(analytics.getPeakTimes());
        });

        app.get("/api/analytics/projection", ctx -> {
            ctx.json(analytics.getProjection());
        });

        // ---------- Fake data for demos ----------
        // Generates 4 weeks of fake snapshots so analytics aren't empty.
        // Calling it again replaces the old fake data instead of piling up.
        app.post("/api/seed", ctx -> {
            int rows = SeedData.generate(db);
            System.out.println("Seeded " + rows + " fake snapshots.");
            ctx.json(Map.of("seededRows", rows));
        });

        // Removes ONLY fake rows (real sensor data is untouched).
        app.post("/api/seed/clear", ctx -> {
            int removed = db.clearSeedData();
            System.out.println("Removed " + removed + " fake snapshots.");
            ctx.json(Map.of("removedRows", removed));
        });

        // ---------- Demo-day safety net ----------
        app.post("/api/reset", ctx -> {
            state.reset();
            System.out.println("State reset manually.");
            ctx.json(Map.of("ok", true));
        });

        // Bind to 0.0.0.0 (all network interfaces), NOT just localhost.
        // Otherwise the Arduino on the WiFi network can't reach the server.
        app.start("0.0.0.0", 7000);
        System.out.println("Kiosk server running on port 7000");
    }
}