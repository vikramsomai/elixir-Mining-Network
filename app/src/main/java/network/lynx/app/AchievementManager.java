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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AchievementManager {
    private static final String TAG = "AchievementManager";
    private static AchievementManager instance;

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;
    private List<Achievement> unlockedAchievements = new ArrayList<>();
    private List<AchievementListener> listeners = new ArrayList<>();

    public enum AchievementType {
        FIRST_MINING("first_mining", "First Steps", "Complete your first mining session", "⛏️", 0.0f, 10),
        MINING_NOVICE("mining_novice", "Mining Novice", "Mine 100 LYX tokens", "🥉", 0.02f, 50),
        MINING_APPRENTICE("mining_apprentice", "Mining Apprentice", "Mine 500 LYX tokens", "🥈", 0.05f, 100),
        MINING_EXPERT("mining_expert", "Mining Expert", "Mine 2,000 LYX tokens", "🥇", 0.08f, 250),
        MINING_MASTER("mining_master", "Mining Master", "Mine 10,000 LYX tokens", "💎", 0.12f, 500),
        STREAK_STARTER("streak_starter", "Streak Starter", "Maintain a 3-day streak", "🔥", 0.01f, 20),
        STREAK_KEEPER("streak_keeper", "Streak Keeper", "Maintain a 7-day streak", "🔥🔥", 0.03f, 50),
        STREAK_CHAMPION("streak_champion", "Streak Champion", "Maintain a 30-day streak", "🏆", 0.10f, 250),
        FIRST_REFERRAL("first_referral", "Social Butterfly", "Invite your first friend", "🦋", 0.02f, 30),
        REFERRAL_STAR("referral_star", "Referral Star", "Invite 5 friends", "⭐", 0.05f, 100),
        LUCKY_SPINNER("lucky_spinner", "Lucky Spinner", "Complete 10 spins", "🎰", 0.01f, 25),
        EARLY_BIRD("early_bird", "Early Bird", "Claim reward before 8 AM", "🌅", 0.02f, 30),
        LOYAL_USER("loyal_user", "Loyal User", "Use the app for 30 days", "❤️", 0.08f, 200);

        private final String id;
        private final String title;
        private final String description;
        private final String icon;
        private final float boostBonus;
        private final int tokenReward;

        AchievementType(String id, String title, String description, String icon, float boostBonus, int tokenReward) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.boostBonus = boostBonus;
            this.tokenReward = tokenReward;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }
        public float getBoostBonus() { return boostBonus; }
        public int getTokenReward() { return tokenReward; }
    }

    public static class Achievement {
        private AchievementType type;
        private boolean unlocked;
        private long unlockedAt;

        public Achievement(AchievementType type, boolean unlocked, long unlockedAt) {
            this.type = type;
            this.unlocked = unlocked;
            this.unlockedAt = unlockedAt;
        }

        public AchievementType getType() { return type; }
        public boolean isUnlocked() { return unlocked; }
        public long getUnlockedAt() { return unlockedAt; }
    }

    public interface AchievementListener {
        void onAchievementUnlocked(Achievement achievement);
        void onAchievementsLoaded(List<Achievement> achievements);
    }

    private AchievementManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized AchievementManager getInstance(Context context) {
        if (instance == null) {
            instance = new AchievementManager(context);
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
            prefs = context.getSharedPreferences("Achievements_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            loadAchievements();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AchievementManager", e);
        }
    }

    private void loadAchievements() {
        if (userRef == null) return;

        userRef.child("achievements").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                unlockedAchievements.clear();
                for (AchievementType type : AchievementType.values()) {
                    boolean unlocked = false;
                    long unlockedAt = 0;

                    if (snapshot.child(type.getId()).exists()) {
                        unlocked = true;
                        Long time = snapshot.child(type.getId()).child("unlockedAt").getValue(Long.class);
                        unlockedAt = time != null ? time : 0;
                    }

                    unlockedAchievements.add(new Achievement(type, unlocked, unlockedAt));
                }
                notifyAchievementsLoaded();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading achievements", error.toException());
            }
        });
    }

    public void checkAchievements(double totalCoins, int referrals, int streak, int spins) {
        checkAndUnlock(AchievementType.FIRST_MINING, totalCoins >= 1);
        checkAndUnlock(AchievementType.MINING_NOVICE, totalCoins >= 100);
        checkAndUnlock(AchievementType.MINING_APPRENTICE, totalCoins >= 500);
        checkAndUnlock(AchievementType.MINING_EXPERT, totalCoins >= 2000);
        checkAndUnlock(AchievementType.MINING_MASTER, totalCoins >= 10000);
        checkAndUnlock(AchievementType.STREAK_STARTER, streak >= 3);
        checkAndUnlock(AchievementType.STREAK_KEEPER, streak >= 7);
        checkAndUnlock(AchievementType.STREAK_CHAMPION, streak >= 30);
        checkAndUnlock(AchievementType.FIRST_REFERRAL, referrals >= 1);
        checkAndUnlock(AchievementType.REFERRAL_STAR, referrals >= 5);
        checkAndUnlock(AchievementType.LUCKY_SPINNER, spins >= 10);
    }

    private void checkAndUnlock(AchievementType type, boolean condition) {
        if (!condition) return;
        if (isAchievementUnlocked(type)) return;
        unlockAchievement(type);
    }

    private boolean isAchievementUnlocked(AchievementType type) {
        for (Achievement a : unlockedAchievements) {
            if (a.getType() == type && a.isUnlocked()) {
                return true;
            }
        }
        return prefs.getBoolean(type.getId() + "_unlocked", false);
    }

    private void unlockAchievement(AchievementType type) {
        long now = System.currentTimeMillis();

        prefs.edit()
                .putBoolean(type.getId() + "_unlocked", true)
                .putLong(type.getId() + "_time", now)
                .apply();

        if (userRef != null) {
            Map<String, Object> achievementData = new HashMap<>();
            achievementData.put("unlockedAt", now);
            achievementData.put("tokenReward", type.getTokenReward());
            achievementData.put("boostBonus", type.getBoostBonus());
            userRef.child("achievements").child(type.getId()).setValue(achievementData);

            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Double current = snapshot.getValue(Double.class);
                    if (current == null) current = 0.0;
                    userRef.child("totalcoins").setValue(current + type.getTokenReward());
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
        }

        Achievement achievement = new Achievement(type, true, now);
        for (int i = 0; i < unlockedAchievements.size(); i++) {
            if (unlockedAchievements.get(i).getType() == type) {
                unlockedAchievements.set(i, achievement);
                break;
            }
        }

        notifyAchievementUnlocked(achievement);
        Log.d(TAG, "Achievement unlocked: " + type.getTitle() + " | Reward: " + type.getTokenReward() + " LYX");
    }

    public float getTotalAchievementBoost() {
        float totalBoost = 0f;
        for (Achievement a : unlockedAchievements) {
            if (a.isUnlocked()) {
                totalBoost += a.getType().getBoostBonus();
            }
        }
        return totalBoost;
    }

    public List<Achievement> getAllAchievements() {
        return new ArrayList<>(unlockedAchievements);
    }

    public int getUnlockedCount() {
        int count = 0;
        for (Achievement a : unlockedAchievements) {
            if (a.isUnlocked()) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return AchievementType.values().length;
    }

    public void addListener(AchievementListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AchievementListener listener) {
        listeners.remove(listener);
    }

    private void notifyAchievementUnlocked(Achievement achievement) {
        for (AchievementListener listener : listeners) {
            try {
                listener.onAchievementUnlocked(achievement);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyAchievementsLoaded() {
        for (AchievementListener listener : listeners) {
            try {
                listener.onAchievementsLoaded(unlockedAchievements);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    public void refresh() {
        loadAchievements();
    }

    /**
     * Reset the singleton instance - call this on logout
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.listeners.clear();
            instance.unlockedAchievements.clear();
            instance = null;
        }
    }
}

