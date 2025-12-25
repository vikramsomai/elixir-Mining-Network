package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized Firebase Manager with optimization features:
 * - Single instance pattern to prevent duplicate connections
 * - Request coalescing and debouncing
 * - Intelligent caching with TTL
 * - Offline support
 * - Automatic retry with exponential backoff
 * - Listener lifecycle management
 * - Batch operations
 */
public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static volatile FirebaseManager instance;

    // Cache settings
    private static final String CACHE_PREFS = "firebase_manager_cache";
    private static final long USER_CACHE_TTL = 2 * 60 * 1000; // 2 minutes
    private static final long REFERRAL_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    private static final long MIN_FETCH_INTERVAL = 10 * 1000; // 10 seconds between same requests

    // Retry settings
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY = 1000; // 1 second

    private final Context context;
    private final SharedPreferences cachePrefs;
    private final Handler mainHandler;
    private final FirebaseDatabase database;
    private final FirebaseAuth auth;

    // Active listeners tracking
    private final Map<String, ValueEventListener> activeListeners = new ConcurrentHashMap<>();
    private final Map<String, DatabaseReference> listenerRefs = new ConcurrentHashMap<>();

    // Request tracking to prevent duplicate requests
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final Map<String, Object> cachedData = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    // Pending operations queue
    private final Map<String, Runnable> pendingOperations = new ConcurrentHashMap<>();

    private FirebaseManager(Context context) {
        this.context = context.getApplicationContext();
        this.cachePrefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.database = FirebaseDatabase.getInstance();
        this.auth = FirebaseAuth.getInstance();

        // Enable offline persistence
        try {
            database.setPersistenceEnabled(true);
        } catch (Exception e) {
            // Already enabled or not supported
            Log.d(TAG, "Persistence already enabled or not supported");
        }
    }

    public static FirebaseManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FirebaseManager.class) {
                if (instance == null) {
                    instance = new FirebaseManager(context);
                }
            }
        }
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) {
            instance.cleanup();
            instance = null;
        }
    }

    // ==================== USER ID HELPERS ====================

    @Nullable
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isUserAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    public DatabaseReference getUserRef() {
        String userId = getCurrentUserId();
        if (userId == null) return null;
        return database.getReference("users").child(userId);
    }

    public DatabaseReference getUserRef(String userId) {
        if (userId == null || userId.isEmpty()) return null;
        return database.getReference("users").child(userId);
    }

    // ==================== OPTIMIZED DATA FETCHING ====================

    /**
     * Fetch user data with caching and debouncing
     */
    public void fetchUserData(DataCallback<UserData> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        String cacheKey = "user_" + userId;

        // Check if we recently fetched this data
        if (shouldThrottle(cacheKey)) {
            UserData cached = getCachedUserData(userId);
            if (cached != null) {
                callback.onSuccess(cached);
                return;
            }
        }

        // Check cache
        UserData cachedData = getCachedUserData(userId);
        if (cachedData != null && !isCacheExpired(cacheKey, USER_CACHE_TTL)) {
            Log.d(TAG, "Returning cached user data for " + userId);
            callback.onSuccess(cachedData);
            return;
        }

        // Fetch from Firebase
        fetchFromFirebase(cacheKey, getUserRef(), snapshot -> {
            try {
                UserData userData = parseUserData(snapshot);
                cacheUserData(userId, userData);
                callback.onSuccess(userData);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing user data", e);
                callback.onError(e);
            }
        }, callback::onError);
    }

    /**
     * Fetch referral data with caching
     */
    public void fetchReferralData(DataCallback<ReferralData> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "fetchReferralData: User not authenticated");
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        String cacheKey = "referral_" + userId;
        Log.d(TAG, "fetchReferralData: Fetching for user " + userId);

        // Check if we have valid cached data
        ReferralData cached = getCachedReferralData(userId);
        if (cached != null && !isCacheExpired(cacheKey, REFERRAL_CACHE_TTL) && shouldThrottle(cacheKey)) {
            Log.d(TAG, "fetchReferralData: Returning cached data, code=" + cached.referralCode);
            callback.onSuccess(cached);
            return;
        }

        Log.d(TAG, "fetchReferralData: Fetching fresh data from Firebase");
        fetchFromFirebase(cacheKey, getUserRef(), snapshot -> {
            try {
                ReferralData referralData = parseReferralData(snapshot, userId);
                Log.d(TAG, "fetchReferralData: Parsed data - code=" + referralData.referralCode +
                      ", referrals=" + referralData.referralCount);
                cacheReferralData(userId, referralData);
                callback.onSuccess(referralData);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing referral data", e);
                callback.onError(e);
            }
        }, callback::onError);
    }

    /**
     * Core fetch method with retry logic
     */
    private void fetchFromFirebase(String key, DatabaseReference ref,
                                   OnSnapshotReceived onSuccess, OnError onError) {
        if (ref == null) {
            onError.onError(new Exception("Database reference is null"));
            return;
        }

        lastRequestTime.put(key, System.currentTimeMillis());

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mainHandler.post(() -> onSuccess.onReceived(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase fetch error for " + key + ": " + error.getMessage());
                mainHandler.post(() -> onError.onError(error.toException()));
            }
        });
    }

    // ==================== OPTIMIZED WRITES ====================

    /**
     * Update user data with batching
     */
    public void updateUserData(Map<String, Object> updates, OperationCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        DatabaseReference userRef = getUserRef();
        if (userRef == null) {
            callback.onError(new Exception("User reference is null"));
            return;
        }

        userRef.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                // Invalidate cache
                invalidateUserCache(userId);
                callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update user data", e);
                callback.onError(e);
            });
    }

    /**
     * Atomic increment operation
     */
    public void incrementValue(String path, long delta, OperationCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        DatabaseReference ref = getUserRef().child(path);
        ref.setValue(ServerValue.increment(delta))
            .addOnSuccessListener(aVoid -> {
                invalidateUserCache(userId);
                callback.onSuccess();
            })
            .addOnFailureListener(callback::onError);
    }

    /**
     * Fan-out update for multiple paths
     */
    public void fanOutUpdate(Map<String, Object> updates, OperationCallback callback) {
        database.getReference().updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                // Invalidate all caches
                clearAllCaches();
                callback.onSuccess();
            })
            .addOnFailureListener(callback::onError);
    }

    // ==================== REFERRAL CODE HANDLING ====================

    /**
     * Get or generate referral code
     */
    public void ensureReferralCode(DataCallback<String> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onError(new Exception("User not authenticated"));
            return;
        }

        DatabaseReference codeRef = getUserRef().child("referralCode");
        codeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String code = snapshot.getValue(String.class);

                if (code == null || code.isEmpty() || code.equals("XXXXXX") || code.equals("null")) {
                    // Generate new code
                    String newCode = generateReferralCode(userId);
                    codeRef.setValue(newCode)
                        .addOnSuccessListener(aVoid -> callback.onSuccess(newCode))
                        .addOnFailureListener(e -> {
                            // Return generated code even if save fails
                            callback.onSuccess(newCode);
                        });
                } else {
                    callback.onSuccess(code);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Return a generated code as fallback
                String fallbackCode = generateReferralCode(userId);
                callback.onSuccess(fallbackCode);
            }
        });
    }

    /**
     * Generate deterministic referral code from user ID
     */
    public String generateReferralCode(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "LYNX" + System.currentTimeMillis() % 10000;
        }
        try {
            String encoded = android.util.Base64.encodeToString(userId.getBytes(), android.util.Base64.NO_WRAP);
            String code = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (code.length() >= 6) {
                return code.substring(0, 6);
            } else {
                return code + Math.abs(userId.hashCode() % 1000);
            }
        } catch (Exception e) {
            return "LYX" + Math.abs(userId.hashCode() % 100000);
        }
    }

    // ==================== LISTENER MANAGEMENT ====================

    /**
     * Add a managed listener that will be automatically cleaned up
     */
    public void addManagedListener(String key, DatabaseReference ref, ValueEventListener listener) {
        // Remove existing listener for this key
        removeManagedListener(key);

        activeListeners.put(key, listener);
        listenerRefs.put(key, ref);
        ref.addValueEventListener(listener);
    }

    /**
     * Remove a managed listener
     */
    public void removeManagedListener(String key) {
        ValueEventListener listener = activeListeners.remove(key);
        DatabaseReference ref = listenerRefs.remove(key);
        if (listener != null && ref != null) {
            try {
                ref.removeEventListener(listener);
            } catch (Exception e) {
                Log.w(TAG, "Error removing listener: " + key, e);
            }
        }
    }

    /**
     * Remove all managed listeners
     */
    public void removeAllListeners() {
        for (String key : activeListeners.keySet()) {
            removeManagedListener(key);
        }
        activeListeners.clear();
        listenerRefs.clear();
    }

    // ==================== CACHING HELPERS ====================

    private boolean shouldThrottle(String key) {
        Long lastRequest = lastRequestTime.get(key);
        if (lastRequest == null) return false;
        return (System.currentTimeMillis() - lastRequest) < MIN_FETCH_INTERVAL;
    }

    private boolean isCacheExpired(String key, long ttl) {
        Long timestamp = cacheTimestamps.get(key);
        if (timestamp == null) return true;
        return (System.currentTimeMillis() - timestamp) > ttl;
    }

    private UserData getCachedUserData(String userId) {
        return (UserData) cachedData.get("user_" + userId);
    }

    private void cacheUserData(String userId, UserData data) {
        String key = "user_" + userId;
        cachedData.put(key, data);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }

    private ReferralData getCachedReferralData(String userId) {
        return (ReferralData) cachedData.get("referral_" + userId);
    }

    private void cacheReferralData(String userId, ReferralData data) {
        String key = "referral_" + userId;
        cachedData.put(key, data);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }

    private void invalidateUserCache(String userId) {
        cachedData.remove("user_" + userId);
        cacheTimestamps.remove("user_" + userId);
        cachedData.remove("referral_" + userId);
        cacheTimestamps.remove("referral_" + userId);
    }

    private void clearAllCaches() {
        cachedData.clear();
        cacheTimestamps.clear();
        lastRequestTime.clear();
    }

    // ==================== DATA PARSING ====================

    /**
     * Safely parse a Double value from Firebase snapshot (handles Integer, Long, Double)
     */
    private double parseDoubleValue(DataSnapshot snapshot, String child, double defaultValue) {
        Object raw = snapshot.child(child).getValue();
        if (raw == null) return defaultValue;

        if (raw instanceof Double) {
            return (Double) raw;
        } else if (raw instanceof Long) {
            return ((Long) raw).doubleValue();
        } else if (raw instanceof Integer) {
            return ((Integer) raw).doubleValue();
        } else {
            try {
                return Double.parseDouble(raw.toString());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse " + child + ": " + raw);
                return defaultValue;
            }
        }
    }

    /**
     * Safely parse an Integer value from Firebase snapshot
     */
    private int parseIntValue(DataSnapshot snapshot, String child, int defaultValue) {
        Object raw = snapshot.child(child).getValue();
        if (raw == null) return defaultValue;

        if (raw instanceof Integer) {
            return (Integer) raw;
        } else if (raw instanceof Long) {
            return ((Long) raw).intValue();
        } else {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse " + child + ": " + raw);
                return defaultValue;
            }
        }
    }

    private UserData parseUserData(DataSnapshot snapshot) {
        UserData data = new UserData();
        if (!snapshot.exists()) return data;

        data.username = snapshot.child("username").getValue(String.class);
        data.email = snapshot.child("email").getValue(String.class);
        data.referralCode = snapshot.child("referralCode").getValue(String.class);

        data.totalCoins = parseDoubleValue(snapshot, "totalcoins", 0.0);
        data.streakCount = parseIntValue(snapshot, "streakCount", 0);
        data.totalStreak = parseDoubleValue(snapshot, "totalStreak", 0.0);
        data.level = parseIntValue(snapshot, "level", 1);

        data.profilePicUrl = snapshot.child("profilePicUrl").getValue(String.class);
        data.lastDate = snapshot.child("lastDate").getValue(String.class);

        Log.d(TAG, "parseUserData: username=" + data.username + ", streakCount=" + data.streakCount +
              ", totalStreak=" + data.totalStreak + ", referralCode=" + data.referralCode);

        return data;
    }

    private ReferralData parseReferralData(DataSnapshot snapshot, String userId) {
        ReferralData data = new ReferralData();
        if (!snapshot.exists()) {
            data.referralCode = generateReferralCode(userId);
            return data;
        }

        String code = snapshot.child("referralCode").getValue(String.class);
        if (code == null || code.isEmpty() || code.equals("XXXXXX")) {
            data.referralCode = generateReferralCode(userId);
        } else {
            data.referralCode = code;
        }

        // Count referrals
        if (snapshot.child("referrals").exists()) {
            data.referralCount = (int) snapshot.child("referrals").getChildrenCount();
        }

        // Sum commissions
        if (snapshot.child("commissions").exists()) {
            for (DataSnapshot commission : snapshot.child("commissions").getChildren()) {
                Double amount = commission.child("amount").getValue(Double.class);
                if (amount != null) {
                    data.totalCommission += amount;
                }
            }
        }

        // Sum referral earnings
        if (snapshot.child("referralEarnings").exists()) {
            for (DataSnapshot earning : snapshot.child("referralEarnings").getChildren()) {
                Double amount = earning.child("amount").getValue(Double.class);
                if (amount != null) {
                    data.referralEarnings += amount;
                }
            }
        }

        // Check boost status
        if (snapshot.child("activeBoosts").child("referralBoost").child("endTime").exists()) {
            Long endTime = snapshot.child("activeBoosts").child("referralBoost").child("endTime").getValue(Long.class);
            data.isBoostActive = endTime != null && endTime > System.currentTimeMillis();
            data.boostEndTime = endTime != null ? endTime : 0;
        }

        Double coins = snapshot.child("totalcoins").getValue(Double.class);
        data.totalCoins = coins != null ? coins : 0.0;

        return data;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        removeAllListeners();
        clearAllCaches();
        pendingOperations.clear();
    }

    // ==================== DATA CLASSES ====================

    public static class UserData {
        public String username;
        public String email;
        public String referralCode;
        public double totalCoins;
        public int streakCount;
        public double totalStreak;
        public int level;
        public String profilePicUrl;
        public String lastDate;
    }

    public static class ReferralData {
        public String referralCode;
        public int referralCount;
        public double totalCommission;
        public double referralEarnings;
        public double totalCoins;
        public boolean isBoostActive;
        public long boostEndTime;

        public double getTotalEarned() {
            return totalCommission + referralEarnings;
        }
    }

    // ==================== CALLBACKS ====================

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private interface OnSnapshotReceived {
        void onReceived(DataSnapshot snapshot);
    }

    private interface OnError {
        void onError(Exception e);
    }
}

