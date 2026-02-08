package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

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
 * DailyEventsManager - Manages daily challenges, events, and engagement features
 * Features:
 * - Daily Challenges (3 tasks per day)
 * - Check-in Calendar (7-day streaks)
 * - Mystery Box (time-limited rewards)
 * - Multiplier Rush Hours (2x/3x rewards at specific times)
 * - Daily Spin Wheel (ad-supported)
 */
public class DailyEventsManager {
    private static final String TAG = "DailyEventsManager";
    private static DailyEventsManager instance;

    private static final String PREFS_NAME = "DailyEvents";

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;
    private Random random = new Random();

    // ==================== DAILY CHALLENGES ====================

    public enum ChallengeType {
        MINING_TIME("⛏️", "Mining Marathon", "Mine for 2 hours today", 2.0, 5.0),
        WATCH_ADS("📺", "Ad Watcher", "Watch 3 reward ads", 3, 3.0),
        REFER_FRIEND("👥", "Social Butterfly", "Invite 1 friend", 1, 10.0),
        HOURLY_BONUS("⏰", "Bonus Hunter", "Claim 2 hourly bonuses", 2, 4.0),
        SCRATCH_CARDS("🎫", "Lucky Scratcher", "Use 2 scratch cards", 2, 4.0),
        CHECK_IN("📅", "Daily Visitor", "Open the app", 1, 1.0),
        STREAK_KEEPER("🔥", "Streak Keeper", "Maintain mining streak", 1, 5.0),
        LUCKY_NUMBER("🎲", "Fortune Teller", "Play lucky number game", 1, 2.0);

        public final String icon;
        public final String title;
        public final String description;
        public final double target;
        public final double reward;

        ChallengeType(String icon, String title, String description, double target, double reward) {
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.target = target;
            this.reward = reward;
        }
    }

    public static class DailyChallenge {
        public ChallengeType type;
        public double progress;
        public double target;
        public double reward;
        public boolean completed;
        public boolean claimed;

        public DailyChallenge(ChallengeType type) {
            this.type = type;
            this.progress = 0;
            this.target = type.target;
            this.reward = type.reward;
            this.completed = false;
            this.claimed = false;
        }

        public float getProgressPercent() {
            return (float) Math.min(progress / target, 1.0);
        }

        public String getProgressText() {
            return String.format(Locale.US, "%.0f/%.0f", Math.min(progress, target), target);
        }
    }

    // ==================== CHECK-IN CALENDAR ====================

    public static class CheckInStatus {
        public int currentStreak;
        public int longestStreak;
        public boolean checkedInToday;
        public int[] weeklyCheckIns; // 0 = not checked, 1 = checked, 2 = today
        public double todayReward;
        public double weeklyBonusReward;
        public boolean canClaimWeeklyBonus;

        public static final double[] DAILY_REWARDS = {1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0};
        public static final double WEEKLY_BONUS = 15.0;
    }

    // ==================== MYSTERY BOX ====================

    public enum MysteryBoxTier {
        BRONZE("🥉", "Bronze Box", 1.0, 5.0, 3),
        SILVER("🥈", "Silver Box", 5.0, 15.0, 2),
        GOLD("🥇", "Gold Box", 15.0, 50.0, 1);

        public final String icon;
        public final String name;
        public final double minReward;
        public final double maxReward;
        public final int maxPerDay;

        MysteryBoxTier(String icon, String name, double minReward, double maxReward, int maxPerDay) {
            this.icon = icon;
            this.name = name;
            this.minReward = minReward;
            this.maxReward = maxReward;
            this.maxPerDay = maxPerDay;
        }
    }

    public static class MysteryBoxResult {
        public MysteryBoxTier tier;
        public double reward;
        public String bonusType; // "tokens", "boost", "scratch_cards"
        public int bonusAmount;
        public String message;
    }

    // ==================== MULTIPLIER RUSH ====================

    public static class MultiplierRushStatus {
        public boolean isActive;
        public float multiplier;
        public long endTime;
        public String eventName;
        public long timeRemaining;

        public String getTimeRemainingFormatted() {
            if (timeRemaining <= 0) return "Ended";
            long hours = timeRemaining / (60 * 60 * 1000);
            long minutes = (timeRemaining % (60 * 60 * 1000)) / (60 * 1000);
            return String.format(Locale.US, "%dh %dm", hours, minutes);
        }
    }

    // ==================== SPIN WHEEL ====================

    public static class SpinWheelResult {
        public int segment; // 0-7
        public double reward;
        public String rewardType; // "tokens", "boost", "scratch_card", "mystery_box"
        public String displayText;

        public static final String[] SEGMENT_LABELS = {
            "1 LYX", "2 LYX", "5 LYX", "🎫 Card",
            "10 LYX", "2x Boost", "🎁 Mystery", "25 LYX"
        };
        public static final double[] SEGMENT_WEIGHTS = {
            25, 25, 15, 15, 10, 5, 3, 2
        }; // Probability weights
    }

    // ==================== SINGLETON ====================

    private DailyEventsManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized DailyEventsManager getInstance(Context context) {
        if (instance == null) {
            instance = new DailyEventsManager(context);
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    private void initialize() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                userId = auth.getCurrentUser().getUid();
                prefs = context.getSharedPreferences(PREFS_NAME + "_" + userId, Context.MODE_PRIVATE);
                userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
                checkAndResetDaily();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing DailyEventsManager", e);
        }
    }

    private String getTodayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private void checkAndResetDaily() {
        String today = getTodayKey();
        String lastDate = prefs.getString("lastResetDate", "");

        if (!today.equals(lastDate)) {
            // New day - reset daily challenges and counters
            prefs.edit()
                .putString("lastResetDate", today)
                .putInt("dailySpinsUsed", 0)
                .putInt("bronzeBoxesOpened", 0)
                .putInt("silverBoxesOpened", 0)
                .putInt("goldBoxesOpened", 0)
                .putInt("adsWatchedToday", 0)
                .putString("dailyChallenges", "")
                .apply();

            generateDailyChallenges();
            Log.d(TAG, "Daily reset completed for " + today);
        }
    }

    // ==================== DAILY CHALLENGES METHODS ====================

    private void generateDailyChallenges() {
        // Pick 3 random challenges for today
        List<ChallengeType> allChallenges = new ArrayList<>();
        for (ChallengeType type : ChallengeType.values()) {
            allChallenges.add(type);
        }

        // Shuffle and pick 3
        java.util.Collections.shuffle(allChallenges);
        List<ChallengeType> todaysChallenges = allChallenges.subList(0, 3);

        StringBuilder sb = new StringBuilder();
        for (ChallengeType type : todaysChallenges) {
            if (sb.length() > 0) sb.append(",");
            sb.append(type.name());
        }
        prefs.edit().putString("dailyChallenges", sb.toString()).apply();
    }

    public List<DailyChallenge> getDailyChallenges() {
        List<DailyChallenge> challenges = new ArrayList<>();
        String challengesStr = prefs.getString("dailyChallenges", "");

        if (challengesStr.isEmpty()) {
            generateDailyChallenges();
            challengesStr = prefs.getString("dailyChallenges", "");
        }

        String[] challengeNames = challengesStr.split(",");
        for (String name : challengeNames) {
            try {
                ChallengeType type = ChallengeType.valueOf(name.trim());
                DailyChallenge challenge = new DailyChallenge(type);
                challenge.progress = prefs.getFloat("challenge_" + name + "_progress", 0);
                challenge.completed = prefs.getBoolean("challenge_" + name + "_completed", false);
                challenge.claimed = prefs.getBoolean("challenge_" + name + "_claimed", false);
                challenges.add(challenge);
            } catch (Exception e) {
                Log.e(TAG, "Error loading challenge: " + name, e);
            }
        }

        return challenges;
    }

    public void updateChallengeProgress(ChallengeType type, double amount) {
        String key = "challenge_" + type.name();
        float currentProgress = prefs.getFloat(key + "_progress", 0);
        float newProgress = currentProgress + (float) amount;
        boolean wasCompleted = prefs.getBoolean(key + "_completed", false);

        prefs.edit()
            .putFloat(key + "_progress", newProgress)
            .putBoolean(key + "_completed", newProgress >= type.target || wasCompleted)
            .apply();

        Log.d(TAG, "Challenge progress updated: " + type.name() + " = " + newProgress);
    }

    public interface ChallengeClaimCallback {
        void onClaimed(double reward);
        void onError(String message);
    }

    public void claimChallengeReward(DailyChallenge challenge, ChallengeClaimCallback callback) {
        if (!challenge.completed || challenge.claimed) {
            callback.onError("Challenge not completed or already claimed");
            return;
        }

        String key = "challenge_" + challenge.type.name() + "_claimed";
        prefs.edit().putBoolean(key, true).apply();

        // Add reward to user's balance
        if (userRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalcoins", ServerValue.increment(challenge.reward));
            userRef.updateChildren(updates).addOnCompleteListener(task -> {
                // FIXED: Notify WalletManager so balance updates immediately
                if (task.isSuccessful()) {
                    try {
                        WalletManager.getInstance(context).refreshBalance();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to notify WalletManager", e);
                    }
                }
            });
        }

        callback.onClaimed(challenge.reward);
    }

    public int getCompletedChallengesCount() {
        List<DailyChallenge> challenges = getDailyChallenges();
        int count = 0;
        for (DailyChallenge c : challenges) {
            if (c.completed) count++;
        }
        return count;
    }

    public double getTotalChallengeRewards() {
        List<DailyChallenge> challenges = getDailyChallenges();
        double total = 0;
        for (DailyChallenge c : challenges) {
            if (c.claimed) total += c.reward;
        }
        return total;
    }

    // ==================== CHECK-IN CALENDAR METHODS ====================

    public CheckInStatus getCheckInStatus() {
        CheckInStatus status = new CheckInStatus();

        status.currentStreak = prefs.getInt("checkInStreak", 0);
        status.longestStreak = prefs.getInt("longestStreak", 0);
        status.checkedInToday = prefs.getString("lastCheckIn", "").equals(getTodayKey());

        // Calculate weekly check-ins
        status.weeklyCheckIns = new int[7];
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);

        for (int i = 0; i < 7; i++) {
            String dayKey = sdf.format(cal.getTime());
            boolean isToday = dayKey.equals(getTodayKey());

            if (isToday) {
                status.weeklyCheckIns[i] = status.checkedInToday ? 1 : 2;
            } else {
                status.weeklyCheckIns[i] = prefs.getBoolean("checkin_" + dayKey, false) ? 1 : 0;
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Calculate rewards
        int streakDay = Math.min(status.currentStreak % 7, 6);
        status.todayReward = CheckInStatus.DAILY_REWARDS[streakDay];

        // Check weekly bonus eligibility
        int checkIns = 0;
        for (int c : status.weeklyCheckIns) {
            if (c == 1) checkIns++;
        }
        status.canClaimWeeklyBonus = checkIns >= 7 && !prefs.getBoolean("weeklyBonusClaimed_" + getWeekKey(), false);
        status.weeklyBonusReward = CheckInStatus.WEEKLY_BONUS;

        return status;
    }

    private String getWeekKey() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "_" + cal.get(Calendar.WEEK_OF_YEAR);
    }

    public interface CheckInCallback {
        void onCheckInSuccess(double reward, int newStreak);
        void onAlreadyCheckedIn();
        void onError(String message);
    }

    public void performCheckIn(CheckInCallback callback) {
        String today = getTodayKey();
        if (prefs.getString("lastCheckIn", "").equals(today)) {
            callback.onAlreadyCheckedIn();
            return;
        }

        String yesterday = getYesterdayKey();
        int streak = prefs.getInt("checkInStreak", 0);

        // Check if streak continues or resets
        if (prefs.getString("lastCheckIn", "").equals(yesterday)) {
            streak++;
        } else {
            streak = 1;
        }

        int longestStreak = Math.max(streak, prefs.getInt("longestStreak", 0));

        prefs.edit()
            .putString("lastCheckIn", today)
            .putBoolean("checkin_" + today, true)
            .putInt("checkInStreak", streak)
            .putInt("longestStreak", longestStreak)
            .apply();

        // Calculate reward
        int streakDay = Math.min((streak - 1) % 7, 6);
        double reward = CheckInStatus.DAILY_REWARDS[streakDay];

        // Add reward to user's balance
        if (userRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalcoins", ServerValue.increment(reward));
            userRef.updateChildren(updates);
        }

        // Update check-in challenge
        updateChallengeProgress(ChallengeType.CHECK_IN, 1);

        callback.onCheckInSuccess(reward, streak);
    }

    private String getYesterdayKey() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.getTime());
    }

    public void claimWeeklyBonus(CheckInCallback callback) {
        CheckInStatus status = getCheckInStatus();
        if (!status.canClaimWeeklyBonus) {
            callback.onError("Weekly bonus not available");
            return;
        }

        prefs.edit().putBoolean("weeklyBonusClaimed_" + getWeekKey(), true).apply();

        // Add reward
        if (userRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalcoins", ServerValue.increment(CheckInStatus.WEEKLY_BONUS));
            userRef.updateChildren(updates);
        }

        callback.onCheckInSuccess(CheckInStatus.WEEKLY_BONUS, status.currentStreak);
    }

    // ==================== MYSTERY BOX METHODS ====================

    public boolean canOpenMysteryBox(MysteryBoxTier tier) {
        String key = tier.name().toLowerCase() + "BoxesOpened";
        int opened = prefs.getInt(key, 0);
        return opened < tier.maxPerDay;
    }

    public int getMysteryBoxesRemaining(MysteryBoxTier tier) {
        String key = tier.name().toLowerCase() + "BoxesOpened";
        int opened = prefs.getInt(key, 0);
        return Math.max(0, tier.maxPerDay - opened);
    }

    public interface MysteryBoxCallback {
        void onBoxOpened(MysteryBoxResult result);
        void onError(String message);
    }

    public void openMysteryBox(MysteryBoxTier tier, MysteryBoxCallback callback) {
        if (!canOpenMysteryBox(tier)) {
            callback.onError("No more " + tier.name + " boxes available today");
            return;
        }

        String key = tier.name().toLowerCase() + "BoxesOpened";
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();

        MysteryBoxResult result = new MysteryBoxResult();
        result.tier = tier;

        // Random reward calculation
        double range = tier.maxReward - tier.minReward;
        result.reward = tier.minReward + (random.nextDouble() * range);
        result.reward = Math.round(result.reward * 100.0) / 100.0;

        // Random bonus type
        int bonusRoll = random.nextInt(100);
        if (bonusRoll < 70) {
            result.bonusType = "tokens";
            result.bonusAmount = 0;
            result.message = String.format(Locale.US, "You won %.2f LYX!", result.reward);
        } else if (bonusRoll < 90) {
            result.bonusType = "scratch_cards";
            result.bonusAmount = tier == MysteryBoxTier.GOLD ? 2 : 1;
            result.message = String.format(Locale.US, "You won %.2f LYX + %d Scratch Card(s)!", result.reward, result.bonusAmount);
        } else {
            result.bonusType = "boost";
            result.bonusAmount = tier == MysteryBoxTier.GOLD ? 30 : (tier == MysteryBoxTier.SILVER ? 15 : 5);
            result.message = String.format(Locale.US, "You won %.2f LYX + %d min 2x Boost!", result.reward, result.bonusAmount);
        }

        // Apply rewards
        if (userRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalcoins", ServerValue.increment(result.reward));
            userRef.updateChildren(updates);
        }

        callback.onBoxOpened(result);
    }

    // ==================== MULTIPLIER RUSH METHODS ====================

    public MultiplierRushStatus getMultiplierRushStatus() {
        MultiplierRushStatus status = new MultiplierRushStatus();

        // Check for scheduled rush hours (e.g., 12-1 PM and 8-9 PM)
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 12 && hour < 13) {
            status.isActive = true;
            status.multiplier = 2.0f;
            status.eventName = "🌞 Lunch Rush";
            cal.set(Calendar.HOUR_OF_DAY, 13);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            status.endTime = cal.getTimeInMillis();
        } else if (hour >= 20 && hour < 21) {
            status.isActive = true;
            status.multiplier = 3.0f;
            status.eventName = "🌙 Night Surge";
            cal.set(Calendar.HOUR_OF_DAY, 21);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            status.endTime = cal.getTimeInMillis();
        } else if (hour >= 8 && hour < 9) {
            status.isActive = true;
            status.multiplier = 1.5f;
            status.eventName = "☀️ Morning Boost";
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            status.endTime = cal.getTimeInMillis();
        } else {
            status.isActive = false;
            status.multiplier = 1.0f;
            status.eventName = "No active rush";

            // Calculate next rush
            if (hour < 8) {
                cal.set(Calendar.HOUR_OF_DAY, 8);
            } else if (hour < 12) {
                cal.set(Calendar.HOUR_OF_DAY, 12);
            } else if (hour < 20) {
                cal.set(Calendar.HOUR_OF_DAY, 20);
            } else {
                cal.add(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 8);
            }
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            status.endTime = cal.getTimeInMillis();
        }

        status.timeRemaining = status.endTime - System.currentTimeMillis();
        return status;
    }

    // ==================== SPIN WHEEL METHODS ====================

    public int getDailySpinsRemaining() {
        int maxSpins = 3;
        int used = prefs.getInt("dailySpinsUsed", 0);
        return Math.max(0, maxSpins - used);
    }

    public boolean canSpin() {
        return getDailySpinsRemaining() > 0;
    }

    public interface SpinCallback {
        void onSpinComplete(SpinWheelResult result);
        void onNoSpinsRemaining();
        void onError(String message);
    }

    public void performSpin(SpinCallback callback) {
        if (!canSpin()) {
            callback.onNoSpinsRemaining();
            return;
        }

        prefs.edit().putInt("dailySpinsUsed", prefs.getInt("dailySpinsUsed", 0) + 1).apply();

        // Calculate weighted random segment
        double totalWeight = 0;
        for (double w : SpinWheelResult.SEGMENT_WEIGHTS) totalWeight += w;

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        int segment = 0;

        for (int i = 0; i < SpinWheelResult.SEGMENT_WEIGHTS.length; i++) {
            cumulative += SpinWheelResult.SEGMENT_WEIGHTS[i];
            if (roll <= cumulative) {
                segment = i;
                break;
            }
        }

        SpinWheelResult result = new SpinWheelResult();
        result.segment = segment;
        result.displayText = SpinWheelResult.SEGMENT_LABELS[segment];

        // Parse reward
        switch (segment) {
            case 0: result.reward = 1.0; result.rewardType = "tokens"; break;
            case 1: result.reward = 2.0; result.rewardType = "tokens"; break;
            case 2: result.reward = 5.0; result.rewardType = "tokens"; break;
            case 3: result.reward = 1.0; result.rewardType = "scratch_card"; break;
            case 4: result.reward = 10.0; result.rewardType = "tokens"; break;
            case 5: result.reward = 30.0; result.rewardType = "boost"; break;
            case 6: result.reward = 0; result.rewardType = "mystery_box"; break;
            case 7: result.reward = 25.0; result.rewardType = "tokens"; break;
        }

        // Apply token rewards immediately
        if (result.rewardType.equals("tokens") && userRef != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("totalcoins", ServerValue.increment(result.reward));
            userRef.updateChildren(updates);
        }

        // Track ads watched
        prefs.edit().putInt("adsWatchedToday", prefs.getInt("adsWatchedToday", 0) + 1).apply();
        updateChallengeProgress(ChallengeType.WATCH_ADS, 1);

        callback.onSpinComplete(result);
    }

    // ==================== AD TRACKING ====================

    public int getAdsWatchedToday() {
        return prefs.getInt("adsWatchedToday", 0);
    }

    public void recordAdWatched() {
        prefs.edit().putInt("adsWatchedToday", prefs.getInt("adsWatchedToday", 0) + 1).apply();
        updateChallengeProgress(ChallengeType.WATCH_ADS, 1);
    }
}

