package kiosk;

/**
 * The JSON body the Arduino sends to POST /api/event.
 *
 * Example body:   { "type": "enter" }
 *
 * Valid types:
 *   "enter"  -> someone joined the line (entry sensor pair, A then B)
 *   "exit"   -> someone left the line without being served (B then A)
 *   "served" -> the kiosk sensor detected a person being served
 *
 * NOTE: We intentionally do NOT accept a timestamp from the Arduino.
 * The Uno R4 has no real-time clock, so its "time" would just be
 * milliseconds since it powered on. The server stamps events with its
 * own clock when they arrive, which is accurate enough (WiFi latency
 * is tiny compared to how long people spend in line).
 *
 * Public fields + an empty constructor is all Jackson needs to
 * turn incoming JSON into this object automatically.
 */
public class EventPayload {
    public String type;

    // Required by Jackson (it builds the object, then fills in the fields)
    public EventPayload() {}
}
