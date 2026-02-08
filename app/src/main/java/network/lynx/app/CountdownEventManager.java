package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * COUNTDOWN & FOMO EVENT MANAGER
 *
 * Creates urgency through limited-time events and countdowns.
 * Users feel they'll miss out if they don't act now.
 *
 * EVENT TYPES:
 * 1. Flash Mining (2x rate for 1 hour)
 * 2. Double Referral (2x referral bonus for 24h)
 * 3. Bonus Hour (random hour with 3x spin rewards)
 * 4. Weekend Special (Saturday/Sunday bonuses)
 * 5. Halving Countdown (days until next halving)
 * 6. Mainnet Countdown (creates anticipation)
 *
 * WHY IT WORKS:
 * - FOMO (Fear Of Missing Out)
 * - Creates daily check-in habit
 * - Users open app to check current events
 * - Limited time = watch ads NOW
 */
public class CountdownEventManager {
    private static final String TAG = "CountdownEventManager";
    private static final String PREFS_NAME = "countdown_events";

    private static CountdownEventManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<CountdownEvent> activeEvents = new ArrayList<>();
    private EventUpdateListener listener;

    public enum EventType {
        FLASH_MINING("Flash Mining", "2x mining rate!", 2.0f, 60 * 60 * 1000),           // 1 hour
        DOUBLE_REFERRAL("Double Referral", "2x referral bonus!", 2.0f, 24 * 60 * 60 * 1000), // 24 hours
        BONUS_HOUR("Bonus Hour", "3x spin rewards!", 3.0f, 60 * 60 * 1000),              // 1 hour
        WEEKEND_SPECIAL("Weekend Special", "50% bonus mining!", 1.5f, 48 * 60 * 60 * 1000), // 2 days
        HAPPY_HOUR("Happy Hour", "All rewards doubled!", 2.0f, 60 * 60 * 1000),          // 1 hour
        NEW_USER_BOOST("New User Boost", "5x mining for new users!", 5.0f, 7 * 24 * 60 * 60 * 1000); // 7 days

        public final String name;
        public final String description;
        public final float multiplier;
        public final long defaultDuration;

        EventType(String name, String description, float multiplier, long duration) {
            this.name = name;
            this.description = description;
            this.multiplier = multiplier;
            this.defaultDuration = duration;
        }
    }

    public static class CountdownEvent {
        public String id;
        public EventType type;
        public String name;
        public String description;
        public float multiplier;
        public long startTime;
        public long endTime;
        public boolean isActive;
        public String targetFeature; // mining, referral, spin, all

        public CountdownEvent() {}

        public CountdownEvent(EventType type) {
            this.type = type;
            this.name = type.name;
            this.description = type.description;
            this.multiplier = type.multiplier;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + type.defaultDuration;
            this.isActive = true;
        }

        public long getRemainingTime() {
            return Math.max(0, endTime - System.currentTimeMillis());
        }

        public String getFormattedRemaining() {
            long remaining = getRemainingTime();

            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            long seconds = (remaining % (60 * 1000)) / 1000;

            if (hours > 24) {
                long days = hours / 24;
                return days + "d " + (hours % 24) + "h";
            } else if (hours > 0) {
                return String.format(Locale.US, "%dh %dm", hours, minutes);
            } else {
                return String.format(Locale.US, "%dm %ds", minutes, seconds);
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > endTime;
        }
    }

    public interface EventUpdateListener {
        void onEventsUpdated(List<CountdownEvent> events);
        void onEventStarted(CountdownEvent event);
        void onEventEnded(CountdownEvent event);
        void onCountdownTick(CountdownEvent event, String formattedTime);
    }

    private CountdownEventManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        loadEvents();
        startEventChecker();
    }

    public static synchronized CountdownEventManager getInstance(Context context) {
        if (instance == null) {
            instance = new CountdownEventManager(context);
        }
        return instance;
    }

    public void setListener(EventUpdateListener listener) {
        this.listener = listener;
    }

    private void loadEvents() {
        // Load from Firebase
        dbRef.child("events").child("active").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                activeEvents.clear();

                for (DataSnapshot eventSnap : snapshot.getChildren()) {
                    try {
                        CountdownEvent event = eventSnap.getValue(CountdownEvent.class);
                        if (event != null && !event.isExpired()) {
                            event.id = eventSnap.getKey();
                            activeEvents.add(event);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing event", e);
                    }
                }

                // Check for auto-generated events
                checkAutoEvents();

                if (listener != null) {
                    listener.onEventsUpdated(activeEvents);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading events", error.toException());
            }
        });
    }

    /**
     * Check and create automatic events (weekend, happy hour, etc.)
     */
    private void checkAutoEvents() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // Weekend Special (Saturday/Sunday)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            boolean hasWeekendEvent = false;
            for (CountdownEvent e : activeEvents) {
                if (e.type == EventType.WEEKEND_SPECIAL) {
                    hasWeekendEvent = true;
                    break;
                }
            }

            if (!hasWeekendEvent) {
                CountdownEvent weekend = new CountdownEvent(EventType.WEEKEND_SPECIAL);
                weekend.targetFeature = "mining";
                activeEvents.add(weekend);
            }
        }

        // Happy Hour (12 PM and 8 PM)
        if (hour == 12 || hour == 20) {
            String todayHour = cal.get(Calendar.DAY_OF_YEAR) + "_" + hour;
            if (!prefs.getBoolean("happyHour_" + todayHour, false)) {
                prefs.edit().putBoolean("happyHour_" + todayHour, true).apply();

                CountdownEvent happyHour = new CountdownEvent(EventType.HAPPY_HOUR);
                happyHour.targetFeature = "all";
                activeEvents.add(happyHour);

                if (listener != null) {
                    listener.onEventStarted(happyHour);
                }
            }
        }
    }

    /**
     * Start periodic event checker
     */
    private void startEventChecker() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Remove expired events
                List<CountdownEvent> expired = new ArrayList<>();
                for (CountdownEvent event : activeEvents) {
                    if (event.isExpired()) {
                        expired.add(event);
                        if (listener != null) {
                            listener.onEventEnded(event);
                        }
                    }
                }
                activeEvents.removeAll(expired);

                // Notify tick
                if (listener != null && !activeEvents.isEmpty()) {
                    for (CountdownEvent event : activeEvents) {
                        listener.onCountdownTick(event, event.getFormattedRemaining());
                    }
                }

                // Check every second
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /**
     * Get current multiplier for a feature
     */
    public float getMultiplierForFeature(String feature) {
        float multiplier = 1.0f;

        for (CountdownEvent event : activeEvents) {
            if (!event.isExpired()) {
                if ("all".equals(event.targetFeature) || feature.equals(event.targetFeature)) {
                    multiplier = Math.max(multiplier, event.multiplier);
                }
            }
        }

        return multiplier;
    }

    /**
     * Get highest active multiplier
     */
    public float getHighestActiveMultiplier() {
        float max = 1.0f;
        for (CountdownEvent event : activeEvents) {
            if (!event.isExpired()) {
                max = Math.max(max, event.multiplier);
            }
        }
        return max;
    }

    /**
     * Check if any event is active
     */
    public boolean hasActiveEvent() {
        for (CountdownEvent event : activeEvents) {
            if (!event.isExpired()) return true;
        }
        return false;
    }

    /**
     * Get active events
     */
    public List<CountdownEvent> getActiveEvents() {
        // Filter expired
        List<CountdownEvent> active = new ArrayList<>();
        for (CountdownEvent event : activeEvents) {
            if (!event.isExpired()) {
                active.add(event);
            }
        }
        return active;
    }

    /**
     * Get next event countdown string
     */
    public String getNextEventCountdown() {
        CountdownEvent soonest = null;

        for (CountdownEvent event : activeEvents) {
            if (!event.isExpired()) {
                if (soonest == null || event.endTime < soonest.endTime) {
                    soonest = event;
                }
            }
        }

        return soonest != null ? soonest.getFormattedRemaining() : null;
    }

    /**
     * Create a custom event (for admin)
     */
    public void createEvent(EventType type, long durationMs, String targetFeature) {
        CountdownEvent event = new CountdownEvent(type);
        event.endTime = System.currentTimeMillis() + durationMs;
        event.targetFeature = targetFeature;
        event.id = dbRef.child("events").child("active").push().getKey();

        dbRef.child("events").child("active").child(event.id).setValue(event);
    }

    public void cleanup() {
        handler.removeCallbacksAndMessages(null);
    }
}

