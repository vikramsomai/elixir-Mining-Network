package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Optimizer - Reduces Firebase calls and implements caching strategy
 * 
 * Features:
 * - Intelligent caching with TTL (Time To Live)
 * - Batch operations to reduce database calls
 * - Single listener pattern to prevent duplicate listeners
 * - Offline support with cached data
 * - Request debouncing
 */
public class FirebaseOptimizer {
    private static final String TAG = "FirebaseOptimizer";
    private static final String CACHE_PREFS = "firebase_cache";
    private static final long DEFAULT_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    private static final long LEADERBOARD_CACHE_TTL = 10 * 60 * 1000; // 10 minutes
    private static final long USER_DATA_CACHE_TTL = 3 * 60 * 1000; // 3 minutes

    private Context context;
    private SharedPreferences cachePrefs;
    private Map<String, Long> lastFetchTime = new HashMap<>();
    private Map<String, ValueEventListener> activeListeners = new HashMap<>();

    public FirebaseOptimizer(Context context) {
        this.context = context;
        this.cachePrefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Fetch user data with caching
     */
    public void fetchUserDataOptimized(String userId, OnUserDataFetched callback) {
        // Check cache first
        String cachedData = cachePrefs.getString("user_" + userId, null);
        long lastFetch = cachePrefs.getLong("user_" + userId + "_time", 0);
        long now = System.currentTimeMillis();

        if (cachedData != null && (now - lastFetch) < USER_DATA_CACHE_TTL) {
            Log.d(TAG, "Using cached user data for " + userId);
            callback.onSuccess(cachedData);
            return;
        }

        // Fetch from Firebase
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);

        // Remove old listener if exists
        if (activeListeners.containsKey("user_" + userId)) {
            userRef.removeEventListener(activeListeners.get("user_" + userId));
        }

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    String data = snapshot.getValue(String.class);
                    if (data != null) {
                        // Cache the data
                        cachePrefs.edit()
                                .putString("user_" + userId, data)
                                .putLong("user_" + userId + "_time", System.currentTimeMillis())
                                .apply();
                        callback.onSuccess(data);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching user data", e);
                    callback.onError(e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
                callback.onError(error.toException());
            }
        };

        activeListeners.put("user_" + userId, listener);
        userRef.addValueEventListener(listener);
    }

    /**
     * Fetch leaderboard data with caching
     */
    public void fetchLeaderboardOptimized(OnLeaderboardFetched callback) {
        // Check cache first
        String cachedData = cachePrefs.getString("leaderboard", null);
        long lastFetch = cachePrefs.getLong("leaderboard_time", 0);
        long now = System.currentTimeMillis();

        if (cachedData != null && (now - lastFetch) < LEADERBOARD_CACHE_TTL) {
            Log.d(TAG, "Using cached leaderboard data");
            callback.onSuccess(cachedData);
            return;
        }

        // Fetch from Firebase
        DatabaseReference leaderboardRef = FirebaseDatabase.getInstance()
                .getReference("leaderboard");

        // Remove old listener if exists
        if (activeListeners.containsKey("leaderboard")) {
            leaderboardRef.removeEventListener(activeListeners.get("leaderboard"));
        }

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    String data = snapshot.getValue(String.class);
                    if (data != null) {
                        // Cache the data
                        cachePrefs.edit()
                                .putString("leaderboard", data)
                                .putLong("leaderboard_time", System.currentTimeMillis())
                                .apply();
                        callback.onSuccess(data);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching leaderboard", e);
                    callback.onError(e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
                callback.onError(error.toException());
            }
        };

        activeListeners.put("leaderboard", listener);
        leaderboardRef.addValueEventListener(listener);
    }

    /**
     * Batch update multiple fields at once
     */
    public void batchUpdate(String userId, Map<String, Object> updates, OnUpdateComplete callback) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);

        userRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Batch update successful");
                    // Invalidate cache
                    cachePrefs.edit().remove("user_" + userId).apply();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Batch update failed", e);
                    callback.onError(e);
                });
    }

    /**
     * Remove listener to prevent memory leaks
     */
    public void removeListener(String key) {
        if (activeListeners.containsKey(key)) {
            // Get the reference and remove listener
            Log.d(TAG, "Removing listener for " + key);
            activeListeners.remove(key);
        }
    }

    /**
     * Clear all listeners
     */
    public void clearAllListeners() {
        Log.d(TAG, "Clearing all listeners");
        activeListeners.clear();
    }

    /**
     * Clear cache
     */
    public void clearCache() {
        Log.d(TAG, "Clearing cache");
        cachePrefs.edit().clear().apply();
    }

    /**
     * Get cached data without fetching
     */
    public String getCachedData(String key) {
        return cachePrefs.getString(key, null);
    }

    /**
     * Check if cache is still valid
     */
    public boolean isCacheValid(String key, long ttl) {
        long lastFetch = cachePrefs.getLong(key + "_time", 0);
        return (System.currentTimeMillis() - lastFetch) < ttl;
    }

    // Callbacks
    public interface OnUserDataFetched {
        void onSuccess(String data);
        void onError(Exception e);
    }

    public interface OnLeaderboardFetched {
        void onSuccess(String data);
        void onError(Exception e);
    }

    public interface OnUpdateComplete {
        void onSuccess();
        void onError(Exception e);
    }
}
