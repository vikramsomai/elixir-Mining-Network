package network.lynx.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.AdError;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdManager {
    private static final String TAG = "AdManager";
    private static AdManager instance;

    // Ad Units
    public static final String AD_UNIT_CHECK_IN = "ca-app-pub-1396109779371789/7595812194";
    public static final String AD_UNIT_SPIN = "ca-app-pub-1396109779371789/5731483027";
    public static final String AD_UNIT_MINING = "ca-app-pub-1396109779371789/9096627942";
    public static final String AD_UNIT_BOOST = "ca-app-pub-1396109779371789/9096627942"; // Same as mining for now

    // Rate limiting constants
    private static final long MIN_LOAD_INTERVAL = 30 * 1000; // 30 seconds between loads
    private static final long RETRY_BASE_DELAY = 30 * 1000; // 30 seconds base retry delay
    private static final int MAX_RETRIES = 2;
    private static final int MAX_DAILY_REQUESTS = 200; // Conservative daily limit
    private static final long AD_EXPIRY_TIME = 10 * 60 * 1000; // 15 minutes


    // Thread-safe collections
    private final Map<String, RewardedAd> adCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loadingStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLoadTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> adLoadTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();

    // Preferences for frequency tracking
    private static final String PREFS_NAME = "ad_manager_prefs";
    private static final String KEY_DAILY_REQUESTS = "daily_requests";
    private static final String KEY_LAST_DATE = "last_date";

    private AdManager() {
        // Private constructor for singleton
    }

    public static synchronized AdManager getInstance() {
        if (instance == null) {
            instance = new AdManager();
        }
        return instance;
    }

    /**
     * Load a rewarded ad with rate limiting and smart retry logic
     */
    public void loadRewardedAd(Context context, String adUnitId, AdLoadCallback callback) {
        if (!canLoadAd(context, adUnitId)) {
            Log.d(TAG, "Cannot load ad for " + adUnitId + " - rate limited or conditions not met");
            if (callback != null) {
                callback.onAdLoadFailed("Rate limited or already loading");
            }
            return;
        }

        // Check if we already have a valid, non-expired ad
        if (isAdReady(adUnitId) && !isAdExpired(adUnitId)) {
            Log.d(TAG, "Valid ad already available for " + adUnitId);
            if (callback != null) {
                callback.onAdLoaded();
            }
            return;
        }

        // Remove expired ad
        if (isAdExpired(adUnitId)) {
            Log.d(TAG, "Removing expired ad for " + adUnitId);
            adCache.remove(adUnitId);
            adLoadTimes.remove(adUnitId);
        }

        // Record the request
        recordAdRequest(context);
        loadingStates.put(adUnitId, true);
        lastLoadTimes.put(adUnitId, System.currentTimeMillis());

        Log.d(TAG, "Loading ad for " + adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(context, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Ad failed to load for " + adUnitId + ": " + loadAdError.getMessage());
                loadingStates.put(adUnitId, false);

                // Implement smart retry with exponential backoff
                int retryCount = retryCounts.getOrDefault(adUnitId, 0);
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    retryCounts.put(adUnitId, retryCount);

                    long baseDelay = RETRY_BASE_DELAY * (long) Math.pow(2, retryCount - 1); // Exponential backoff
                    long jitter = (long) (Math.random() * 5000); // 0 to 5 seconds
                    long delay = baseDelay + jitter;
                    Log.d(TAG, "Retrying ad load for " + adUnitId + " in " + delay + "ms (attempt " + retryCount + ")");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (context instanceof Activity && !((Activity) context).isDestroyed()) {
                            loadRewardedAd(context, adUnitId, callback);
                        }
                    }, delay);
                } else {
                    Log.e(TAG, "Max retries reached for " + adUnitId);
                    retryCounts.put(adUnitId, 0); // Reset for next session
                    if (callback != null) {
                        callback.onAdLoadFailed("Max retries reached: " + loadAdError.getMessage());
                    }
                }
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                Log.d(TAG, "Ad loaded successfully for " + adUnitId);
                adCache.put(adUnitId, rewardedAd);
                adLoadTimes.put(adUnitId, System.currentTimeMillis());
                loadingStates.put(adUnitId, false);
                retryCounts.put(adUnitId, 0); // Reset retry count on success

                if (callback != null) {
                    callback.onAdLoaded();
                }
            }
        });
    }

    /**
     * Show a rewarded ad with proper lifecycle management
     */
    public void showRewardedAd(Activity activity, String adUnitId, AdShowCallback callback) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            Log.w(TAG, "Activity is destroyed/finishing, cannot show ad");
            if (callback != null) {
                callback.onAdNotAvailable();
            }
            return;
        }

        RewardedAd ad = adCache.get(adUnitId);

        if (ad == null || isAdExpired(adUnitId)) {
            Log.w(TAG, "No valid ad available for " + adUnitId);
            if (isAdExpired(adUnitId)) {
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
            }
            if (callback != null) {
                callback.onAdNotAvailable();
            }
            return;
        }

        Log.d(TAG, "Showing ad for " + adUnitId);

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed full screen content for " + adUnitId);
                if (callback != null) {
                    callback.onAdShowed();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                Log.e(TAG, "Ad failed to show for " + adUnitId + ": " + adError.getMessage());
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
                if (callback != null) {
                    callback.onAdShowFailed(adError.getMessage());
                }
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed for " + adUnitId);
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
                if (callback != null) {
                    callback.onAdDismissed();
                }
            }
        });

        ad.show(activity, rewardItem -> {
            Log.d(TAG, "User earned reward for " + adUnitId + ": " + rewardItem.getAmount() + " " + rewardItem.getType());
            if (callback != null) {
                callback.onUserEarnedReward(rewardItem);
            }
        });
    }

    /**
     * Check if an ad is ready to be shown
     */
    public boolean isAdReady(String adUnitId) {
        return adCache.containsKey(adUnitId) && adCache.get(adUnitId) != null && !isAdExpired(adUnitId);
    }

    /**
     * Check if an ad has expired
     */
    private boolean isAdExpired(String adUnitId) {
        Long loadTime = adLoadTimes.get(adUnitId);
        if (loadTime == null) return true;
        return (System.currentTimeMillis() - loadTime) > AD_EXPIRY_TIME;
    }

    /**
     * Check if we're currently loading an ad
     */
    public boolean isLoading(String adUnitId) {
        return loadingStates.getOrDefault(adUnitId, false);
    }

    /**
     * Intelligent preloading based on user behavior
     */
    public void smartPreloadAd(Context context, String adUnitId) {
        if (shouldPreloadAd(context, adUnitId) && !isAdReady(adUnitId) && !isLoading(adUnitId)) {
            Log.d(TAG, "Smart preloading ad for " + adUnitId);
            loadRewardedAd(context, adUnitId, new AdLoadCallback() {
                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "Preloaded ad successfully for " + adUnitId);
                }

                @Override
                public void onAdLoadFailed(String error) {
                    Log.d(TAG, "Preload failed for " + adUnitId + ": " + error);
                }
            });
        }
    }

    /**
     * Rate limiting logic
     */
    private boolean canLoadAd(Context context, String adUnitId) {
        // Check if already loading
        if (isLoading(adUnitId)) {
            Log.d(TAG, "Already loading ad for " + adUnitId);
            return false;
        }

        // Check minimum interval between loads
        Long lastLoadTime = lastLoadTimes.get(adUnitId);
        if (lastLoadTime != null && (System.currentTimeMillis() - lastLoadTime) < MIN_LOAD_INTERVAL) {
            Log.d(TAG, "Too soon to load ad for " + adUnitId + " (min interval not met)");
            return false;
        }

        // Check daily request limit
        if (!canMakeDailyRequest(context)) {
            Log.w(TAG, "Daily request limit reached");
            return false;
        }

        return true;
    }

    /**
     * Daily request limiting with automatic reset
     */
    private boolean canMakeDailyRequest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            // Reset for new day
            prefs.edit()
                    .putString(KEY_LAST_DATE, today)
                    .putInt(KEY_DAILY_REQUESTS, 0)
                    .apply();
            Log.d(TAG, "Reset daily ad request counter for new day");
            return true;
        }

        int dailyRequests = prefs.getInt(KEY_DAILY_REQUESTS, 0);
        boolean canRequest = dailyRequests < MAX_DAILY_REQUESTS;

        if (!canRequest) {
            Log.w(TAG, "Daily request limit reached: " + dailyRequests + "/" + MAX_DAILY_REQUESTS);
        }

        return canRequest;
    }

    /**
     * Record an ad request for tracking
     */
    private void recordAdRequest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_DAILY_REQUESTS, 0);
        prefs.edit().putInt(KEY_DAILY_REQUESTS, current + 1).apply();
        Log.d(TAG, "Daily ad requests: " + (current + 1) + "/" + MAX_DAILY_REQUESTS);
    }

    /**
     * Smart preloading logic based on user behavior patterns
     */
    private boolean shouldPreloadAd(Context context, String adUnitId) {
        SharedPreferences prefs = context.getSharedPreferences("user_behavior", Context.MODE_PRIVATE);
        String usageKey = adUnitId + "_usage_count";
        String lastUsageKey = adUnitId + "_last_usage";

        int usageCount = prefs.getInt(usageKey, 0);
        long lastUsage = prefs.getLong(lastUsageKey, 0);
        long timeSinceLastUsage = System.currentTimeMillis() - lastUsage;

        // Preload if user frequently uses this feature (>3 times) and used it recently (within 24 hours)
        boolean frequentUser = usageCount > 3;
        boolean recentUser = timeSinceLastUsage < (24 * 60 * 60 * 1000); // 24 hours

        return frequentUser && recentUser;
    }

    /**
     * Track user behavior for smart preloading
     */
    public void recordFeatureUsage(Context context, String adUnitId) {
        SharedPreferences prefs = context.getSharedPreferences("user_behavior", Context.MODE_PRIVATE);
        String usageKey = adUnitId + "_usage_count";
        String lastUsageKey = adUnitId + "_last_usage";

        int current = prefs.getInt(usageKey, 0);
        prefs.edit()
                .putInt(usageKey, current + 1)
                .putLong(lastUsageKey, System.currentTimeMillis())
                .apply();

        Log.d(TAG, "Recorded feature usage for " + adUnitId + ": " + (current + 1) + " times");
    }

    /**
     * Get comprehensive ad statistics
     */
    public AdStats getAdStats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int dailyRequests = prefs.getInt(KEY_DAILY_REQUESTS, 0);

        int readyAds = 0;
        int loadingAds = 0;
        int expiredAds = 0;

        for (String adUnitId : new String[]{AD_UNIT_CHECK_IN, AD_UNIT_SPIN, AD_UNIT_MINING, AD_UNIT_BOOST}) {
            if (isLoading(adUnitId)) {
                loadingAds++;
            } else if (isAdReady(adUnitId)) {
                readyAds++;
            } else if (adCache.containsKey(adUnitId) && isAdExpired(adUnitId)) {
                expiredAds++;
            }
        }

        return new AdStats(dailyRequests, MAX_DAILY_REQUESTS, readyAds, loadingAds, expiredAds);
    }

    /**
     * Clean up expired ads and reset states
     */
    public void cleanupExpiredAds() {
        for (String adUnitId : new String[]{AD_UNIT_CHECK_IN, AD_UNIT_SPIN, AD_UNIT_MINING, AD_UNIT_BOOST}) {
            if (isAdExpired(adUnitId)) {
                Log.d(TAG, "Cleaning up expired ad for " + adUnitId);
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
            }
        }
    }

    /**
     * Clear all cache and reset states (call when app is destroyed)
     */
    public void clearCache() {
        Log.d(TAG, "Clearing ad cache and resetting states");
        adCache.clear();
        adLoadTimes.clear();
        loadingStates.clear();
        lastLoadTimes.clear();
        retryCounts.clear();
    }

    /**
     * Force reload an ad (use sparingly)
     */
    public void forceReloadAd(Context context, String adUnitId, AdLoadCallback callback) {
        Log.d(TAG, "Force reloading ad for " + adUnitId);
        adCache.remove(adUnitId);
        adLoadTimes.remove(adUnitId);
        loadingStates.put(adUnitId, false);
        lastLoadTimes.remove(adUnitId);

        loadRewardedAd(context, adUnitId, callback);
    }

    // Callback interfaces
    public interface AdLoadCallback {
        void onAdLoaded();
        void onAdLoadFailed(String error);
    }

    public interface AdShowCallback {
        void onAdShowed();
        void onAdShowFailed(String error);
        void onAdDismissed();
        void onAdNotAvailable();
        void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem);
    }

    // Statistics class
    public static class AdStats {
        public final int dailyRequests;
        public final int maxDailyRequests;
        public final int readyAds;
        public final int loadingAds;
        public final int expiredAds;

        public AdStats(int dailyRequests, int maxDailyRequests, int readyAds, int loadingAds, int expiredAds) {
            this.dailyRequests = dailyRequests;
            this.maxDailyRequests = maxDailyRequests;
            this.readyAds = readyAds;
            this.loadingAds = loadingAds;
            this.expiredAds = expiredAds;
        }

        @Override
        public String toString() {
            return String.format("AdStats{daily: %d/%d, ready: %d, loading: %d, expired: %d}",
                    dailyRequests, maxDailyRequests, readyAds, loadingAds, expiredAds);
        }
    }
}
