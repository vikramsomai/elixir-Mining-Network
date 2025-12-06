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
import java.util.List;
import java.util.Random;

public class HourlyBonusManager {
    private static final String TAG = "HourlyBonusManager";
    private static HourlyBonusManager instance;

    private static final long BONUS_INTERVAL_MS = 4 * 60 * 60 * 1000; // 4 hours
    private static final int MAX_CLAIMS_PER_DAY = 4;
    private static final float BASE_BONUS_MIN = 0.5f;
    private static final float BASE_BONUS_MAX = 2.0f;

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;
    private List<BonusListener> listeners = new ArrayList<>();

    public interface BonusListener {
        void onBonusAvailable(float bonusAmount, long timeUntilNext);
        void onBonusClaimed(float bonusAmount, int claimsRemaining);
        void onBonusNotAvailable(long timeRemaining);
    }

    public static class BonusStatus {
        public boolean isAvailable;
        public float potentialReward;
        public long timeRemaining;
        public int claimsToday;
        public int maxClaimsPerDay;

        public BonusStatus(boolean isAvailable, float potentialReward, long timeRemaining, int claimsToday, int maxClaimsPerDay) {
            this.isAvailable = isAvailable;
            this.potentialReward = potentialReward;
            this.timeRemaining = timeRemaining;
            this.claimsToday = claimsToday;
            this.maxClaimsPerDay = maxClaimsPerDay;
        }

        public String getTimeRemainingFormatted() {
            if (timeRemaining <= 0) return "Ready!";
            long hours = timeRemaining / (60 * 60 * 1000);
            long minutes = (timeRemaining % (60 * 60 * 1000)) / (60 * 1000);
            long seconds = (timeRemaining % (60 * 1000)) / 1000;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    private HourlyBonusManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized HourlyBonusManager getInstance(Context context) {
        if (instance == null) {
            instance = new HourlyBonusManager(context);
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
            prefs = context.getSharedPreferences("HourlyBonus_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            resetDailyClaimsIfNeeded();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing HourlyBonusManager", e);
        }
    }

    private void resetDailyClaimsIfNeeded() {
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String lastClaimDate = prefs.getString("lastClaimDate", "");

        if (!today.equals(lastClaimDate)) {
            prefs.edit()
                    .putString("lastClaimDate", today)
                    .putInt("claimsToday", 0)
                    .apply();
            Log.d(TAG, "Daily claims reset for new day");
        }
    }

    public BonusStatus getBonusStatus() {
        resetDailyClaimsIfNeeded();

        long lastClaimTime = prefs.getLong("lastClaimTime", 0);
        int claimsToday = prefs.getInt("claimsToday", 0);
        long currentTime = System.currentTimeMillis();
        long timeSinceLastClaim = currentTime - lastClaimTime;
        long timeRemaining = Math.max(0, BONUS_INTERVAL_MS - timeSinceLastClaim);

        boolean isAvailable = timeRemaining == 0 && claimsToday < MAX_CLAIMS_PER_DAY;
        float potentialReward = calculatePotentialReward();

        return new BonusStatus(isAvailable, potentialReward, timeRemaining, claimsToday, MAX_CLAIMS_PER_DAY);
    }

    public boolean isBonusAvailable() {
        return getBonusStatus().isAvailable;
    }

    public long getTimeUntilNextBonus() {
        return getBonusStatus().timeRemaining;
    }

    private float calculatePotentialReward() {
        Random random = new Random();
        float baseReward = BASE_BONUS_MIN + random.nextFloat() * (BASE_BONUS_MAX - BASE_BONUS_MIN);

        float multiplier = 1.0f;
        int userLevel = prefs.getInt("userLevel", 1);
        multiplier += (userLevel - 1) * 0.1f;

        int streak = prefs.getInt("currentStreak", 0);
        if (streak >= 7) multiplier += 0.2f;
        else if (streak >= 3) multiplier += 0.1f;

        return baseReward * multiplier;
    }

    public void claimBonus(ClaimCallback callback) {
        resetDailyClaimsIfNeeded();

        BonusStatus status = getBonusStatus();
        if (!status.isAvailable) {
            if (callback != null) {
                callback.onClaimFailed("Bonus not available yet. " + status.getTimeRemainingFormatted() + " remaining.");
            }
            return;
        }

        float bonusAmount = calculatePotentialReward();
        long currentTime = System.currentTimeMillis();
        int newClaimsCount = status.claimsToday + 1;

        prefs.edit()
                .putLong("lastClaimTime", currentTime)
                .putInt("claimsToday", newClaimsCount)
                .apply();

        if (userRef != null) {
            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Double current = snapshot.getValue(Double.class);
                    if (current == null) current = 0.0;
                    double newBalance = current + bonusAmount;
                    userRef.child("totalcoins").setValue(newBalance);

                    String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                            .format(new java.util.Date());
                    userRef.child("hourlyBonusClaims").child(today).child(String.valueOf(currentTime))
                            .setValue(bonusAmount);

                    if (callback != null) {
                        callback.onClaimSuccess(bonusAmount, newBalance, MAX_CLAIMS_PER_DAY - newClaimsCount);
                    }
                    Log.d(TAG, "Hourly bonus claimed: " + bonusAmount + " LYX");
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    if (callback != null) {
                        callback.onClaimFailed("Database error: " + error.getMessage());
                    }
                }
            });
        } else {
            if (callback != null) {
                callback.onClaimFailed("Not connected to database");
            }
        }
    }

    public void updateUserStats(int level, int streak) {
        prefs.edit()
                .putInt("userLevel", level)
                .putInt("currentStreak", streak)
                .apply();
    }

    public interface ClaimCallback {
        void onClaimSuccess(float bonusAmount, double newBalance, int claimsRemaining);
        void onClaimFailed(String error);
    }

    public void addListener(BonusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(BonusListener listener) {
        listeners.remove(listener);
    }
}

