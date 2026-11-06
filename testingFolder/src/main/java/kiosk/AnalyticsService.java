package kiosk;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the stored queue snapshots into the two things the website's
 * analytics page needs:
 *   1. Peak times  - when is the line usually longest?
 *   2. Projections - what will the wait probably be over the next few hours?
 *
 * There's no machine learning here on purpose. Everything is an average of
 * past data for the same day of the week and hour, which is easy to explain
 * in a presentation: "we project using historical averages for that time slot."
 */
public class AnalyticsService {

    private static final String[] DAY_NAMES =
            {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private static final int TOP_SLOTS = 5;         // how many "busiest times" to list
    private static final int PROJECTION_HOURS = 6;  // how far ahead to project

    private final Database db;

    public AnalyticsService(Database db) {
        this.db = db;
    }

    // ---------------------------------------------------------------
    // Peak times
    // ---------------------------------------------------------------

    /**
     * Returns:
     *   topSlots  - the 5 busiest (day, hour) slots overall
     *   peakByDay - the busiest hour for each day of the week
     *   pattern   - every slot with data (use this for a heatmap/bar chart)
     */
    public Map<String, Object> getPeakTimes() throws SQLException {
        List<Map<String, Object>> pattern = db.getHourlyPattern();

        // Busiest overall: sort a copy by average queue, biggest first
        List<Map<String, Object>> sorted = new ArrayList<>(pattern);
        sorted.sort((a, b) -> Double.compare(num(b, "avgQueueLength"), num(a, "avgQueueLength")));

        List<Map<String, Object>> topSlots = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_SLOTS, sorted.size()); i++) {
            topSlots.add(describe(sorted.get(i)));
        }

        // Busiest hour within each day of the week
        List<Map<String, Object>> peakByDay = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            Map<String, Object> best = null;
            for (Map<String, Object> row : pattern) {
                if ((int) row.get("dayOfWeek") != day) continue;
                if (best == null || num(row, "avgQueueLength") > num(best, "avgQueueLength")) {
                    best = row;
                }
            }
            if (best != null) peakByDay.add(describe(best));
        }

        // The full table, formatted for the frontend
        List<Map<String, Object>> fullPattern = new ArrayList<>();
        for (Map<String, Object> row : pattern) fullPattern.add(describe(row));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topSlots", topSlots);
        result.put("peakByDay", peakByDay);
        result.put("pattern", fullPattern);
        return result;
    }

    // ---------------------------------------------------------------
    // Projections
    // ---------------------------------------------------------------

    /**
     * For each of the next few hours (starting with the current one),
     * projects the wait as the historical average for that same
     * day-of-week + hour. If we have no history for a slot,
     * hasData is false and the projection fields are null.
     */
    public Map<String, Object> getProjection() throws SQLException {
        // Index the pattern for quick lookup: key = dayOfWeek * 24 + hour
        Map<Integer, Map<String, Object>> lookup = new LinkedHashMap<>();
        for (Map<String, Object> row : db.getHourlyPattern()) {
            lookup.put((int) row.get("dayOfWeek") * 24 + (int) row.get("hour"), row);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> hours = new ArrayList<>();

        for (int i = 0; i < PROJECTION_HOURS; i++) {
            LocalDateTime slot = now.plusHours(i);   // plusHours handles wrapping past midnight
            int day = slot.getDayOfWeek().getValue() % 7;
            int hour = slot.getHour();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dayOfWeek", day);
            entry.put("dayName", DAY_NAMES[day]);
            entry.put("hour", hour);
            entry.put("hourLabel", hourLabel(hour));

            Map<String, Object> history = lookup.get(day * 24 + hour);
            if (history == null) {
                entry.put("hasData", false);
                entry.put("projectedQueueLength", null);
                entry.put("projectedWaitMinutes", null);
                entry.put("capacityLevel", null);
                entry.put("samples", 0);
            } else {
                double avgQueue = num(history, "avgQueueLength");
                entry.put("hasData", true);
                entry.put("projectedQueueLength", round1(avgQueue));
                entry.put("projectedWaitMinutes", round1(num(history, "avgWaitSeconds") / 60.0));
                entry.put("capacityLevel", CapacityConfig.getLevel((int) Math.round(avgQueue)));
                entry.put("samples", history.get("samples"));
            }
            hours.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basedOn", "historical average for the same day of week and hour");
        result.put("hours", hours);
        return result;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Reads a numeric value out of a row map as a double. */
    private static double num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).doubleValue();
    }

    /** Rounds to one decimal place (2.456 -> 2.5). */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** 0 -> "12 AM", 13 -> "1 PM", etc. */
    private static String hourLabel(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    /** Converts a raw database row into a friendly, frontend-ready object. */
    private static Map<String, Object> describe(Map<String, Object> row) {
        int day = (int) row.get("dayOfWeek");
        int hour = (int) row.get("hour");
        double avgQueue = num(row, "avgQueueLength");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dayOfWeek", day);
        out.put("dayName", DAY_NAMES[day]);
        out.put("hour", hour);
        out.put("hourLabel", hourLabel(hour));
        out.put("avgQueueLength", round1(avgQueue));
        out.put("avgWaitMinutes", round1(num(row, "avgWaitSeconds") / 60.0));
        out.put("capacityLevel", CapacityConfig.getLevel((int) Math.round(avgQueue)));
        out.put("samples", row.get("samples"));
        return out;
    }
}
