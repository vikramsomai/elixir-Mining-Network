package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mining Streak Multiplier System
 * Consecutive days of mining = increasing multiplier
 * Encourages daily engagement
 */
public class MiningStreakManager {
    private static final String TAG = "MiningStreakManager";
    private static MiningStreakManager instance;

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;
    private List<StreakListener> listeners = new ArrayList<>();

    // Streak milestones and their multipliers
    public static class StreakMilestone {
        public int daysRequired;
        public float multiplier;
        public String badge;
        public String title;
        public int bonusTokens;

        public StreakMilestone(int daysRequired, float multiplier, String badge, String title, int bonusTokens) {
            this.daysRequired = daysRequired;
            this.multiplier = multiplier;
            this.badge = badge;
            this.title = title;
            this.bonusTokens = bonusTokens;
        }
    }

    // Define streak milestones
    public static final StreakMilestone[] MILESTONES = {
            new StreakMilestone(1, 1.0f, "🌱", "Seedling", 0),
            new StreakMilestone(3, 1.1f, "🌿", "Sprout", 5),
            new StreakMilestone(7, 1.25f, "🌳", "Sapling", 15),
            new StreakMilestone(14, 1.4f, "🌲", "Tree", 30),
            new StreakMilestone(21, 1.5f, "🏔️", "Mountain", 50),
            new StreakMilestone(30, 1.75f, "⭐", "Star Miner", 100),
            new StreakMilestone(45, 1.9f, "🌟", "Super Star", 150),
            new StreakMilestone(60, 2.0f, "💫", "Mining Legend", 250),
            new StreakMilestone(90, 2.25f, "🏆", "Champion", 500),
            new StreakMilestone(180, 2.5f, "👑", "Mining King", 1000),
            new StreakMilestone(365, 3.0f, "💎", "Diamond Miner", 2500)
    };

    public static class StreakStatus {
        public int currentStreak;
        public int longestStreak;
        public float currentMultiplier;
        public StreakMilestone currentMilestone;
        public StreakMilestone nextMilestone;
        public int daysToNextMilestone;
        public boolean minedToday;
        public String lastMiningDate;

        public StreakStatus(int currentStreak, int longestStreak, float currentMultiplier,
                           StreakMilestone currentMilestone, StreakMilestone nextMilestone,
                           int daysToNextMilestone, boolean minedToday, String lastMiningDate) {
            this.currentStreak = currentStreak;
            this.longestStreak = longestStreak;
            this.currentMultiplier = currentMultiplier;
            this.currentMilestone = currentMilestone;
            this.nextMilestone = nextMilestone;
            this.daysToNextMilestone = daysToNextMilestone;
            this.minedToday = minedToday;
            this.lastMiningDate = lastMiningDate;
        }

        public String getStreakDisplay() {
            if (currentMilestone != null) {
                return currentMilestone.badge + " " + currentStreak + " days";
            }
            return "🌱 " + currentStreak + " days";
        }

        public float getProgressToNextMilestone() {
            if (nextMilestone == null || currentMilestone == null) return 1.0f;
            int progressDays = currentStreak - currentMilestone.daysRequired;
            int totalDays = nextMilestone.daysRequired - currentMilestone.daysRequired;
            return Math.min(1.0f, (float) progressDays / totalDays);
        }
    }

    public interface StreakListener {
        void onStreakUpdated(StreakStatus status);
        void onMilestoneReached(StreakMilestone milestone, int bonusTokens);
        void onStreakBroken(int previousStreak);
    }

    private MiningStreakManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized MiningStreakManager getInstance(Context context) {
        if (instance == null) {
            instance = new MiningStreakManager(context);
        }
        return instance;
    }

    private void initialize() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in");
                return;
            }
            userId = auth.getCurrentUser().getUid();
            prefs = context.getSharedPreferences("MiningStreak_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            checkAndUpdateStreak();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MiningStreakManager", e);
        }
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    private String getYesterdayDate() {
        long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(yesterday));
    }

    public StreakStatus getStreakStatus() {
        int currentStreak = prefs.getInt("currentStreak", 0);
        int longestStreak = prefs.getInt("longestStreak", 0);
        String lastMiningDate = prefs.getString("lastMiningDate", "");
        boolean minedToday = getTodayDate().equals(lastMiningDate);

        // Check if streak should be broken
        String yesterday = getYesterdayDate();
        if (!lastMiningDate.isEmpty() && !lastMiningDate.equals(getTodayDate()) && !lastMiningDate.equals(yesterday)) {
            // Streak broken - user missed a day
            currentStreak = 0;
            prefs.edit().putInt("currentStreak", 0).apply();
        }

        float currentMultiplier = getMultiplierForStreak(currentStreak);
        StreakMilestone currentMilestone = getMilestoneForStreak(currentStreak);
        StreakMilestone nextMilestone = getNextMilestone(currentStreak);
        int daysToNext = nextMilestone != null ? nextMilestone.daysRequired - currentStreak : 0;

        return new StreakStatus(currentStreak, longestStreak, currentMultiplier,
                currentMilestone, nextMilestone, daysToNext, minedToday, lastMiningDate);
    }

    private void checkAndUpdateStreak() {
        String today = getTodayDate();
        String yesterday = getYesterdayDate();
        String lastMiningDate = prefs.getString("lastMiningDate", "");

        // If already mined today, do nothing
        if (today.equals(lastMiningDate)) {
            return;
        }

        int currentStreak = prefs.getInt("currentStreak", 0);

        // Check if streak should be broken
        if (!lastMiningDate.isEmpty() && !lastMiningDate.equals(yesterday)) {
            // Missed a day, break streak
            int previousStreak = currentStreak;
            currentStreak = 0;
            prefs.edit().putInt("currentStreak", 0).apply();
            notifyStreakBroken(previousStreak);
            Log.d(TAG, "Mining streak broken! Previous: " + previousStreak + " days");
        }
    }

    // Call this when user starts mining for the day
    public void recordMiningSession() {
        String today = getTodayDate();
        String lastMiningDate = prefs.getString("lastMiningDate", "");

        // Already mined today
        if (today.equals(lastMiningDate)) {
            Log.d(TAG, "Already recorded mining for today");
            return;
        }

        int currentStreak = prefs.getInt("currentStreak", 0);
        int longestStreak = prefs.getInt("longestStreak", 0);
        String yesterday = getYesterdayDate();

        // Check if continuing streak or starting fresh
        if (lastMiningDate.isEmpty() || lastMiningDate.equals(yesterday)) {
            // Continue streak
            currentStreak++;
        } else {
            // Streak broken, start fresh
            currentStreak = 1;
        }

        // Update longest streak
        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }

        // Check for milestone
        StreakMilestone previousMilestone = getMilestoneForStreak(currentStreak - 1);
        StreakMilestone newMilestone = getMilestoneForStreak(currentStreak);

        // Save to preferences
        prefs.edit()
                .putInt("currentStreak", currentStreak)
                .putInt("longestStreak", longestStreak)
                .putString("lastMiningDate", today)
                .apply();

        // Save to Firebase
        saveToFirebase(currentStreak, longestStreak, today);

        // Check if hit new milestone
        if (newMilestone != null && (previousMilestone == null ||
            newMilestone.daysRequired > previousMilestone.daysRequired)) {
            if (currentStreak == newMilestone.daysRequired) {
                awardMilestoneBonus(newMilestone);
                notifyMilestoneReached(newMilestone);
            }
        }

        // Notify listeners
        notifyStreakUpdated(getStreakStatus());
        Log.d(TAG, "Mining streak: " + currentStreak + " days | Multiplier: " + getMultiplierForStreak(currentStreak) + "x");
    }

    private void saveToFirebase(int currentStreak, int longestStreak, String lastMiningDate) {
        if (userRef == null) return;

        Map<String, Object> streakData = new HashMap<>();
        streakData.put("currentStreak", currentStreak);
        streakData.put("longestStreak", longestStreak);
        streakData.put("lastMiningDate", lastMiningDate);
        streakData.put("multiplier", getMultiplierForStreak(currentStreak));

        StreakMilestone milestone = getMilestoneForStreak(currentStreak);
        if (milestone != null) {
            streakData.put("currentMilestone", milestone.title);
            streakData.put("currentBadge", milestone.badge);
        }

        userRef.child("miningStreak").setValue(streakData);
    }

    private void awardMilestoneBonus(StreakMilestone milestone) {
        if (userRef == null || milestone.bonusTokens <= 0) return;

        userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double current = snapshot.getValue(Double.class);
                if (current == null) current = 0.0;
                userRef.child("totalcoins").setValue(current + milestone.bonusTokens);
                Log.d(TAG, "Awarded " + milestone.bonusTokens + " LYX for reaching " + milestone.title + " milestone!");
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    public float getMultiplierForStreak(int streak) {
        float multiplier = 1.0f;
        for (int i = MILESTONES.length - 1; i >= 0; i--) {
            if (streak >= MILESTONES[i].daysRequired) {
                multiplier = MILESTONES[i].multiplier;
                break;
            }
        }
        return multiplier;
    }

    public StreakMilestone getMilestoneForStreak(int streak) {
        StreakMilestone result = null;
        for (int i = MILESTONES.length - 1; i >= 0; i--) {
            if (streak >= MILESTONES[i].daysRequired) {
                result = MILESTONES[i];
                break;
            }
        }
        return result;
    }

    public StreakMilestone getNextMilestone(int streak) {
        for (StreakMilestone milestone : MILESTONES) {
            if (streak < milestone.daysRequired) {
                return milestone;
            }
        }
        return null; // Max milestone reached
    }

    public float getCurrentMultiplier() {
        return getStreakStatus().currentMultiplier;
    }

    public int getCurrentStreak() {
        return getStreakStatus().currentStreak;
    }

    // Listeners
    public void addListener(StreakListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(StreakListener listener) {
        listeners.remove(listener);
    }

    private void notifyStreakUpdated(StreakStatus status) {
        for (StreakListener listener : listeners) {
            try {
                listener.onStreakUpdated(status);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyMilestoneReached(StreakMilestone milestone) {
        for (StreakListener listener : listeners) {
            try {
                listener.onMilestoneReached(milestone, milestone.bonusTokens);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyStreakBroken(int previousStreak) {
        for (StreakListener listener : listeners) {
            try {
                listener.onStreakBroken(previousStreak);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    // Get streak info for display
    public String getStreakInfoDisplay() {
        StreakStatus status = getStreakStatus();
        StringBuilder info = new StringBuilder();

        info.append(status.getStreakDisplay());
        info.append(" • ").append(String.format(Locale.getDefault(), "%.1fx", status.currentMultiplier));

        if (status.nextMilestone != null) {
            info.append(" • ").append(status.daysToNextMilestone).append(" days to ").append(status.nextMilestone.badge);
        }

        return info.toString();
    }

    /**
     * Reset the singleton instance - call this on logout
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.listeners.clear();
            instance = null;
        }
    }
}

