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

    // Rate limiting constants - RELAXED for better ad availability
    private static final long MIN_LOAD_INTERVAL = 10 * 1000; // 10 seconds between loads (reduced from 30)
    private static final long RETRY_BASE_DELAY = 15 * 1000; // 15 seconds base retry delay (reduced from 30)
    private static final int MAX_RETRIES = 3; // Increased retries
    private static final int MAX_DAILY_REQUESTS = 500; // Increased daily limit
    private static final long AD_EXPIRY_TIME = 55 * 60 * 1000; // 55 minutes


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
     * Start session tracking for ads
     */
    public void startSession() {
        Log.d(TAG, "Ad session started");
    }

    /**
     * Load a rewarded ad with rate limiting and smart retry logic
     */
    public void loadRewardedAd(Context context, String adUnitId, AdLoadCallback callback) {
        final Context applicationContext = context.getApplicationContext();

        // Always allow loading if no ad is ready
        if (!isAdReady(adUnitId)) {
            // Clear any blocking states
            loadingStates.put(adUnitId, false);
        }

        if (!canLoadAd(applicationContext, adUnitId)) {
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
        recordAdRequest(applicationContext);
        loadingStates.put(adUnitId, true);
        lastLoadTimes.put(adUnitId, System.currentTimeMillis());

        Log.d(TAG, "Loading ad for " + adUnitId);

        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(applicationContext, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "Ad failed to load for " + adUnitId + ": " + loadAdError.getMessage() + " (code: " + loadAdError.getCode() + ")");
                loadingStates.put(adUnitId, false);

                // Implement smart retry with exponential backoff
                int retryCount = retryCounts.getOrDefault(adUnitId, 0);
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    retryCounts.put(adUnitId, retryCount);

                    long baseDelay = RETRY_BASE_DELAY * (long) Math.pow(2, retryCount - 1);
                    long jitter = (long) (Math.random() * 5000);
                    long delay = baseDelay + jitter;
                    Log.d(TAG, "Retrying ad load for " + adUnitId + " in " + delay + "ms (attempt " + retryCount + ")");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        loadRewardedAd(applicationContext, adUnitId, callback);
                    }, delay);
                } else {
                    Log.e(TAG, "Max retries reached for " + adUnitId);
                    retryCounts.put(adUnitId, 0);
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
                retryCounts.put(adUnitId, 0);

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
        showRewardedAd(activity, adUnitId, callback, false);
    }

    /**
     * Show a rewarded ad - bypassChecks parameter for flexibility
     */
    public void showRewardedAd(Activity activity, String adUnitId, AdShowCallback callback, boolean bypassChecks) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            Log.w(TAG, "Activity is null/destroyed/finishing, cannot show ad");
            if (callback != null) {
                callback.onAdNotAvailable();
            }
            return;
        }

        RewardedAd ad = adCache.get(adUnitId);

        if (ad == null || isAdExpired(adUnitId)) {
            Log.w(TAG, "No valid ad available for " + adUnitId + ", attempting to load...");
            if (isAdExpired(adUnitId)) {
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
            }

            // Try to load the ad instead of just returning
            loadAndShowAd(activity, adUnitId, callback);
            return;
        }

        showAdInternal(activity, ad, adUnitId, callback);
    }

    /**
     * Load and then show an ad
     */
    private void loadAndShowAd(Activity activity, String adUnitId, AdShowCallback callback) {
        Log.d(TAG, "Loading ad before showing for " + adUnitId);

        // Force load by clearing rate limit for this specific request
        lastLoadTimes.remove(adUnitId);
        loadingStates.put(adUnitId, false);
        retryCounts.put(adUnitId, 0);

        loadRewardedAd(activity, adUnitId, new AdLoadCallback() {
            @Override
            public void onAdLoaded() {
                if (activity.isDestroyed() || activity.isFinishing()) {
                    Log.w(TAG, "Activity destroyed while loading ad");
                    if (callback != null) {
                        callback.onAdNotAvailable();
                    }
                    return;
                }

                RewardedAd ad = adCache.get(adUnitId);
                if (ad != null) {
                    showAdInternal(activity, ad, adUnitId, callback);
                } else {
                    Log.e(TAG, "Ad loaded but not found in cache for " + adUnitId);
                    if (callback != null) {
                        callback.onAdNotAvailable();
                    }
                }
            }

            @Override
            public void onAdLoadFailed(String error) {
                Log.e(TAG, "Failed to load ad for showing: " + error);
                if (callback != null) {
                    callback.onAdNotAvailable();
                }
            }
        });
    }

    /**
     * Internal method to actually show the ad
     */
    private void showAdInternal(Activity activity, RewardedAd ad, String adUnitId, AdShowCallback callback) {
        Log.d(TAG, "Showing ad for " + adUnitId);

        // Suppress app open ads while rewarded ad is showing
        suppressAppOpenAds(activity, true);

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
                suppressAppOpenAds(activity, false);
                if (callback != null) {
                    callback.onAdShowFailed(adError.getMessage());
                }
                // Try to preload next ad
                smartPreloadAd(activity, adUnitId);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed for " + adUnitId);
                adCache.remove(adUnitId);
                adLoadTimes.remove(adUnitId);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    suppressAppOpenAds(activity, false);
                }, 1000);
                if (callback != null) {
                    callback.onAdDismissed();
                }
                // Preload next ad immediately
                smartPreloadAd(activity, adUnitId);
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
     * Helper method to suppress/enable app open ads
     */
    private void suppressAppOpenAds(Activity activity, boolean suppress) {
        try {
            if (activity.getApplication() instanceof LynxApplication) {
                LynxApplication app = (LynxApplication) activity.getApplication();
                AppOpenAdManager.getInstance(app).setSuppressAppOpenAds(suppress);
                Log.d(TAG, "App open ads suppressed: " + suppress);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error suppressing app open ads", e);
        }
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
     * Preload ad immediately without smart checks
     */
    public void preloadAd(Context context, String adUnitId) {
        if (!isAdReady(adUnitId) && !isLoading(adUnitId)) {
            Log.d(TAG, "Preloading ad for " + adUnitId);
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
     * Intelligent preloading based on user behavior
     */
    public void smartPreloadAd(Context context, String adUnitId) {
        if (!isAdReady(adUnitId) && !isLoading(adUnitId)) {
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
     * Rate limiting logic - simplified
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

        return true;
    }

    /**
     * Record an ad request for tracking
     */
    private void recordAdRequest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (!today.equals(lastDate)) {
            prefs.edit()
                    .putString(KEY_LAST_DATE, today)
                    .putInt(KEY_DAILY_REQUESTS, 1)
                    .apply();
        } else {
            int current = prefs.getInt(KEY_DAILY_REQUESTS, 0);
            prefs.edit().putInt(KEY_DAILY_REQUESTS, current + 1).apply();
        }
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
     * Clear all cache and reset states
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
     * Force reload an ad
     */
    public void forceReloadAd(Context context, String adUnitId, AdLoadCallback callback) {
        Log.d(TAG, "Force reloading ad for " + adUnitId);
        adCache.remove(adUnitId);
        adLoadTimes.remove(adUnitId);
        loadingStates.put(adUnitId, false);
        lastLoadTimes.remove(adUnitId);
        retryCounts.put(adUnitId, 0);

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
