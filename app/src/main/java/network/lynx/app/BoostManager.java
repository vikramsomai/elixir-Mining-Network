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

public class BoostManager {
    private static final String TAG = "BoostManager";
    private static BoostManager instance;
    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;

    // Enhanced base rates for better rewards
    private static final float BASE_RATE_PER_SECOND = 0.00125f;  // ~4.5 LYX/hour base
    private static final float AD_BOOST_MULTIPLIER = 2.0f;       // 2x when ad is watched
    private static final float TEMPORARY_BOOST_MULTIPLIER = 1.5f; // 1.5x temporary boost
    private static final float TWITTER_BOOST_MULTIPLIER = 1.2f;   // 1.2x Twitter follow
    private static final float DAILY_CHECKIN_MULTIPLIER = 1.1f;   // 1.1x daily checkin

    // Current boost states
    private boolean isAdWatched = false;
    private boolean isTemporaryBoostActive = false;
    private long temporaryBoostExpirationTime = 0;
    private float permanentBoostMultiplier = 1.0f;
    private boolean hasPermanentBoost = false;

    // New daily boost states
    private boolean isTwitterBoostActive = false;
    private long twitterBoostExpirationTime = 0;
    private boolean isDailyCheckinBoostActive = false;
    private long dailyCheckinBoostExpirationTime = 0;

    // Listeners for boost changes
    private List<BoostChangeListener> listeners = new ArrayList<>();

    public interface BoostChangeListener {
        void onBoostStateChanged(float currentMiningRate, String boostInfo);
        void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier);
    }

    private BoostManager(Context context) {
        this.context = context.getApplicationContext();
        initializeBoostManager();
    }

    public static synchronized BoostManager getInstance(Context context) {
        if (instance == null) {
            instance = new BoostManager(context);
        }
        return instance;
    }

    public static BoostManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BoostManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }

    private void initializeBoostManager() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in, cannot initialize BoostManager");
                return;
            }
            userId = auth.getCurrentUser().getUid();
            prefs = context.getSharedPreferences("BoostManager_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            loadCachedBoostStates();
            loadBoostStatesFromFirebase();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BoostManager", e);
        }
    }

    private void loadCachedBoostStates() {
        isAdWatched = prefs.getBoolean("isAdWatched", false);
        isTemporaryBoostActive = prefs.getBoolean("isTemporaryBoostActive", false);
        temporaryBoostExpirationTime = prefs.getLong("temporaryBoostExpirationTime", 0);
        permanentBoostMultiplier = prefs.getFloat("permanentBoostMultiplier", 1.0f);
        hasPermanentBoost = prefs.getBoolean("hasPermanentBoost", false);

        // Load daily boost states
        isTwitterBoostActive = prefs.getBoolean("isTwitterBoostActive", false);
        twitterBoostExpirationTime = prefs.getLong("twitterBoostExpirationTime", 0);
        isDailyCheckinBoostActive = prefs.getBoolean("isDailyCheckinBoostActive", false);
        dailyCheckinBoostExpirationTime = prefs.getLong("dailyCheckinBoostExpirationTime", 0);

        // Check if boosts have expired
        checkAndDeactivateExpiredBoosts();
    }

    private void checkAndDeactivateExpiredBoosts() {
        long currentTime = System.currentTimeMillis();

        if (isTemporaryBoostActive && currentTime >= temporaryBoostExpirationTime) {
            deactivateTemporaryBoost();
        }

        if (isTwitterBoostActive && currentTime >= twitterBoostExpirationTime) {
            deactivateTwitterBoost();
        }

        if (isDailyCheckinBoostActive && currentTime >= dailyCheckinBoostExpirationTime) {
            deactivateDailyCheckinBoost();
        }
    }

    private void loadBoostStatesFromFirebase() {
        if (userRef == null) {
            Log.e(TAG, "userRef is null, cannot load boost states");
            return;
        }

        // OPTIMIZATION: Use single value event listener instead of continuous listener
        // Load permanent boosts
        userRef.child("permanentBoosts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    boolean oldHasPermanentBoost = hasPermanentBoost;
                    float oldPermanentMultiplier = permanentBoostMultiplier;

                    permanentBoostMultiplier = 1.0f;
                    hasPermanentBoost = false;

                    if (snapshot.child("invite3Friends").exists()) {
                        Boolean isActive = snapshot.child("invite3Friends").getValue(Boolean.class);
                        if (isActive != null && isActive) {
                            Double multiplier = snapshot.child("invite3FriendsMultiplier").getValue(Double.class);
                            if (multiplier != null) {
                                permanentBoostMultiplier = multiplier.floatValue();
                                hasPermanentBoost = true;
                            }
                        }
                    }

                    // Cache the values
                    if (prefs != null) {
                        prefs.edit()
                                .putFloat("permanentBoostMultiplier", permanentBoostMultiplier)
                                .putBoolean("hasPermanentBoost", hasPermanentBoost)
                                .apply();
                    }

                    // Notify listeners if changed
                    if (oldHasPermanentBoost != hasPermanentBoost || oldPermanentMultiplier != permanentBoostMultiplier) {
                        notifyPermanentBoostChanged();
                    }
                    notifyBoostStateChanged();
                } catch (Exception e) {
                    Log.e(TAG, "Error processing permanent boosts", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading permanent boosts", error.toException());
            }
        });

        // Load active boosts from Firebase - also use single value listener
        userRef.child("activeBoosts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    long currentTime = System.currentTimeMillis();

                    // Check temporary boost
                    if (snapshot.child("temporaryBoost").exists()) {
                        Long expirationTime = snapshot.child("temporaryBoost").child("expirationTime").getValue(Long.class);
                        if (expirationTime != null && expirationTime > currentTime) {
                            activateTemporaryBoost(expirationTime);
                        } else if (isTemporaryBoostActive) {
                            deactivateTemporaryBoost();
                        }
                    }

                    // Check Twitter boost
                    if (snapshot.child("twitterFollow").exists()) {
                        Long expirationTime = snapshot.child("twitterFollow").child("expirationTime").getValue(Long.class);
                        if (expirationTime != null && expirationTime > currentTime) {
                            activateTwitterBoost(expirationTime);
                        } else if (isTwitterBoostActive) {
                            deactivateTwitterBoost();
                        }
                    }

                    // Check daily checkin boost
                    if (snapshot.child("dailyCheckin").exists()) {
                        Long expirationTime = snapshot.child("dailyCheckin").child("expirationTime").getValue(Long.class);
                        if (expirationTime != null && expirationTime > currentTime) {
                            activateDailyCheckinBoost(expirationTime);
                        } else if (isDailyCheckinBoostActive) {
                            deactivateDailyCheckinBoost();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing active boosts", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading active boosts", error.toException());
            }
        });
    }

    /**
     * Manually refresh boost states from Firebase
     * Call this when needed (e.g., when boost is activated)
     */
    public void refreshBoostStates() {
        loadBoostStatesFromFirebase();
    }

    // RATE CALCULATION METHODS
    public float getCurrentMiningRatePerSecond() {
        float rate = BASE_RATE_PER_SECOND;

        // Apply ad boost
        if (isAdWatched) {
            rate *= AD_BOOST_MULTIPLIER;
        }

        // Apply permanent boost
        if (hasPermanentBoost) {
            rate *= permanentBoostMultiplier;
        }

        // Apply temporary boost
        if (isTemporaryBoostActive && System.currentTimeMillis() <= temporaryBoostExpirationTime) {
            rate *= TEMPORARY_BOOST_MULTIPLIER;
        }

        // Apply Twitter boost
        if (isTwitterBoostActive && System.currentTimeMillis() <= twitterBoostExpirationTime) {
            rate *= TWITTER_BOOST_MULTIPLIER;
        }

        // Apply daily checkin boost
        if (isDailyCheckinBoostActive && System.currentTimeMillis() <= dailyCheckinBoostExpirationTime) {
            rate *= DAILY_CHECKIN_MULTIPLIER;
        }

        // NEW: Apply mining streak multiplier
        try {
            MiningStreakManager streakManager = MiningStreakManager.getInstance(context);
            rate *= streakManager.getCurrentMultiplier();
        } catch (Exception e) {
            // MiningStreakManager not initialized
        }

        // NEW: Apply lucky number boost
        try {
            DailyLuckyNumberManager luckyManager = DailyLuckyNumberManager.getInstance(context);
            if (luckyManager.hasActiveBoost()) {
                rate *= luckyManager.getActiveBoostMultiplier();
            }
        } catch (Exception e) {
            // DailyLuckyNumberManager not initialized
        }

        // NEW: Apply achievement boost
        try {
            AchievementManager achievementManager = AchievementManager.getInstance(context);
            float achievementBoost = achievementManager.getTotalAchievementBoost();
            if (achievementBoost > 0) {
                rate *= (1 + achievementBoost);
            }
        } catch (Exception e) {
            // AchievementManager not initialized
        }

        return rate;
    }

    public float getCurrentMiningRatePerHour() {
        return getCurrentMiningRatePerSecond() * 3600f;
    }

    public float calculateMiningAmount(long durationMillis) {
        float ratePerSecond = getCurrentMiningRatePerSecond();
        float durationSeconds = durationMillis / 1000f;
        return ratePerSecond * durationSeconds;
    }

    public float getTotalMultiplier() {
        float multiplier = 1.0f;
        if (isAdWatched) multiplier *= AD_BOOST_MULTIPLIER;
        if (hasPermanentBoost) multiplier *= permanentBoostMultiplier;
        if (isTemporaryBoostActive && System.currentTimeMillis() <= temporaryBoostExpirationTime) {
            multiplier *= TEMPORARY_BOOST_MULTIPLIER;
        }
        if (isTwitterBoostActive && System.currentTimeMillis() <= twitterBoostExpirationTime) {
            multiplier *= TWITTER_BOOST_MULTIPLIER;
        }
        if (isDailyCheckinBoostActive && System.currentTimeMillis() <= dailyCheckinBoostExpirationTime) {
            multiplier *= DAILY_CHECKIN_MULTIPLIER;
        }
        return multiplier;
    }

    // BOOST ACTIVATION METHODS
    public void setAdWatched(boolean watched) {
        this.isAdWatched = watched;
        prefs.edit().putBoolean("isAdWatched", watched).apply();
        notifyBoostStateChanged();
        Log.d(TAG, "Ad watched state changed: " + watched + ", New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");
    }

    public void activateTemporaryBoost(long expirationTime) {
        this.isTemporaryBoostActive = true;
        this.temporaryBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isTemporaryBoostActive", true)
                .putLong("temporaryBoostExpirationTime", expirationTime)
                .apply();
        notifyBoostStateChanged();

        Log.d(TAG, "Temporary boost activated until: " + expirationTime + ", New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");

        // Schedule deactivation
        scheduleBoostDeactivation(expirationTime - System.currentTimeMillis(), this::checkAndDeactivateTemporaryBoost);
    }

    public void activateTwitterBoost(long expirationTime) {
        this.isTwitterBoostActive = true;
        this.twitterBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isTwitterBoostActive", true)
                .putLong("twitterBoostExpirationTime", expirationTime)
                .apply();
        notifyBoostStateChanged();

        Log.d(TAG, "Twitter boost activated until: " + expirationTime + ", New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");

        // Schedule deactivation
        scheduleBoostDeactivation(expirationTime - System.currentTimeMillis(), this::checkAndDeactivateTwitterBoost);
    }

    public void activateDailyCheckinBoost(long expirationTime) {
        this.isDailyCheckinBoostActive = true;
        this.dailyCheckinBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isDailyCheckinBoostActive", true)
                .putLong("dailyCheckinBoostExpirationTime", expirationTime)
                .apply();
        notifyBoostStateChanged();

        Log.d(TAG, "Daily checkin boost activated until: " + expirationTime + ", New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");

        // Schedule deactivation
        scheduleBoostDeactivation(expirationTime - System.currentTimeMillis(), this::checkAndDeactivateDailyCheckinBoost);
    }

    private void scheduleBoostDeactivation(long delay, Runnable deactivationTask) {
        if (delay > 0) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(deactivationTask, delay);
        }
    }

    // BOOST DEACTIVATION METHODS
    public void deactivateTemporaryBoost() {
        this.isTemporaryBoostActive = false;
        this.temporaryBoostExpirationTime = 0;
        prefs.edit()
                .putBoolean("isTemporaryBoostActive", false)
                .putLong("temporaryBoostExpirationTime", 0)
                .apply();
        notifyBoostStateChanged();
        Log.d(TAG, "Temporary boost deactivated, New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");
    }

    private void deactivateTwitterBoost() {
        this.isTwitterBoostActive = false;
        this.twitterBoostExpirationTime = 0;
        prefs.edit()
                .putBoolean("isTwitterBoostActive", false)
                .putLong("twitterBoostExpirationTime", 0)
                .apply();
        notifyBoostStateChanged();
        Log.d(TAG, "Twitter boost deactivated, New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");
    }

    private void deactivateDailyCheckinBoost() {
        this.isDailyCheckinBoostActive = false;
        this.dailyCheckinBoostExpirationTime = 0;
        prefs.edit()
                .putBoolean("isDailyCheckinBoostActive", false)
                .putLong("dailyCheckinBoostExpirationTime", 0)
                .apply();
        notifyBoostStateChanged();
        Log.d(TAG, "Daily checkin boost deactivated, New rate: " + getCurrentMiningRatePerHour() + " LYX/hour");
    }

    private void checkAndDeactivateTemporaryBoost() {
        if (isTemporaryBoostActive && System.currentTimeMillis() >= temporaryBoostExpirationTime) {
            deactivateTemporaryBoost();
        }
    }

    private void checkAndDeactivateTwitterBoost() {
        if (isTwitterBoostActive && System.currentTimeMillis() >= twitterBoostExpirationTime) {
            deactivateTwitterBoost();
        }
    }

    private void checkAndDeactivateDailyCheckinBoost() {
        if (isDailyCheckinBoostActive && System.currentTimeMillis() >= dailyCheckinBoostExpirationTime) {
            deactivateDailyCheckinBoost();
        }
    }

    // GETTERS
    public boolean isAdWatched() { return isAdWatched; }
    public boolean isTemporaryBoostActive() {
        return isTemporaryBoostActive && System.currentTimeMillis() <= temporaryBoostExpirationTime;
    }
    public boolean hasPermanentBoost() { return hasPermanentBoost; }
    public float getPermanentBoostMultiplier() { return permanentBoostMultiplier; }
    public long getTemporaryBoostTimeRemaining() {
        if (!isTemporaryBoostActive) return 0;
        return Math.max(0, temporaryBoostExpirationTime - System.currentTimeMillis());
    }

    // New getters for daily boosts
    public boolean isTwitterBoostActive() {
        return isTwitterBoostActive && System.currentTimeMillis() <= twitterBoostExpirationTime;
    }

    public boolean isDailyCheckinBoostActive() {
        return isDailyCheckinBoostActive && System.currentTimeMillis() <= dailyCheckinBoostExpirationTime;
    }

    public String getBoostInfo() {
        List<String> activeBoosts = new ArrayList<>();

        if (hasPermanentBoost) {
            activeBoosts.add(String.format("🚀 Permanent +%.0f%%", (permanentBoostMultiplier - 1) * 100));
        }
        if (isTemporaryBoostActive()) {
            long timeRemaining = getTemporaryBoostTimeRemaining();
            activeBoosts.add(String.format("⚡ Temporary +%.0f%% (%dm left)",
                    (TEMPORARY_BOOST_MULTIPLIER - 1) * 100, timeRemaining / 60000));
        }
        if (isTwitterBoostActive()) {
            long timeRemaining = twitterBoostExpirationTime - System.currentTimeMillis();
            activeBoosts.add(String.format("📱 Twitter +%.0f%% (%dh left)",
                    (TWITTER_BOOST_MULTIPLIER - 1) * 100, timeRemaining / (60 * 60 * 1000)));
        }
        if (isDailyCheckinBoostActive()) {
            long timeRemaining = dailyCheckinBoostExpirationTime - System.currentTimeMillis();
            activeBoosts.add(String.format("💎 Daily +%.0f%% (%dh left)",
                    (DAILY_CHECKIN_MULTIPLIER - 1) * 100, timeRemaining / (60 * 60 * 1000)));
        }
        if (isAdWatched) {
            activeBoosts.add("📺 Ad Boost +100%");
        }

        if (activeBoosts.isEmpty()) {
            return String.format("No boosts active • %.0f LYX/day", getCurrentMiningRatePerHour() * 24);
        } else if (activeBoosts.size() == 1) {
            return activeBoosts.get(0) + String.format(" • %.0f LYX/day", getCurrentMiningRatePerHour() * 24);
        } else {
            return String.format("%.1fx Total Boost • %.0f LYX/day", getTotalMultiplier(), getCurrentMiningRatePerHour() * 24);
        }
    }

    public String getBoostIndicators() {
        StringBuilder indicators = new StringBuilder();
        if (hasPermanentBoost) indicators.append("🚀");
        if (isTemporaryBoostActive()) indicators.append("⚡");
        if (isTwitterBoostActive()) indicators.append("📱");
        if (isDailyCheckinBoostActive()) indicators.append("💎");
        if (isAdWatched) indicators.append("📺");
        return indicators.toString();
    }

    /**
     * Get detailed rate breakdown for debugging - THIS WAS THE MISSING METHOD
     */
    public String getRateBreakdown() {
        StringBuilder breakdown = new StringBuilder();
        breakdown.append("=== BOOST RATE BREAKDOWN ===\n");
        breakdown.append("Base rate: ").append(String.format("%.6f", BASE_RATE_PER_SECOND)).append("/sec (").append(String.format("%.4f", BASE_RATE_PER_SECOND * 3600)).append("/hour)\n");

        if (isAdWatched) {
            breakdown.append("Ad boost: x").append(AD_BOOST_MULTIPLIER).append(" (+").append((AD_BOOST_MULTIPLIER - 1) * 100).append("%)\n");
        }

        if (hasPermanentBoost) {
            breakdown.append("Permanent boost: x").append(permanentBoostMultiplier).append(" (+").append((permanentBoostMultiplier - 1) * 100).append("%)\n");
        }

        if (isTemporaryBoostActive && System.currentTimeMillis() <= temporaryBoostExpirationTime) {
            long timeRemaining = temporaryBoostExpirationTime - System.currentTimeMillis();
            breakdown.append("Temporary boost: x").append(TEMPORARY_BOOST_MULTIPLIER).append(" (+").append((TEMPORARY_BOOST_MULTIPLIER - 1) * 100).append("%) - ").append(timeRemaining / 60000).append("m left\n");
        }

        if (isTwitterBoostActive && System.currentTimeMillis() <= twitterBoostExpirationTime) {
            long timeRemaining = twitterBoostExpirationTime - System.currentTimeMillis();
            breakdown.append("Twitter boost: x").append(TWITTER_BOOST_MULTIPLIER).append(" (+").append((TWITTER_BOOST_MULTIPLIER - 1) * 100).append("%) - ").append(timeRemaining / (60 * 60 * 1000)).append("h left\n");
        }

        if (isDailyCheckinBoostActive && System.currentTimeMillis() <= dailyCheckinBoostExpirationTime) {
            long timeRemaining = dailyCheckinBoostExpirationTime - System.currentTimeMillis();
            breakdown.append("Daily checkin boost: x").append(DAILY_CHECKIN_MULTIPLIER).append(" (+").append((DAILY_CHECKIN_MULTIPLIER - 1) * 100).append("%) - ").append(timeRemaining / (60 * 60 * 1000)).append("h left\n");
        }

        breakdown.append("---\n");
        breakdown.append("Total multiplier: x").append(String.format("%.2f", getTotalMultiplier())).append("\n");
        breakdown.append("Final rate: ").append(String.format("%.6f", getCurrentMiningRatePerSecond())).append("/sec\n");
        breakdown.append("Final rate: ").append(String.format("%.4f", getCurrentMiningRatePerHour())).append("/hour\n");
        breakdown.append("Daily estimate: ").append(String.format("%.2f", getCurrentMiningRatePerHour() * 24)).append(" LYX/day\n");
        breakdown.append("===========================");

        return breakdown.toString();
    }

    /**
     * Get estimated daily mining amount (24 hours)
     */
    public float getDailyMiningEstimate() {
        return getCurrentMiningRatePerHour() * 24f;
    }

    // LISTENER MANAGEMENT
    public void addBoostChangeListener(BoostChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeBoostChangeListener(BoostChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyBoostStateChanged() {
        float currentRate = getCurrentMiningRatePerHour();
        String boostInfo = getBoostInfo();
        for (BoostChangeListener listener : listeners) {
            try {
                listener.onBoostStateChanged(currentRate, boostInfo);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying boost change listener", e);
            }
        }
    }

    private void notifyPermanentBoostChanged() {
        for (BoostChangeListener listener : listeners) {
            try {
                listener.onPermanentBoostChanged(hasPermanentBoost, permanentBoostMultiplier);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying permanent boost change listener", e);
            }
        }
    }

    /**
     * Cleanup method to remove all listeners
     * Call this when the component using BoostManager is destroyed
     */
    public void cleanup() {
        listeners.clear();
        Log.d(TAG, "BoostManager listeners cleared");
    }
}
