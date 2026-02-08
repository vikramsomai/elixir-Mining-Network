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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * DAILY MISSIONS SYSTEM - Inspired by Bee Network & Mobile Games
 *
 * Daily missions create urgency and routine engagement.
 * Users complete tasks to earn bonus rewards.
 *
 * MISSION TYPES:
 * 1. Mining Session - Start mining today
 * 2. Social Share - Share app with friends
 * 3. Invite Friend - Invite 1 new friend
 * 4. Watch Ads - Watch 3 rewarded ads
 * 5. Check Circle - Verify security circle
 * 6. Daily Login - Just open the app
 * 7. Spin Wheel - Use spin feature
 * 8. Claim Bonus - Claim any bonus
 *
 * REWARDS:
 * - Each mission = XP + LYX tokens
 * - Complete all = MEGA BONUS (3x daily missions)
 * - 7-day streak = SUPER REWARD
 */
public class DailyMissionsManager {
    private static final String TAG = "DailyMissionsManager";
    private static final String PREFS_NAME = "daily_missions";

    private static DailyMissionsManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private String currentUserId;

    private List<Mission> todayMissions = new ArrayList<>();
    private int completedCount = 0;
    private MissionUpdateListener listener;

    // Mission types
    public enum MissionType {
        MINING_SESSION("Start Mining", "Begin a mining session today", 5.0, 10),
        WATCH_ADS("Watch Ads", "Watch 3 rewarded ads", 10.0, 20),
        DAILY_LOGIN("Daily Login", "Open the app", 2.0, 5),
        SPIN_WHEEL("Lucky Spin", "Use the spin wheel", 3.0, 8),
        INVITE_FRIEND("Invite Friend", "Invite a new friend", 15.0, 30),
        CHECK_CIRCLE("Security Check", "Check your security circle", 5.0, 10),
        CLAIM_BONUS("Claim Bonus", "Claim any bonus reward", 3.0, 8),
        SHARE_APP("Share App", "Share on social media", 8.0, 15);

        public final String title;
        public final String description;
        public final double lyxReward;
        public final int xpReward;

        MissionType(String title, String description, double lyxReward, int xpReward) {
            this.title = title;
            this.description = description;
            this.lyxReward = lyxReward;
            this.xpReward = xpReward;
        }
    }

    public static class Mission {
        public MissionType type;
        public String title;
        public String description;
        public double lyxReward;
        public int xpReward;
        public int progress;
        public int target;
        public boolean completed;
        public boolean claimed;

        public Mission() {}

        public Mission(MissionType type, int target) {
            this.type = type;
            this.title = type.title;
            this.description = type.description;
            this.lyxReward = type.lyxReward;
            this.xpReward = type.xpReward;
            this.target = target;
            this.progress = 0;
            this.completed = false;
            this.claimed = false;
        }

        public float getProgressPercent() {
            return target > 0 ? (float) progress / target * 100 : 0;
        }
    }

    public interface MissionUpdateListener {
        void onMissionsUpdated(List<Mission> missions, int completed, int total);
        void onMissionCompleted(Mission mission);
        void onAllMissionsCompleted(double bonusReward);
    }

    private DailyMissionsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    public static synchronized DailyMissionsManager getInstance(Context context) {
        if (instance == null) {
            instance = new DailyMissionsManager(context);
        }
        return instance;
    }

    public void setListener(MissionUpdateListener listener) {
        this.listener = listener;
    }

    /**
     * Initialize daily missions - call on app start
     */
    public void initializeMissions() {
        String today = getTodayDate();
        String lastDate = prefs.getString("lastMissionDate", "");

        if (!today.equals(lastDate)) {
            // New day - generate new missions
            generateDailyMissions();
            prefs.edit()
                    .putString("lastMissionDate", today)
                    .putInt("missionStreak", getMissionStreak(lastDate))
                    .apply();
        } else {
            // Load existing missions
            loadMissionsFromPrefs();
        }

        notifyListener();
    }

    private int getMissionStreak(String lastDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            Date last = sdf.parse(lastDate);
            Date today = sdf.parse(getTodayDate());

            if (last != null && today != null) {
                long diff = (today.getTime() - last.getTime()) / (24 * 60 * 60 * 1000);
                if (diff == 1) {
                    // Consecutive day
                    return prefs.getInt("missionStreak", 0) + 1;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating streak", e);
        }
        return 0; // Streak broken
    }

    /**
     * Generate random daily missions
     */
    private void generateDailyMissions() {
        todayMissions.clear();

        // Always include these core missions
        todayMissions.add(new Mission(MissionType.DAILY_LOGIN, 1));
        todayMissions.add(new Mission(MissionType.MINING_SESSION, 1));
        todayMissions.add(new Mission(MissionType.WATCH_ADS, 3));

        // Randomly add 2-3 more missions
        Random random = new Random();
        List<MissionType> optionalMissions = new ArrayList<>();
        optionalMissions.add(MissionType.SPIN_WHEEL);
        optionalMissions.add(MissionType.INVITE_FRIEND);
        optionalMissions.add(MissionType.CHECK_CIRCLE);
        optionalMissions.add(MissionType.CLAIM_BONUS);
        optionalMissions.add(MissionType.SHARE_APP);

        int additionalCount = 2 + random.nextInt(2); // 2-3 additional
        for (int i = 0; i < additionalCount && !optionalMissions.isEmpty(); i++) {
            int index = random.nextInt(optionalMissions.size());
            MissionType type = optionalMissions.remove(index);
            todayMissions.add(new Mission(type, 1));
        }

        // Auto-complete login mission
        completeMission(MissionType.DAILY_LOGIN);

        saveMissionsToPrefs();
    }

    private void saveMissionsToPrefs() {
        StringBuilder sb = new StringBuilder();
        for (Mission m : todayMissions) {
            sb.append(m.type.name()).append(",")
              .append(m.progress).append(",")
              .append(m.target).append(",")
              .append(m.completed ? "1" : "0").append(",")
              .append(m.claimed ? "1" : "0").append(";");
        }
        prefs.edit().putString("missions", sb.toString()).apply();
    }

    private void loadMissionsFromPrefs() {
        todayMissions.clear();
        String data = prefs.getString("missions", "");

        if (data.isEmpty()) {
            generateDailyMissions();
            return;
        }

        String[] missionStrings = data.split(";");
        for (String ms : missionStrings) {
            if (ms.isEmpty()) continue;

            String[] parts = ms.split(",");
            if (parts.length >= 5) {
                try {
                    MissionType type = MissionType.valueOf(parts[0]);
                    Mission m = new Mission(type, Integer.parseInt(parts[2]));
                    m.progress = Integer.parseInt(parts[1]);
                    m.completed = parts[3].equals("1");
                    m.claimed = parts[4].equals("1");
                    todayMissions.add(m);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing mission", e);
                }
            }
        }

        updateCompletedCount();
    }

    /**
     * Complete a mission (call when user performs action)
     */
    public void completeMission(MissionType type) {
        for (Mission m : todayMissions) {
            if (m.type == type && !m.completed) {
                m.progress++;
                if (m.progress >= m.target) {
                    m.completed = true;
                    updateCompletedCount();

                    if (listener != null) {
                        listener.onMissionCompleted(m);
                    }

                    // Check if all completed
                    if (completedCount == todayMissions.size()) {
                        double bonus = calculateMegaBonus();
                        if (listener != null) {
                            listener.onAllMissionsCompleted(bonus);
                        }
                    }
                }
                saveMissionsToPrefs();
                notifyListener();
                break;
            }
        }
    }

    /**
     * Claim reward for completed mission
     */
    public void claimMissionReward(MissionType type, ClaimCallback callback) {
        for (Mission m : todayMissions) {
            if (m.type == type && m.completed && !m.claimed) {
                m.claimed = true;
                saveMissionsToPrefs();

                // Grant reward
                grantReward(m.lyxReward, m.xpReward, callback);
                notifyListener();
                return;
            }
        }
        callback.onError("Mission not ready to claim");
    }

    private void grantReward(double lyx, int xp, ClaimCallback callback) {
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        // Update Firebase - FIXED: Use 'totalcoins' (lowercase) to match rest of app
        dbRef.child("users").child(currentUserId).child("totalcoins")
                .get().addOnSuccessListener(snapshot -> {
                    double current = 0;
                    if (snapshot.exists() && snapshot.getValue() != null) {
                        current = ((Number) snapshot.getValue()).doubleValue();
                    }
                    dbRef.child("users").child(currentUserId).child("totalcoins")
                            .setValue(current + lyx)
                            .addOnCompleteListener(task -> {
                                // FIXED: Notify WalletManager so balance updates immediately
                                if (task.isSuccessful()) {
                                    try {
                                        WalletManager.getInstance(context).refreshBalance();
                                    } catch (Exception e) {
                                        Log.w(TAG, "Failed to notify WalletManager", e);
                                    }
                                }
                            });

                    // Update XP
                    dbRef.child("users").child(currentUserId).child("xp")
                            .get().addOnSuccessListener(xpSnap -> {
                                int currentXp = 0;
                                if (xpSnap.exists() && xpSnap.getValue() != null) {
                                    currentXp = ((Number) xpSnap.getValue()).intValue();
                                }
                                dbRef.child("users").child(currentUserId).child("xp")
                                        .setValue(currentXp + xp);
                            });

                    callback.onSuccess(lyx, xp);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private double calculateMegaBonus() {
        double total = 0;
        for (Mission m : todayMissions) {
            total += m.lyxReward;
        }
        return total * 0.5; // 50% of total as mega bonus
    }

    private void updateCompletedCount() {
        completedCount = 0;
        for (Mission m : todayMissions) {
            if (m.completed) completedCount++;
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onMissionsUpdated(todayMissions, completedCount, todayMissions.size());
        }
    }

    public List<Mission> getTodayMissions() {
        return todayMissions;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getTotalMissions() {
        return todayMissions.size();
    }

    public int getMissionStreak() {
        return prefs.getInt("missionStreak", 0);
    }

    public float getCompletionPercent() {
        return todayMissions.isEmpty() ? 0 : (float) completedCount / todayMissions.size() * 100;
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    public interface ClaimCallback {
        void onSuccess(double lyx, int xp);
        void onError(String message);
    }
}

