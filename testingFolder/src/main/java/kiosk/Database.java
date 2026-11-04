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
}
