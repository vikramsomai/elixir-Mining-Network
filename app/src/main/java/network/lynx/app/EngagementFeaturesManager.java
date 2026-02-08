package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * EngagementFeaturesManager - Comprehensive engagement system
 *
 * Features designed for maximum user retention and revenue:
 *
 * 1. PROGRESSIVE JACKPOT SYSTEM
 *    - Global jackpot grows with each spin
 *    - Creates excitement and FOMO
 *    - Encourages more ad watches
 *
 * 2. DAILY LOGIN CALENDAR (7-Day)
 *    - Visual progress tracking
 *    - Escalating daily rewards (1→5→10→15→25→40→100 LYX)
 *    - Streak multiplier bonus
 *    - Week completion bonus (50 extra LYX)
 *
 * 3. TREASURE HUNT SYSTEM
 *    - Hidden "treasure spots" that appear randomly
 *    - User must watch ad to claim
 *    - Creates excitement and exploration
 *
 * 4. HOURLY FORTUNE WHEEL
 *    - Small wheel every hour (no ad)
 *    - Big wheel every 4 hours (ad required)
 *    - Mega wheel once daily (ad required)
 *
 * 5. SOCIAL COMPETITION (Weekly Race)
 *    - Top 100 leaderboard with weekly prizes
 *    - Creates urgency and competition
 *    - Resets weekly for fresh starts
 *
 * 6. VIP TIER SYSTEM
 *    - Bronze → Silver → Gold → Platinum → Diamond
 *    - Permanent mining rate bonuses
 *    - Exclusive features unlock
 *
 * 7. LUCKY TIME BONUSES
 *    - Random 2x/3x bonus periods
 *    - Push notifications when active
 *    - Creates urgency to open app
 *
 * 8. MINI-GAMES
 *    - Daily number prediction
 *    - Coin flip (double or nothing)
 *    - Match 3 puzzle for bonus
 */
public class EngagementFeaturesManager {
    private static final String TAG = "EngagementFeatures";
    private static EngagementFeaturesManager instance;

    private static final String PREFS_NAME = "EngagementFeatures";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference dbRef;
    private DatabaseReference userRef;
    private String userId;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<EngagementListener> listeners = new ArrayList<>();

    // ==================== PROGRESSIVE JACKPOT ====================

    public static class JackpotInfo {
        public double currentAmount;
        public double minWin;
        public double maxWin;
        public long lastWonTimestamp;
        public String lastWinner;
        public int totalContributors;

        public JackpotInfo() {
            this.currentAmount = 1000; // Starting amount
            this.minWin = 100;
            this.maxWin = 0;
        }
    }

    // ==================== LOGIN CALENDAR ====================

    public static class LoginCalendarDay {
        public int dayNumber; // 1-7
        public double reward;
        public boolean claimed;
        public boolean isToday;
        public boolean isLocked; // Future days

        public LoginCalendarDay(int dayNumber, double reward, boolean claimed, boolean isToday, boolean isLocked) {
            this.dayNumber = dayNumber;
            this.reward = reward;
            this.claimed = claimed;
            this.isToday = isToday;
            this.isLocked = isLocked;
        }
    }

    public static class LoginCalendarStatus {
        public List<LoginCalendarDay> days;
        public int currentDay; // 1-7
        public int weekNumber;
        public double weeklyBonusAmount;
        public boolean weeklyBonusClaimed;
        public int totalWeeksCompleted;

        public LoginCalendarStatus() {
            this.days = new ArrayList<>();
            this.currentDay = 1;
            this.weekNumber = 1;
            this.weeklyBonusAmount = 50;
            this.weeklyBonusClaimed = false;
            this.totalWeeksCompleted = 0;
        }
    }

    // Daily rewards escalate each day
    private static final double[] DAILY_REWARDS = {1, 5, 10, 15, 25, 40, 100};
    private static final double WEEKLY_COMPLETION_BONUS = 50;

    // ==================== VIP TIER SYSTEM ====================

    public enum VIPTier {
        BRONZE(0, "🥉", "Bronze", 0.0f, 0),
        SILVER(500, "🥈", "Silver", 0.05f, 1),
        GOLD(2000, "🥇", "Gold", 0.10f, 2),
        PLATINUM(10000, "💎", "Platinum", 0.20f, 3),
        DIAMOND(50000, "👑", "Diamond", 0.35f, 5);

        public final int requiredCoins;
        public final String icon;
        public final String name;
        public final float miningBonus; // Permanent mining rate bonus
        public final int extraSpins; // Extra daily spins

        VIPTier(int requiredCoins, String icon, String name, float miningBonus, int extraSpins) {
            this.requiredCoins = requiredCoins;
            this.icon = icon;
            this.name = name;
            this.miningBonus = miningBonus;
            this.extraSpins = extraSpins;
        }

        public static VIPTier fromCoins(double totalCoins) {
            VIPTier result = BRONZE;
            for (VIPTier tier : values()) {
                if (totalCoins >= tier.requiredCoins) {
                    result = tier;
                }
            }
            return result;
        }

        public VIPTier getNextTier() {
            int ordinal = this.ordinal();
            if (ordinal < values().length - 1) {
                return values()[ordinal + 1];
            }
            return this;
        }
    }

    public static class VIPStatus {
        public VIPTier currentTier;
        public VIPTier nextTier;
        public double currentCoins;
        public double coinsToNextTier;
        public float progressToNextTier;
        public int daysAtCurrentTier;
        public List<String> unlockedPerks;

        public VIPStatus() {
            this.currentTier = VIPTier.BRONZE;
            this.nextTier = VIPTier.SILVER;
            this.currentCoins = 0;
            this.coinsToNextTier = 500;
            this.progressToNextTier = 0;
            this.daysAtCurrentTier = 0;
            this.unlockedPerks = new ArrayList<>();
        }
    }

    // ==================== TREASURE HUNT ====================

    public static class TreasureSpot {
        public String id;
        public String location; // "home", "mining", "referral", "profile"
        public double reward;
        public long expiresAt;
        public boolean claimed;

        public TreasureSpot(String id, String location, double reward, long expiresAt) {
            this.id = id;
            this.location = location;
            this.reward = reward;
            this.expiresAt = expiresAt;
            this.claimed = false;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long getTimeRemaining() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
    }

    // ==================== LUCKY TIME BONUS ====================

    public static class LuckyTimeBonus {
        public float multiplier; // 2x, 3x, 5x
        public long startTime;
        public long endTime;
        public String bonusType; // "mining", "spin", "all"
        public boolean isActive;

        public LuckyTimeBonus() {
            this.multiplier = 1.0f;
            this.isActive = false;
        }

        public long getTimeRemaining() {
            return Math.max(0, endTime - System.currentTimeMillis());
        }

        public String getTimeRemainingText() {
            long remaining = getTimeRemaining();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    // ==================== WEEKLY RACE ====================

    public static class WeeklyRaceStatus {
        public int currentRank;
        public double weeklyEarnings;
        public int totalParticipants;
        public long weekEndsAt;
        public List<RaceParticipant> topParticipants;
        public double[] prizes; // Prizes for top 10

        public WeeklyRaceStatus() {
            this.currentRank = 0;
            this.weeklyEarnings = 0;
            this.totalParticipants = 0;
            this.topParticipants = new ArrayList<>();
            this.prizes = new double[]{500, 300, 200, 100, 80, 60, 50, 40, 30, 20};
        }

        public long getTimeRemaining() {
            return Math.max(0, weekEndsAt - System.currentTimeMillis());
        }
    }

    public static class RaceParticipant {
        public String username;
        public double earnings;
        public int rank;
        public String tier;

        public RaceParticipant(String username, double earnings, int rank, String tier) {
            this.username = username;
            this.earnings = earnings;
            this.rank = rank;
            this.tier = tier;
        }
    }

    // ==================== MINI GAMES ====================

    public static class CoinFlipGame {
        public double betAmount;
        public boolean isHeads;
        public boolean won;
        public double winAmount;
        public int consecutiveWins;
        public int maxConsecutiveWins;

        public CoinFlipGame() {
            this.betAmount = 0;
            this.consecutiveWins = 0;
            this.maxConsecutiveWins = 0;
        }
    }

    public static class DailyPredictionGame {
        public int predictedNumber; // 1-10
        public int actualNumber;
        public boolean played;
        public boolean won;
        public double reward;
        public String todayDate;

        public DailyPredictionGame() {
            this.predictedNumber = 0;
            this.played = false;
            this.won = false;
            this.reward = 0;
        }
    }

    // ==================== INTERFACES ====================

    public interface EngagementListener {
        void onJackpotUpdated(JackpotInfo jackpot);
        void onTreasureFound(TreasureSpot treasure);
        void onLuckyTimeStarted(LuckyTimeBonus bonus);
        void onVIPTierChanged(VIPStatus status);
        void onWeeklyRaceUpdated(WeeklyRaceStatus race);
    }

    public interface RewardCallback {
        void onSuccess(double reward, String message);
        void onError(String error);
    }

    // ==================== SINGLETON ====================

    private EngagementFeaturesManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();
        initialize();
    }

    public static synchronized EngagementFeaturesManager getInstance(Context context) {
        if (instance == null) {
            instance = new EngagementFeaturesManager(context);
        }
        return instance;
    }

    private void initialize() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            userRef = dbRef.child("users").child(userId);
            setupListeners();
            checkForTreasures();
            checkForLuckyTime();
        }
    }

    public void refreshUser() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            userRef = dbRef.child("users").child(userId);
        }
    }

    // ==================== LOGIN CALENDAR METHODS ====================

    public void getLoginCalendarStatus(LoginCalendarCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());
        String weekKey = getWeekKey();

        userRef.child("loginCalendar").child(weekKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                LoginCalendarStatus status = new LoginCalendarStatus();

                int claimedDays = 0;
                for (int i = 1; i <= 7; i++) {
                    boolean claimed = false;
                    if (snapshot.hasChild("day" + i)) {
                        claimed = Boolean.TRUE.equals(snapshot.child("day" + i).getValue(Boolean.class));
                        if (claimed) claimedDays++;
                    }

                    double reward = DAILY_REWARDS[i - 1];
                    boolean isToday = (i == claimedDays + 1) && !claimed;
                    boolean isLocked = i > claimedDays + 1;

                    status.days.add(new LoginCalendarDay(i, reward, claimed, isToday, isLocked));
                }

                status.currentDay = Math.min(claimedDays + 1, 7);
                status.weeklyBonusClaimed = Boolean.TRUE.equals(
                    snapshot.child("weeklyBonusClaimed").getValue(Boolean.class));

                Integer weeksCompleted = snapshot.child("totalWeeksCompleted").getValue(Integer.class);
                status.totalWeeksCompleted = weeksCompleted != null ? weeksCompleted : 0;

                // Calculate escalating weekly bonus
                status.weeklyBonusAmount = WEEKLY_COMPLETION_BONUS * (1 + status.totalWeeksCompleted * 0.1);

                callback.onSuccess(status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void claimDailyLoginReward(int dayNumber, RewardCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        if (dayNumber < 1 || dayNumber > 7) {
            callback.onError("Invalid day");
            return;
        }

        String weekKey = getWeekKey();
        double reward = DAILY_REWARDS[dayNumber - 1];

        // Verify previous days are claimed
        userRef.child("loginCalendar").child(weekKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check previous days
                for (int i = 1; i < dayNumber; i++) {
                    Boolean claimed = snapshot.child("day" + i).getValue(Boolean.class);
                    if (!Boolean.TRUE.equals(claimed)) {
                        callback.onError("Must claim Day " + i + " first");
                        return;
                    }
                }

                // Check if already claimed
                Boolean alreadyClaimed = snapshot.child("day" + dayNumber).getValue(Boolean.class);
                if (Boolean.TRUE.equals(alreadyClaimed)) {
                    callback.onError("Already claimed today");
                    return;
                }

                // Claim the reward
                Map<String, Object> updates = new HashMap<>();
                updates.put("loginCalendar/" + weekKey + "/day" + dayNumber, true);
                updates.put("loginCalendar/" + weekKey + "/lastClaimDate",
                    new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date()));
                updates.put("totalcoins", ServerValue.increment(reward));

                userRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String message = String.format(Locale.US,
                            "🎉 Day %d claimed! +%.0f LYX", dayNumber, reward);

                        // FIXED: Notify WalletManager so balance updates immediately across all fragments
                        try {
                            WalletManager.getInstance(context).refreshBalance();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to notify WalletManager", e);
                        }

                        // Check if week completed
                        if (dayNumber == 7) {
                            claimWeeklyBonus(weekKey, callback, reward);
                        } else {
                            callback.onSuccess(reward, message);
                        }
                    } else {
                        callback.onError("Failed to claim reward");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    private void claimWeeklyBonus(String weekKey, RewardCallback originalCallback, double dayReward) {
        double weeklyBonus = WEEKLY_COMPLETION_BONUS;

        Map<String, Object> updates = new HashMap<>();
        updates.put("loginCalendar/" + weekKey + "/weeklyBonusClaimed", true);
        updates.put("loginCalendar/totalWeeksCompleted", ServerValue.increment(1));
        updates.put("totalcoins", ServerValue.increment(weeklyBonus));

        userRef.updateChildren(updates).addOnCompleteListener(task -> {
            double totalReward = dayReward + weeklyBonus;
            String message = String.format(Locale.US,
                "🏆 WEEK COMPLETE! Day 7: +%.0f LYX + Weekly Bonus: +%.0f LYX = %.0f LYX!",
                dayReward, weeklyBonus, totalReward);
            originalCallback.onSuccess(totalReward, message);
        });
    }

    private String getWeekKey() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int week = cal.get(Calendar.WEEK_OF_YEAR);
        return year + "_W" + week;
    }

    public interface LoginCalendarCallback {
        void onSuccess(LoginCalendarStatus status);
        void onError(String error);
    }

    // ==================== VIP TIER METHODS ====================

    public void getVIPStatus(VIPStatusCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                VIPStatus status = new VIPStatus();

                Double totalCoins = snapshot.child("totalcoins").getValue(Double.class);
                status.currentCoins = totalCoins != null ? totalCoins : 0;

                status.currentTier = VIPTier.fromCoins(status.currentCoins);
                status.nextTier = status.currentTier.getNextTier();

                if (status.currentTier != status.nextTier) {
                    status.coinsToNextTier = status.nextTier.requiredCoins - status.currentCoins;
                    status.progressToNextTier = (float) (status.currentCoins - status.currentTier.requiredCoins) /
                        (status.nextTier.requiredCoins - status.currentTier.requiredCoins);
                } else {
                    status.coinsToNextTier = 0;
                    status.progressToNextTier = 1.0f;
                }

                // Build perks list
                status.unlockedPerks = new ArrayList<>();
                if (status.currentTier.miningBonus > 0) {
                    status.unlockedPerks.add(String.format(Locale.US,
                        "+%.0f%% Mining Bonus", status.currentTier.miningBonus * 100));
                }
                if (status.currentTier.extraSpins > 0) {
                    status.unlockedPerks.add(String.format(Locale.US,
                        "+%d Extra Daily Spins", status.currentTier.extraSpins));
                }

                callback.onSuccess(status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public interface VIPStatusCallback {
        void onSuccess(VIPStatus status);
        void onError(String error);
    }

    // ==================== TREASURE HUNT METHODS ====================

    private void checkForTreasures() {
        if (userRef == null) return;

        // Check if user should get a treasure (random chance every check)
        if (random.nextInt(100) < 15) { // 15% chance
            spawnTreasure();
        }
    }

    private void spawnTreasure() {
        String[] locations = {"home", "mining", "referral", "profile"};
        String location = locations[random.nextInt(locations.length)];

        double reward = 1 + random.nextInt(10); // 1-10 LYX
        long expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30); // 30 min expiry
        String treasureId = "treasure_" + System.currentTimeMillis();

        TreasureSpot treasure = new TreasureSpot(treasureId, location, reward, expiresAt);

        // Save to user's treasures
        Map<String, Object> treasureData = new HashMap<>();
        treasureData.put("location", location);
        treasureData.put("reward", reward);
        treasureData.put("expiresAt", expiresAt);
        treasureData.put("claimed", false);

        userRef.child("treasures").child(treasureId).setValue(treasureData);

        // Notify listeners
        for (EngagementListener listener : listeners) {
            listener.onTreasureFound(treasure);
        }
    }

    public void getActiveTreasures(TreasuresCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        userRef.child("treasures").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TreasureSpot> treasures = new ArrayList<>();
                long now = System.currentTimeMillis();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Long expiresAt = child.child("expiresAt").getValue(Long.class);
                    Boolean claimed = child.child("claimed").getValue(Boolean.class);

                    if (expiresAt != null && expiresAt > now && !Boolean.TRUE.equals(claimed)) {
                        String location = child.child("location").getValue(String.class);
                        Double reward = child.child("reward").getValue(Double.class);

                        if (location != null && reward != null) {
                            treasures.add(new TreasureSpot(child.getKey(), location, reward, expiresAt));
                        }
                    }
                }

                callback.onSuccess(treasures);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void claimTreasure(String treasureId, RewardCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        userRef.child("treasures").child(treasureId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onError("Treasure not found");
                    return;
                }

                Boolean claimed = snapshot.child("claimed").getValue(Boolean.class);
                if (Boolean.TRUE.equals(claimed)) {
                    callback.onError("Already claimed");
                    return;
                }

                Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);
                if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
                    callback.onError("Treasure expired");
                    return;
                }

                Double reward = snapshot.child("reward").getValue(Double.class);
                if (reward == null) reward = 1.0;

                double finalReward = reward;
                Map<String, Object> updates = new HashMap<>();
                updates.put("treasures/" + treasureId + "/claimed", true);
                updates.put("totalcoins", ServerValue.increment(finalReward));

                userRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess(finalReward, "🎁 Treasure claimed! +" + finalReward + " LYX");
                    } else {
                        callback.onError("Failed to claim treasure");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public interface TreasuresCallback {
        void onSuccess(List<TreasureSpot> treasures);
        void onError(String error);
    }

    // ==================== LUCKY TIME BONUS METHODS ====================

    private void checkForLuckyTime() {
        // Check global lucky time from Firebase
        dbRef.child("globalEvents").child("luckyTime").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean active = snapshot.child("active").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(active)) {
                        Float multiplier = snapshot.child("multiplier").getValue(Float.class);
                        Long endTime = snapshot.child("endTime").getValue(Long.class);
                        String bonusType = snapshot.child("type").getValue(String.class);

                        if (multiplier != null && endTime != null && endTime > System.currentTimeMillis()) {
                            LuckyTimeBonus bonus = new LuckyTimeBonus();
                            bonus.isActive = true;
                            bonus.multiplier = multiplier;
                            bonus.endTime = endTime;
                            bonus.bonusType = bonusType != null ? bonusType : "all";

                            for (EngagementListener listener : listeners) {
                                listener.onLuckyTimeStarted(bonus);
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking lucky time", error.toException());
            }
        });
    }

    public void getCurrentLuckyTimeBonus(LuckyTimeCallback callback) {
        dbRef.child("globalEvents").child("luckyTime").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                LuckyTimeBonus bonus = new LuckyTimeBonus();

                if (snapshot.exists()) {
                    Boolean active = snapshot.child("active").getValue(Boolean.class);
                    Long endTime = snapshot.child("endTime").getValue(Long.class);

                    if (Boolean.TRUE.equals(active) && endTime != null && endTime > System.currentTimeMillis()) {
                        bonus.isActive = true;
                        Float multiplier = snapshot.child("multiplier").getValue(Float.class);
                        bonus.multiplier = multiplier != null ? multiplier : 2.0f;
                        bonus.endTime = endTime;
                        String type = snapshot.child("type").getValue(String.class);
                        bonus.bonusType = type != null ? type : "all";
                    }
                }

                callback.onResult(bonus);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LuckyTimeBonus bonus = new LuckyTimeBonus();
                callback.onResult(bonus);
            }
        });
    }

    public interface LuckyTimeCallback {
        void onResult(LuckyTimeBonus bonus);
    }

    // ==================== PROGRESSIVE JACKPOT METHODS ====================

    public void getJackpotInfo(JackpotCallback callback) {
        dbRef.child("globalEvents").child("jackpot").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                JackpotInfo info = new JackpotInfo();

                if (snapshot.exists()) {
                    Double amount = snapshot.child("currentAmount").getValue(Double.class);
                    info.currentAmount = amount != null ? amount : 1000;

                    Long lastWon = snapshot.child("lastWonTimestamp").getValue(Long.class);
                    info.lastWonTimestamp = lastWon != null ? lastWon : 0;

                    String winner = snapshot.child("lastWinner").getValue(String.class);
                    info.lastWinner = winner != null ? winner : "No winner yet";

                    Integer contributors = snapshot.child("contributors").getValue(Integer.class);
                    info.totalContributors = contributors != null ? contributors : 0;
                }

                callback.onResult(info);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onResult(new JackpotInfo());
            }
        });
    }

    public void contributeToJackpot(double amount) {
        // Called after each spin - adds to global jackpot
        dbRef.child("globalEvents").child("jackpot").child("currentAmount")
            .runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                    Double current = mutableData.getValue(Double.class);
                    if (current == null) current = 1000.0;
                    mutableData.setValue(current + amount * 0.1); // 10% of spin goes to jackpot
                    return Transaction.success(mutableData);
                }

                @Override
                public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                    if (committed) {
                        Log.d(TAG, "Contributed to jackpot: " + amount * 0.1);
                    }
                }
            });
    }

    public interface JackpotCallback {
        void onResult(JackpotInfo info);
    }

    // ==================== COIN FLIP MINI GAME ====================

    public void playCoinFlip(double betAmount, boolean predictHeads, CoinFlipCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        if (betAmount < 1 || betAmount > 50) {
            callback.onError("Bet must be between 1 and 50 LYX");
            return;
        }

        // Check if user has enough balance
        userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double balance = snapshot.getValue(Double.class);
                if (balance == null || balance < betAmount) {
                    callback.onError("Insufficient balance");
                    return;
                }

                // Play the game
                boolean isHeads = random.nextBoolean();
                boolean won = (isHeads == predictHeads);

                CoinFlipGame game = new CoinFlipGame();
                game.betAmount = betAmount;
                game.isHeads = isHeads;
                game.won = won;

                double change = won ? betAmount : -betAmount;
                game.winAmount = won ? betAmount * 2 : 0;

                Map<String, Object> updates = new HashMap<>();
                updates.put("totalcoins", ServerValue.increment(change));
                updates.put("stats/coinFlipPlayed", ServerValue.increment(1));
                if (won) {
                    updates.put("stats/coinFlipWon", ServerValue.increment(1));
                }

                userRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // FIXED: Refresh WalletManager to sync balance
                        try {
                            WalletManager.getInstance(context).refreshBalance();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to refresh wallet after coin flip", e);
                        }
                        callback.onResult(game);
                    } else {
                        callback.onError("Game failed");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public interface CoinFlipCallback {
        void onResult(CoinFlipGame game);
        void onError(String error);
    }

    // ==================== DAILY PREDICTION GAME ====================

    public void playDailyPrediction(int predictedNumber, RewardCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        if (predictedNumber < 1 || predictedNumber > 10) {
            callback.onError("Number must be between 1 and 10");
            return;
        }

        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

        userRef.child("dailyPrediction").child(today).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    callback.onError("Already played today! Come back tomorrow.");
                    return;
                }

                // Generate today's lucky number
                int todaySeed = today.hashCode();
                Random dayRandom = new Random(todaySeed);
                int actualNumber = 1 + dayRandom.nextInt(10);

                boolean won = (predictedNumber == actualNumber);
                double reward = won ? 25.0 : 1.0; // Win big or consolation prize

                Map<String, Object> gameData = new HashMap<>();
                gameData.put("predicted", predictedNumber);
                gameData.put("actual", actualNumber);
                gameData.put("won", won);
                gameData.put("reward", reward);
                gameData.put("timestamp", ServerValue.TIMESTAMP);

                Map<String, Object> updates = new HashMap<>();
                updates.put("dailyPrediction/" + today, gameData);
                updates.put("totalcoins", ServerValue.increment(reward));

                userRef.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // FIXED: Refresh WalletManager to sync balance
                        try {
                            WalletManager.getInstance(context).refreshBalance();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to refresh wallet after prediction", e);
                        }

                        String message;
                        if (won) {
                            message = String.format(Locale.US,
                                "🎉 JACKPOT! You guessed %d and it was %d! +%.0f LYX",
                                predictedNumber, actualNumber, reward);
                        } else {
                            message = String.format(Locale.US,
                                "😅 You guessed %d, but it was %d. +%.0f LYX consolation prize!",
                                predictedNumber, actualNumber, reward);
                        }
                        callback.onSuccess(reward, message);
                    } else {
                        callback.onError("Failed to play");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void getDailyPredictionStatus(DailyPredictionCallback callback) {
        if (userRef == null) {
            callback.onError("Not logged in");
            return;
        }

        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

        userRef.child("dailyPrediction").child(today).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DailyPredictionGame game = new DailyPredictionGame();
                game.todayDate = today;

                if (snapshot.exists()) {
                    game.played = true;
                    Integer predicted = snapshot.child("predicted").getValue(Integer.class);
                    Integer actual = snapshot.child("actual").getValue(Integer.class);
                    Boolean won = snapshot.child("won").getValue(Boolean.class);
                    Double reward = snapshot.child("reward").getValue(Double.class);

                    game.predictedNumber = predicted != null ? predicted : 0;
                    game.actualNumber = actual != null ? actual : 0;
                    game.won = Boolean.TRUE.equals(won);
                    game.reward = reward != null ? reward : 0;
                }

                callback.onResult(game);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public interface DailyPredictionCallback {
        void onResult(DailyPredictionGame game);
        void onError(String error);
    }

    // ==================== LISTENER MANAGEMENT ====================

    public void addListener(EngagementListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(EngagementListener listener) {
        listeners.remove(listener);
    }

    private void setupListeners() {
        // Setup Firebase listeners for real-time updates
    }

    // ==================== UTILITY METHODS ====================

    public float getVIPMiningBonus() {
        double coins = prefs.getFloat("cachedTotalCoins", 0);
        return VIPTier.fromCoins(coins).miningBonus;
    }

    public int getVIPExtraSpins() {
        double coins = prefs.getFloat("cachedTotalCoins", 0);
        return VIPTier.fromCoins(coins).extraSpins;
    }

    public void updateCachedCoins(double coins) {
        prefs.edit().putFloat("cachedTotalCoins", (float) coins).apply();
    }
}

