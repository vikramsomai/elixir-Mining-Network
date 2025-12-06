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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OPTIMIZED BoostManager - Reduced Firebase calls, better memory management
 * 
 * Optimizations:
 * - Single listener pattern (not multiple)
 * - Efficient caching with TTL
 * - Reduced object allocation
 * - Thread-safe listener management
 * - Lazy initialization
 * - Memory-efficient calculations
 */
public class BoostManagerOptimized {
    private static final String TAG = "BoostManagerOptimized";
    private static BoostManagerOptimized instance;
    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;

    // Base rates
    private static final float BASE_RATE_PER_SECOND = 0.00125f;
    private static final float AD_BOOST_MULTIPLIER = 2.0f;
    private static final float TEMPORARY_BOOST_MULTIPLIER = 1.5f;
    private static final float TWITTER_BOOST_MULTIPLIER = 1.2f;
    private static final float DAILY_CHECKIN_MULTIPLIER = 1.1f;

    // Boost states - use primitive types to reduce memory
    private boolean isAdWatched = false;
    private boolean isTemporaryBoostActive = false;
    private long temporaryBoostExpirationTime = 0;
    private float permanentBoostMultiplier = 1.0f;
    private boolean hasPermanentBoost = false;
    private boolean isTwitterBoostActive = false;
    private long twitterBoostExpirationTime = 0;
    private boolean isDailyCheckinBoostActive = false;
    private long dailyCheckinBoostExpirationTime = 0;

    // Cache for calculated values
    private float cachedMiningRatePerSecond = -1f;
    private long lastRateCalculationTime = 0;
    private static final long RATE_CACHE_DURATION = 1000; // 1 second

    // Thread-safe listener management
    private final List<BoostChangeListener> listeners = new CopyOnWriteArrayList<>();
    private ValueEventListener permanentBoostListener;
    private ValueEventListener activeBoostListener;
    private boolean listenersAttached = false;

    public interface BoostChangeListener {
        void onBoostStateChanged(float currentMiningRate, String boostInfo);
        void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier);
    }

    private BoostManagerOptimized(Context context) {
        this.context = context.getApplicationContext();
        initializeBoostManager();
    }

    public static synchronized BoostManagerOptimized getInstance(Context context) {
        if (instance == null) {
            instance = new BoostManagerOptimized(context);
        }
        return instance;
    }

    public static BoostManagerOptimized getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BoostManager not initialized");
        }
        return instance;
    }

    private void initializeBoostManager() {
        try {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            prefs = context.getSharedPreferences("BoostManager_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            loadCachedBoostStates();
            // Defer Firebase listener attachment to avoid blocking
            attachFirebaseListeners();
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
        isTwitterBoostActive = prefs.getBoolean("isTwitterBoostActive", false);
        twitterBoostExpirationTime = prefs.getLong("twitterBoostExpirationTime", 0);
        isDailyCheckinBoostActive = prefs.getBoolean("isDailyCheckinBoostActive", false);
        dailyCheckinBoostExpirationTime = prefs.getLong("dailyCheckinBoostExpirationTime", 0);

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

    /**
     * OPTIMIZATION: Attach listeners only once and reuse them
     */
    private void attachFirebaseListeners() {
        if (listenersAttached) return;

        // Single listener for permanent boosts
        permanentBoostListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updatePermanentBoostState(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading permanent boosts", error.toException());
            }
        };

        // Single listener for active boosts
        activeBoostListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                updateActiveBoostState(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading active boosts", error.toException());
            }
        };

        userRef.child("permanentBoosts").addValueEventListener(permanentBoostListener);
        userRef.child("activeBoosts").addValueEventListener(activeBoostListener);
        listenersAttached = true;
    }

    private void updatePermanentBoostState(DataSnapshot snapshot) {
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

        // Cache update
        prefs.edit()
                .putFloat("permanentBoostMultiplier", permanentBoostMultiplier)
                .putBoolean("hasPermanentBoost", hasPermanentBoost)
                .apply();

        // Invalidate rate cache
        cachedMiningRatePerSecond = -1f;

        if (oldHasPermanentBoost != hasPermanentBoost || oldPermanentMultiplier != permanentBoostMultiplier) {
            notifyPermanentBoostChanged();
        }
        notifyBoostStateChanged();
    }

    private void updateActiveBoostState(DataSnapshot snapshot) {
        long currentTime = System.currentTimeMillis();
        boolean stateChanged = false;

        // Check temporary boost
        if (snapshot.child("temporaryBoost").exists()) {
            Long expirationTime = snapshot.child("temporaryBoost").child("expirationTime").getValue(Long.class);
            if (expirationTime != null && expirationTime > currentTime) {
                if (!isTemporaryBoostActive) {
                    activateTemporaryBoost(expirationTime);
                    stateChanged = true;
                }
            } else if (isTemporaryBoostActive) {
                deactivateTemporaryBoost();
                stateChanged = true;
            }
        }

        // Check Twitter boost
        if (snapshot.child("twitterFollow").exists()) {
            Long expirationTime = snapshot.child("twitterFollow").child("expirationTime").getValue(Long.class);
            if (expirationTime != null && expirationTime > currentTime) {
                if (!isTwitterBoostActive) {
                    activateTwitterBoost(expirationTime);
                    stateChanged = true;
                }
            } else if (isTwitterBoostActive) {
                deactivateTwitterBoost();
                stateChanged = true;
            }
        }

        // Check daily checkin boost
        if (snapshot.child("dailyCheckin").exists()) {
            Long expirationTime = snapshot.child("dailyCheckin").child("expirationTime").getValue(Long.class);
            if (expirationTime != null && expirationTime > currentTime) {
                if (!isDailyCheckinBoostActive) {
                    activateDailyCheckinBoost(expirationTime);
                    stateChanged = true;
                }
            } else if (isDailyCheckinBoostActive) {
                deactivateDailyCheckinBoost();
                stateChanged = true;
            }
        }

        if (stateChanged) {
            cachedMiningRatePerSecond = -1f; // Invalidate cache
            notifyBoostStateChanged();
        }
    }

    /**
     * OPTIMIZATION: Cache rate calculation for 1 second
     */
    public float getCurrentMiningRatePerSecond() {
        long currentTime = System.currentTimeMillis();
        
        // Return cached value if still valid
        if (cachedMiningRatePerSecond > 0 && (currentTime - lastRateCalculationTime) < RATE_CACHE_DURATION) {
            return cachedMiningRatePerSecond;
        }

        float rate = BASE_RATE_PER_SECOND;

        if (isAdWatched) {
            rate *= AD_BOOST_MULTIPLIER;
        }

        if (hasPermanentBoost) {
            rate *= permanentBoostMultiplier;
        }

        if (isTemporaryBoostActive && currentTime <= temporaryBoostExpirationTime) {
            rate *= TEMPORARY_BOOST_MULTIPLIER;
        }

        if (isTwitterBoostActive && currentTime <= twitterBoostExpirationTime) {
            rate *= TWITTER_BOOST_MULTIPLIER;
        }

        if (isDailyCheckinBoostActive && currentTime <= dailyCheckinBoostExpirationTime) {
            rate *= DAILY_CHECKIN_MULTIPLIER;
        }

        // Cache the result
        cachedMiningRatePerSecond = rate;
        lastRateCalculationTime = currentTime;

        return rate;
    }

    public float getCurrentMiningRatePerHour() {
        return getCurrentMiningRatePerSecond() * 3600f;
    }

    public float calculateMiningAmount(long durationMillis) {
        return getCurrentMiningRatePerSecond() * (durationMillis / 1000f);
    }

    /**
     * OPTIMIZATION: Avoid creating new objects for multiplier calculation
     */
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

    public void setAdWatched(boolean watched) {
        if (this.isAdWatched != watched) {
            this.isAdWatched = watched;
            prefs.edit().putBoolean("isAdWatched", watched).apply();
            cachedMiningRatePerSecond = -1f; // Invalidate cache
            notifyBoostStateChanged();
        }
    }

    public void activateTemporaryBoost(long expirationTime) {
        this.isTemporaryBoostActive = true;
        this.temporaryBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isTemporaryBoostActive", true)
                .putLong("temporaryBoostExpirationTime", expirationTime)
                .apply();
        cachedMiningRatePerSecond = -1f;
        notifyBoostStateChanged();
    }

    public void activateTwitterBoost(long expirationTime) {
        this.isTwitterBoostActive = true;
        this.twitterBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isTwitterBoostActive", true)
                .putLong("twitterBoostExpirationTime", expirationTime)
                .apply();
        cachedMiningRatePerSecond = -1f;
        notifyBoostStateChanged();
    }

    public void activateDailyCheckinBoost(long expirationTime) {
        this.isDailyCheckinBoostActive = true;
        this.dailyCheckinBoostExpirationTime = expirationTime;
        prefs.edit()
                .putBoolean("isDailyCheckinBoostActive", true)
                .putLong("dailyCheckinBoostExpirationTime", expirationTime)
                .apply();
        cachedMiningRatePerSecond = -1f;
        notifyBoostStateChanged();
    }

    public void deactivateTemporaryBoost() {
        if (this.isTemporaryBoostActive) {
            this.isTemporaryBoostActive = false;
            this.temporaryBoostExpirationTime = 0;
            prefs.edit()
                    .putBoolean("isTemporaryBoostActive", false)
                    .putLong("temporaryBoostExpirationTime", 0)
                    .apply();
            cachedMiningRatePerSecond = -1f;
            notifyBoostStateChanged();
        }
    }

    public void deactivateTwitterBoost() {
        if (this.isTwitterBoostActive) {
            this.isTwitterBoostActive = false;
            this.twitterBoostExpirationTime = 0;
            prefs.edit()
                    .putBoolean("isTwitterBoostActive", false)
                    .putLong("twitterBoostExpirationTime", 0)
                    .apply();
            cachedMiningRatePerSecond = -1f;
            notifyBoostStateChanged();
        }
    }

    public void deactivateDailyCheckinBoost() {
        if (this.isDailyCheckinBoostActive) {
            this.isDailyCheckinBoostActive = false;
            this.dailyCheckinBoostExpirationTime = 0;
            prefs.edit()
                    .putBoolean("isDailyCheckinBoostActive", false)
                    .putLong("dailyCheckinBoostExpirationTime", 0)
                    .apply();
            cachedMiningRatePerSecond = -1f;
            notifyBoostStateChanged();
        }
    }

    // Getters
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
    public boolean isTwitterBoostActive() {
        return isTwitterBoostActive && System.currentTimeMillis() <= twitterBoostExpirationTime;
    }
    public boolean isDailyCheckinBoostActive() {
        return isDailyCheckinBoostActive && System.currentTimeMillis() <= dailyCheckinBoostExpirationTime;
    }

    public String getBoostInfo() {
        StringBuilder info = new StringBuilder();
        int activeCount = 0;

        if (hasPermanentBoost) {
            info.append("🚀");
            activeCount++;
        }
        if (isTemporaryBoostActive()) {
            info.append("⚡");
            activeCount++;
        }
        if (isTwitterBoostActive()) {
            info.append("📱");
            activeCount++;
        }
        if (isDailyCheckinBoostActive()) {
            info.append("💎");
            activeCount++;
        }
        if (isAdWatched) {
            info.append("📺");
            activeCount++;
        }

        if (activeCount == 0) {
            return String.format("No boosts • %.0f LYX/day", getCurrentMiningRatePerHour() * 24);
        }

        return String.format("%s %.1fx • %.0f LYX/day", info.toString(), getTotalMultiplier(), getCurrentMiningRatePerHour() * 24);
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

    public String getRateBreakdown() {
        return String.format(
            "Base: %.6f/s | Multiplier: %.2fx | Rate: %.6f/s (%.4f/h) | Daily: %.2f LYX",
            BASE_RATE_PER_SECOND,
            getTotalMultiplier(),
            getCurrentMiningRatePerSecond(),
            getCurrentMiningRatePerHour(),
            getCurrentMiningRatePerHour() * 24
        );
    }

    public float getDailyMiningEstimate() {
        return getCurrentMiningRatePerHour() * 24f;
    }

    // Listener management
    public void addBoostChangeListener(BoostChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
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
     * Cleanup resources
     */
    public void cleanup() {
        if (permanentBoostListener != null && userRef != null) {
            userRef.child("permanentBoosts").removeEventListener(permanentBoostListener);
        }
        if (activeBoostListener != null && userRef != null) {
            userRef.child("activeBoosts").removeEventListener(activeBoostListener);
        }
        listeners.clear();
        listenersAttached = false;
    }
}
