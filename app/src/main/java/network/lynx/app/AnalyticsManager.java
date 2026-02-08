package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ANALYTICS MANAGER - Track User Behavior
 *
 * Why you have low eCPM ($1) and low revenue ($2 from 900 users):
 *
 * 1. Low ad completion rate - Users skip/close ads
 * 2. Low engagement - Users don't use reward features
 * 3. Poor ad placement - Ads shown at wrong times
 * 4. Geographic factors - Users from low-CPM countries
 *
 * This manager tracks everything to improve revenue.
 */
public class AnalyticsManager {
    private static final String TAG = "AnalyticsManager";
    private static final String PREFS_NAME = "analytics_prefs";

    private static AnalyticsManager instance;
    private final SharedPreferences prefs;
    private final Context context;

    // Keys
    private static final String KEY_TOTAL_ADS_SHOWN = "total_ads_shown";
    private static final String KEY_TOTAL_ADS_COMPLETED = "total_ads_completed";
    private static final String KEY_TOTAL_ADS_SKIPPED = "total_ads_skipped";
    private static final String KEY_TOTAL_SESSIONS = "total_sessions";
    private static final String KEY_TOTAL_SESSION_TIME = "total_session_time";
    private static final String KEY_FEATURE_CLICKS = "feature_clicks_";
    private static final String KEY_LAST_SESSION_START = "last_session_start";
    private static final String KEY_DAILY_ACTIVE = "daily_active_";

    private long sessionStartTime = 0;

    private AnalyticsManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AnalyticsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AnalyticsManager(context);
        }
        return instance;
    }

    // ============================================
    // SESSION TRACKING
    // ============================================

    public void startSession() {
        sessionStartTime = System.currentTimeMillis();
        prefs.edit()
                .putLong(KEY_LAST_SESSION_START, sessionStartTime)
                .putInt(KEY_TOTAL_SESSIONS, prefs.getInt(KEY_TOTAL_SESSIONS, 0) + 1)
                .apply();

        // Track daily active user
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        prefs.edit().putBoolean(KEY_DAILY_ACTIVE + today, true).apply();

        Log.d(TAG, "Session started. Total sessions: " + prefs.getInt(KEY_TOTAL_SESSIONS, 0));
    }

    public void endSession() {
        if (sessionStartTime > 0) {
            long duration = System.currentTimeMillis() - sessionStartTime;
            long totalTime = prefs.getLong(KEY_TOTAL_SESSION_TIME, 0) + duration;
            prefs.edit().putLong(KEY_TOTAL_SESSION_TIME, totalTime).apply();

            Log.d(TAG, "Session ended. Duration: " + (duration / 1000) + "s, Total: " + (totalTime / 1000) + "s");
        }
        sessionStartTime = 0;
    }

    public long getAverageSessionDuration() {
        int sessions = prefs.getInt(KEY_TOTAL_SESSIONS, 1);
        long totalTime = prefs.getLong(KEY_TOTAL_SESSION_TIME, 0);
        return sessions > 0 ? totalTime / sessions : 0;
    }

    // ============================================
    // AD TRACKING
    // ============================================

    public void trackAdShown(String adType) {
        int total = prefs.getInt(KEY_TOTAL_ADS_SHOWN, 0) + 1;
        prefs.edit().putInt(KEY_TOTAL_ADS_SHOWN, total).apply();
        Log.d(TAG, "Ad shown: " + adType + ". Total: " + total);
    }

    public void trackAdCompleted(String adType) {
        int total = prefs.getInt(KEY_TOTAL_ADS_COMPLETED, 0) + 1;
        prefs.edit().putInt(KEY_TOTAL_ADS_COMPLETED, total).apply();
        Log.d(TAG, "Ad completed: " + adType + ". Total: " + total);
    }

    public void trackAdSkipped(String adType) {
        int total = prefs.getInt(KEY_TOTAL_ADS_SKIPPED, 0) + 1;
        prefs.edit().putInt(KEY_TOTAL_ADS_SKIPPED, total).apply();
        Log.d(TAG, "Ad skipped: " + adType + ". Total: " + total);
    }

    public float getAdCompletionRate() {
        int shown = prefs.getInt(KEY_TOTAL_ADS_SHOWN, 0);
        int completed = prefs.getInt(KEY_TOTAL_ADS_COMPLETED, 0);
        return shown > 0 ? (float) completed / shown * 100 : 0;
    }

    // ============================================
    // FEATURE TRACKING
    // ============================================

    public void trackFeatureClick(String feature) {
        String key = KEY_FEATURE_CLICKS + feature;
        int clicks = prefs.getInt(key, 0) + 1;
        prefs.edit().putInt(key, clicks).apply();
        Log.d(TAG, "Feature clicked: " + feature + ". Total: " + clicks);
    }

    public int getFeatureClicks(String feature) {
        return prefs.getInt(KEY_FEATURE_CLICKS + feature, 0);
    }

    // ============================================
    // INSIGHTS
    // ============================================

    public Map<String, Object> getInsights() {
        Map<String, Object> insights = new HashMap<>();

        insights.put("totalSessions", prefs.getInt(KEY_TOTAL_SESSIONS, 0));
        insights.put("avgSessionSeconds", getAverageSessionDuration() / 1000);
        insights.put("totalAdsShown", prefs.getInt(KEY_TOTAL_ADS_SHOWN, 0));
        insights.put("totalAdsCompleted", prefs.getInt(KEY_TOTAL_ADS_COMPLETED, 0));
        insights.put("totalAdsSkipped", prefs.getInt(KEY_TOTAL_ADS_SKIPPED, 0));
        insights.put("adCompletionRate", getAdCompletionRate());

        // Feature usage
        insights.put("spinClicks", getFeatureClicks("spin"));
        insights.put("miningClicks", getFeatureClicks("mining"));
        insights.put("checkinClicks", getFeatureClicks("checkin"));
        insights.put("boostClicks", getFeatureClicks("boost"));
        insights.put("referralClicks", getFeatureClicks("referral"));

        return insights;
    }

    /**
     * Upload insights to Firebase for analysis
     */
    public void syncToFirebase() {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("analytics")
                    .child(userId);

            Map<String, Object> data = getInsights();
            data.put("lastSync", System.currentTimeMillis());
            data.put("appVersion", AppConfig.APP_VERSION);

            ref.updateChildren(data);
            Log.d(TAG, "Analytics synced to Firebase");

        } catch (Exception e) {
            Log.e(TAG, "Error syncing analytics", e);
        }
    }

    /**
     * Get user engagement score (0-100)
     */
    public int getEngagementScore() {
        int sessions = prefs.getInt(KEY_TOTAL_SESSIONS, 0);
        long avgSession = getAverageSessionDuration() / 1000; // seconds
        float adCompletion = getAdCompletionRate();
        int featureUse = getFeatureClicks("spin") + getFeatureClicks("mining") +
                         getFeatureClicks("checkin") + getFeatureClicks("boost");

        // Score calculation
        int sessionScore = Math.min(sessions * 2, 25); // Max 25
        int durationScore = Math.min((int)(avgSession / 12), 25); // 5 min = 25 points
        int adScore = (int)(adCompletion / 4); // 100% = 25 points
        int featureScore = Math.min(featureUse, 25); // Max 25

        return sessionScore + durationScore + adScore + featureScore;
    }

    /**
     * Get user segment for targeted ads
     */
    public String getUserSegment() {
        int score = getEngagementScore();
        if (score >= 75) return "power_user";
        if (score >= 50) return "engaged";
        if (score >= 25) return "casual";
        return "new_user";
    }
}

