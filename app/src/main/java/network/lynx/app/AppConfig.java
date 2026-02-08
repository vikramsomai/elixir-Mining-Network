package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Centralized App Configuration Manager
 *
 * SENIOR DEVELOPER BEST PRACTICES:
 * - Single source of truth for all app constants
 * - Easy to modify values without hunting through code
 * - Supports remote config integration
 * - Type-safe configuration access
 */
public final class AppConfig {
    private static final String TAG = "AppConfig";
    private static final String PREFS_NAME = "app_config";

    // ============================================
    // APP INFO
    // ============================================
    public static final String APP_NAME = "Lynx Network";
    public static final String APP_VERSION = "4.0";
    public static final int APP_VERSION_CODE = 22;

    // ============================================
    // MINING CONFIGURATION
    // ============================================
    public static final long MINING_DURATION_MS = 24 * 60 * 60 * 1000; // 24 hours
    public static final float BASE_MINING_RATE_PER_SECOND = 0.00125f; // ~4.5 LYX/hour
    public static final float BASE_MINING_RATE_PER_HOUR = BASE_MINING_RATE_PER_SECOND * 3600; // 4.5 LYX/hour

    // ============================================
    // REFERRAL CONFIGURATION
    // ============================================
    public static final double REFERRAL_COMMISSION_RATE = 0.10; // 10% commission
    public static final int REFERRAL_BOOST_THRESHOLD = 3; // 3 referrals for permanent boost
    public static final float REFERRAL_PERMANENT_BOOST = 1.5f; // 50% permanent boost

    // ============================================
    // BOOST MULTIPLIERS
    // ============================================
    public static final float AD_BOOST_MULTIPLIER = 2.0f; // 2x for watching ad
    public static final float TEMPORARY_BOOST_MULTIPLIER = 1.5f; // 50% temp boost
    public static final float TWITTER_BOOST_MULTIPLIER = 1.2f; // 20% Twitter boost
    public static final float DAILY_CHECKIN_MULTIPLIER = 1.1f; // 10% daily checkin
    public static final float TELEGRAM_BOOST_MULTIPLIER = 1.15f; // 15% Telegram boost
    public static final float DISCORD_BOOST_MULTIPLIER = 1.15f; // 15% Discord boost

    // ============================================
    // AD CONFIGURATION (eCPM Optimized)
    // ============================================
    public static final long AD_COOLDOWN_MS = 15 * 60 * 1000; // 15 minutes
    public static final int MAX_DAILY_REWARDED_ADS = 8;
    public static final int NEW_USER_DAILY_AD_LIMIT = 3;
    public static final long MIN_SESSION_FOR_ADS_MS = 2 * 60 * 1000; // 2 minutes
    public static final long AD_EXPIRY_TIME_MS = 55 * 60 * 1000; // 55 minutes

    // ============================================
    // REWARDS CONFIGURATION
    // ============================================
    public static final int MAX_DAILY_SPINS = 3;
    public static final int MAX_DAILY_SCRATCH_CARDS = 5;
    public static final double[] SPIN_REWARDS = {0.5, 1.0, 2.0, 5.0, 10.0, 0.1, 0.25, 3.0};
    public static final int DAILY_CHECKIN_BASE_REWARD = 1; // Base LYX for checkin
    public static final int STREAK_BONUS_MULTIPLIER = 7; // Max streak bonus day

    // ============================================
    // CACHE & SYNC CONFIGURATION
    // ============================================
    public static final long USER_CACHE_TTL_MS = 2 * 60 * 1000; // 2 minutes
    public static final long BALANCE_FETCH_DEBOUNCE_MS = 30 * 1000; // 30 seconds
    public static final long SYNC_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    // ============================================
    // NETWORK CONFIGURATION
    // ============================================
    public static final int NETWORK_TIMEOUT_SECONDS = 30;
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_DELAY_MS = 1000; // 1 second base delay

    // ============================================
    // SOCIAL LINKS
    // ============================================
    public static final String TWITTER_URL = "https://twitter.com/lynxnetwork_";
    public static final String TELEGRAM_URL = "https://t.me/lynx_network_annoucement";
    public static final String DISCORD_URL = "https://discord.gg/veQgvUD8";
    public static final String PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=network.lynx.app";

    // ============================================
    // NOTIFICATION CONFIGURATION
    // ============================================
    public static final String NOTIFICATION_CHANNEL_MINING = "mining_channel";
    public static final String NOTIFICATION_CHANNEL_REWARDS = "rewards_channel";
    public static final String NOTIFICATION_CHANNEL_GENERAL = "general_channel";

    // ============================================
    // ANALYTICS EVENTS
    // ============================================
    public static final String EVENT_MINING_STARTED = "mining_started";
    public static final String EVENT_MINING_COMPLETED = "mining_completed";
    public static final String EVENT_AD_WATCHED = "ad_watched";
    public static final String EVENT_REFERRAL_USED = "referral_used";
    public static final String EVENT_CHECKIN_COMPLETED = "checkin_completed";

    // Singleton instance
    private static AppConfig instance;
    private SharedPreferences prefs;

    private AppConfig(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppConfig getInstance(Context context) {
        if (instance == null) {
            instance = new AppConfig(context);
        }
        return instance;
    }

    // ============================================
    // REMOTE CONFIG SUPPORT (Future Integration)
    // ============================================

    /**
     * Get mining rate - can be overridden by remote config
     */
    public float getMiningRatePerHour() {
        return prefs.getFloat("mining_rate_per_hour", BASE_MINING_RATE_PER_HOUR);
    }

    /**
     * Get ad cooldown - can be overridden by remote config
     */
    public long getAdCooldown() {
        return prefs.getLong("ad_cooldown", AD_COOLDOWN_MS);
    }

    /**
     * Get max daily ads - can be overridden by remote config
     */
    public int getMaxDailyAds() {
        return prefs.getInt("max_daily_ads", MAX_DAILY_REWARDED_ADS);
    }

    /**
     * Get referral commission rate - can be overridden by remote config
     */
    public double getReferralCommissionRate() {
        return (double) prefs.getFloat("referral_commission_rate", (float) REFERRAL_COMMISSION_RATE);
    }

    /**
     * Update config from remote (call when Firebase Remote Config fetches)
     */
    public void updateFromRemote(float miningRate, long adCooldown, int maxDailyAds, float commissionRate) {
        prefs.edit()
                .putFloat("mining_rate_per_hour", miningRate)
                .putLong("ad_cooldown", adCooldown)
                .putInt("max_daily_ads", maxDailyAds)
                .putFloat("referral_commission_rate", commissionRate)
                .apply();
        Log.d(TAG, "Config updated from remote");
    }

    /**
     * Reset to default values
     */
    public void resetToDefaults() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Config reset to defaults");
    }
}

