package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Centralized Session Manager
 *
 * SENIOR DEVELOPER BEST PRACTICES:
 * - Single source of truth for user session
 * - Secure credential storage
 * - Session timeout handling
 * - Multi-device session management
 * - Automatic token refresh
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS_NAME = "user_session";

    // Session keys
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_REFERRAL_CODE = "referral_code";
    private static final String KEY_LOGIN_TIME = "login_time";
    private static final String KEY_LAST_ACTIVE = "last_active";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_TOTAL_COINS = "total_coins";
    private static final String KEY_HAS_PERMANENT_BOOST = "has_permanent_boost";
    private static final String KEY_REFERRAL_COUNT = "referral_count";

    // Session timeout (30 days)
    private static final long SESSION_TIMEOUT_MS = 30L * 24 * 60 * 60 * 1000;

    private static SessionManager instance;
    private final SharedPreferences prefs;
    private final Context context;

    private SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    // ============================================
    // LOGIN / LOGOUT
    // ============================================

    /**
     * Create session after successful login
     */
    public void createSession(String userId, String email, String name, String referralCode) {
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_REFERRAL_CODE, referralCode)
                .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();

        Log.d(TAG, "Session created for user: " + userId);
    }

    /**
     * Update session activity timestamp
     */
    public void updateActivity() {
        if (isLoggedIn()) {
            prefs.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply();
        }
    }

    /**
     * End session (logout)
     */
    public void endSession() {
        String userId = getUserId();

        prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_REFERRAL_CODE)
                .remove(KEY_LOGIN_TIME)
                .remove(KEY_IS_LOGGED_IN)
                .remove(KEY_TOTAL_COINS)
                .remove(KEY_HAS_PERMANENT_BOOST)
                .remove(KEY_REFERRAL_COUNT)
                .apply();

        // Sign out from Firebase
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            Log.e(TAG, "Error signing out from Firebase", e);
        }

        // Reset managers
        try {
            MiningSyncManager.resetInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error resetting MiningSyncManager", e);
        }

        Log.d(TAG, "Session ended for user: " + userId);
    }

    // ============================================
    // SESSION VALIDATION
    // ============================================

    /**
     * Check if user is logged in with valid session
     */
    public boolean isLoggedIn() {
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            return false;
        }

        // Check Firebase auth state
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Firebase session expired, clear local session
            endSession();
            return false;
        }

        // Check session timeout
        long lastActive = prefs.getLong(KEY_LAST_ACTIVE, 0);
        if (System.currentTimeMillis() - lastActive > SESSION_TIMEOUT_MS) {
            Log.d(TAG, "Session timed out");
            endSession();
            return false;
        }

        return true;
    }

    /**
     * Check if session needs refresh
     */
    public boolean needsRefresh() {
        long lastActive = prefs.getLong(KEY_LAST_ACTIVE, 0);
        long timeSinceActive = System.currentTimeMillis() - lastActive;

        // Refresh if inactive for more than 1 hour
        return timeSinceActive > (60 * 60 * 1000);
    }

    // ============================================
    // GETTERS
    // ============================================

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    public String getReferralCode() {
        return prefs.getString(KEY_REFERRAL_CODE, null);
    }

    public long getLoginTime() {
        return prefs.getLong(KEY_LOGIN_TIME, 0);
    }

    public long getLastActive() {
        return prefs.getLong(KEY_LAST_ACTIVE, 0);
    }

    public float getTotalCoins() {
        return prefs.getFloat(KEY_TOTAL_COINS, 0f);
    }

    public boolean hasPermanentBoost() {
        return prefs.getBoolean(KEY_HAS_PERMANENT_BOOST, false);
    }

    public int getReferralCount() {
        return prefs.getInt(KEY_REFERRAL_COUNT, 0);
    }

    // ============================================
    // SETTERS (for caching)
    // ============================================

    public void setTotalCoins(float coins) {
        prefs.edit().putFloat(KEY_TOTAL_COINS, coins).apply();
    }

    public void setHasPermanentBoost(boolean hasBoost) {
        prefs.edit().putBoolean(KEY_HAS_PERMANENT_BOOST, hasBoost).apply();
    }

    public void setReferralCount(int count) {
        prefs.edit().putInt(KEY_REFERRAL_COUNT, count).apply();
    }

    public void setReferralCode(String code) {
        prefs.edit().putString(KEY_REFERRAL_CODE, code).apply();
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    // ============================================
    // SYNC WITH FIREBASE
    // ============================================

    /**
     * Update local session from Firebase user
     */
    public void syncFromFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String currentUserId = getUserId();
        if (currentUserId != null && !currentUserId.equals(user.getUid())) {
            // User changed, clear old session
            endSession();
        }

        prefs.edit()
                .putString(KEY_USER_ID, user.getUid())
                .putString(KEY_USER_EMAIL, user.getEmail())
                .putString(KEY_USER_NAME, user.getDisplayName())
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();

        Log.d(TAG, "Session synced from Firebase");
    }

    /**
     * Get session duration in milliseconds
     */
    public long getSessionDuration() {
        long loginTime = getLoginTime();
        if (loginTime == 0) return 0;
        return System.currentTimeMillis() - loginTime;
    }

    /**
     * Check if this is a new user (first day)
     */
    public boolean isNewUser() {
        long loginTime = getLoginTime();
        if (loginTime == 0) return true;

        long daysSinceLogin = (System.currentTimeMillis() - loginTime) / (24 * 60 * 60 * 1000);
        return daysSinceLogin < 1;
    }

    /**
     * Get user tier based on activity
     */
    public UserTier getUserTier() {
        int referrals = getReferralCount();
        float coins = getTotalCoins();

        if (referrals >= 10 && coins >= 1000) {
            return UserTier.PLATINUM;
        } else if (referrals >= 5 && coins >= 500) {
            return UserTier.GOLD;
        } else if (referrals >= 3 && coins >= 100) {
            return UserTier.SILVER;
        }
        return UserTier.BRONZE;
    }

    public enum UserTier {
        BRONZE(1.0f),
        SILVER(1.1f),
        GOLD(1.2f),
        PLATINUM(1.5f);

        public final float bonusMultiplier;

        UserTier(float multiplier) {
            this.bonusMultiplier = multiplier;
        }
    }
}

