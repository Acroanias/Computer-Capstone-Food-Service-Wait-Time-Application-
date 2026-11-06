package kiosk;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all SQLite access. Everything that touches the database lives
 * here, so the rest of the server never has to write SQL.
 *
 * SQLite stores the whole database in ONE FILE (kiosk.db), created
 * automatically in the folder you run the server from.
 *
 * We keep a single shared connection and mark methods "synchronized"
 * so two web requests can't use it at the same instant.
 */
public class Database {

    // Timestamps are stored as readable local-time text, e.g. "2026-10-21 14:05:09"
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection conn;

    /** Opens (or creates) the database file and makes sure tables exist. */
    public Database(String filename) throws SQLException {
        // "jdbc:sqlite:<file>" is the connection string for SQLite
        conn = DriverManager.getConnection("jdbc:sqlite:" + filename);
        createTables();
    }

    /**
     * Creates the events table if it doesn't exist yet.
     * "IF NOT EXISTS" makes this safe to run on every startup.
     */
    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,   -- 'enter' | 'exit' | 'served'
                    timestamp  TEXT NOT NULL    -- local server time
                )
                """);

            // Periodic snapshots of the queue. This table powers the
            // capacity history and the weekly analytics. day_of_week and
            // hour get their own columns so "busiest time" queries are a
            // simple GROUP BY instead of date math on every row.
            st.execute("""
                CREATE TABLE IF NOT EXISTS queue_snapshots (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    queue_length     INTEGER NOT NULL,
                    avg_service_time REAL NOT NULL,   -- seconds
                    estimated_wait   REAL NOT NULL,   -- seconds
                    capacity_level   TEXT NOT NULL,   -- 'light' | 'moderate' | 'heavy'
                    day_of_week      INTEGER NOT NULL, -- 0=Sunday ... 6=Saturday
                    hour             INTEGER NOT NULL, -- 0-23
                    timestamp        TEXT NOT NULL,
                    is_seed          INTEGER NOT NULL DEFAULT 0  -- 1 = fake demo data
                )
                """);

            // Your existing kiosk.db was created before is_seed existed, and
            // CREATE TABLE IF NOT EXISTS won't add a column to an old table.
            // So we try to add it; if it's already there SQLite throws a
            // "duplicate column" error, which we deliberately ignore.
            try {
                st.execute("ALTER TABLE queue_snapshots "
                        + "ADD COLUMN is_seed INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException alreadyThere) {
                // column exists already: nothing to do
            }
        }
    }

    /** Saves one sensor event. */
    public synchronized void logEvent(String eventType) throws SQLException {
        // PreparedStatement with "?" placeholders is the safe way to insert
        // values. It prevents SQL injection and handles quoting for us.
        String sql = "INSERT INTO events (event_type, timestamp) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, LocalDateTime.now().format(TS_FORMAT));
            ps.executeUpdate();
        }
    }

    /** Returns the most recent events (newest first). Handy for debugging. */
    public synchronized List<Map<String, Object>> getRecentEvents(int limit) throws SQLException {
        String sql = "SELECT id, event_type, timestamp FROM events ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // LinkedHashMap keeps the keys in the order we add them
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("type", rs.getString("event_type"));
                    row.put("timestamp", rs.getString("timestamp"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Saves a snapshot of the queue as it is right now. */
    public synchronized void logSnapshot(int queueLength, double avgServiceSeconds,
                                         double estimatedWaitSeconds,
                                         String capacityLevel) throws SQLException {
        logSnapshotAt(LocalDateTime.now(), queueLength, avgServiceSeconds,
                estimatedWaitSeconds, capacityLevel, false);
    }

    /**
     * Saves a snapshot stamped with a specific time. The live scheduler
     * uses "now"; the seed-data generator uses times in the past.
     * isSeed marks fake rows so they can be deleted later without
     * touching real data.
     */
    public synchronized void logSnapshotAt(LocalDateTime when, int queueLength,
                                           double avgServiceSeconds,
                                           double estimatedWaitSeconds,
                                           String capacityLevel,
                                           boolean isSeed) throws SQLException {
        // Java numbers days 1=Monday ... 7=Sunday. We store 0=Sunday ... 6=Saturday,
        // so "% 7" turns Sunday (7) into 0 and leaves the rest unchanged.
        int dayOfWeek = when.getDayOfWeek().getValue() % 7;

        String sql = "INSERT INTO queue_snapshots "
                + "(queue_length, avg_service_time, estimated_wait, capacity_level, "
                + "day_of_week, hour, timestamp, is_seed) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, queueLength);
            ps.setDouble(2, avgServiceSeconds);
            ps.setDouble(3, estimatedWaitSeconds);
            ps.setString(4, capacityLevel);
            ps.setInt(5, dayOfWeek);
            ps.setInt(6, when.getHour());
            ps.setString(7, when.format(TS_FORMAT));
            ps.setInt(8, isSeed ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** Returns the most recent snapshots (newest first). */
    public synchronized List<Map<String, Object>> getRecentSnapshots(int limit) throws SQLException {
        String sql = "SELECT id, queue_length, avg_service_time, estimated_wait, "
                + "capacity_level, day_of_week, hour, timestamp "
                + "FROM queue_snapshots ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("queueLength", rs.getInt("queue_length"));
                    row.put("avgServiceSeconds", Math.round(rs.getDouble("avg_service_time")));
                    row.put("waitSeconds", Math.round(rs.getDouble("estimated_wait")));
                    row.put("capacityLevel", rs.getString("capacity_level"));
                    row.put("dayOfWeek", rs.getInt("day_of_week"));
                    row.put("hour", rs.getInt("hour"));
                    row.put("timestamp", rs.getString("timestamp"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * The heart of the analytics: average queue length and wait for every
     * (day of week, hour) slot across ALL stored snapshots.
     * One row per slot that has data, e.g. "Tuesdays at 12:00 averaged 8.2 people".
     * "samples" = how many snapshots went into that average (more = more trustworthy).
     */
    public synchronized List<Map<String, Object>> getHourlyPattern() throws SQLException {
        String sql = "SELECT day_of_week, hour, AVG(queue_length) AS avg_q, "
                + "AVG(estimated_wait) AS avg_w, COUNT(*) AS n "
                + "FROM queue_snapshots GROUP BY day_of_week, hour "
                + "ORDER BY day_of_week, hour";
        List<Map<String, Object>> rows = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dayOfWeek", rs.getInt("day_of_week"));
                row.put("hour", rs.getInt("hour"));
                row.put("avgQueueLength", rs.getDouble("avg_q"));
                row.put("avgWaitSeconds", rs.getDouble("avg_w"));
                row.put("samples", rs.getInt("n"));
                rows.add(row);
            }
        }
        return rows;
    }

    /** Deletes only the fake seed rows. Real data is never touched. Returns rows removed. */
    public synchronized int clearSeedData() throws SQLException {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate("DELETE FROM queue_snapshots WHERE is_seed = 1");
        }
    }

    // ----- Batch helpers: inserting 1,500 rows one-by-one is slow because
    // SQLite saves to disk after each insert. A transaction saves once at the end. -----

    public synchronized void beginBatch() throws SQLException {
        conn.setAutoCommit(false);
    }

    public synchronized void endBatch() throws SQLException {
        conn.commit();
        conn.setAutoCommit(true);
    }

    public synchronized void abortBatch() {
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // nothing more we can do
        }
    }
}