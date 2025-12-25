package network.lynx.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class ReferralUtils {
    private static final String TAG = "ReferralUtils";
    private static final String PREFS_NAME = "userData";
    private static final String KEY_USER_ID = "userid";
    private static final String KEY_REFERRAL_CODE = "referralCode";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";

    // Return the appropriate SharedPreferences for a given uid. If uid is null/empty, attempt to
    // derive it from the global prefs or Firebase; if still not available, fall back to global
    // prefs (shared across users).
    public static SharedPreferences getPrefs(Context ctx, String uid) {
        if (ctx == null) return null;
        try {
            // If uid provided, use user-scoped prefs
            if (uid != null && !uid.isEmpty()) {
                return ctx.getSharedPreferences(PREFS_NAME + "_" + uid, Context.MODE_PRIVATE);
            }

            // Try global prefs for stored userid
            SharedPreferences global = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String stored = global.getString(KEY_USER_ID, null);
            if (stored != null && !stored.isEmpty()) {
                return ctx.getSharedPreferences(PREFS_NAME + "_" + stored, Context.MODE_PRIVATE);
            }

            // Try FirebaseAuth as last resort
            try {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null && user.getUid() != null && !user.getUid().isEmpty()) {
                    return ctx.getSharedPreferences(PREFS_NAME + "_" + user.getUid(), Context.MODE_PRIVATE);
                }
            } catch (Exception ignored) {
            }

            // Fallback to global prefs (shared)
            return global;
        } catch (Exception e) {
            Log.e(TAG, "Error getting SharedPreferences", e);
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    // Backwards-compatible helper
    public static SharedPreferences getPrefs(Context ctx) {
        return getPrefs(ctx, null);
    }

    // Save profile details into user-scoped prefs when possible (avoids overwriting other users)
    public static void saveProfileToPrefs(Context ctx, String uid, String name, String email, String code) {
        try {
            SharedPreferences prefs = getPrefs(ctx, uid);
            if (prefs == null) return;
            SharedPreferences.Editor editor = prefs.edit();
            if (uid != null && !uid.isEmpty()) editor.putString(KEY_USER_ID, uid);
            if (name != null && !name.isEmpty()) editor.putString(KEY_USER_NAME, name);
            if (email != null && !email.isEmpty()) editor.putString(KEY_USER_EMAIL, email);
            if (code != null && !code.isEmpty() && !"XXXXXX".equals(code)) editor.putString(KEY_REFERRAL_CODE, code);
            editor.apply();

            // Also keep a pointer in the global prefs for convenience (so older code can find the
            // user's scoped prefs). This pointer is safe to overwrite on login for the current device.
            try {
                SharedPreferences global = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                if (uid != null && !uid.isEmpty()) global.edit().putString(KEY_USER_ID, uid).apply();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving profile to prefs", e);
        }
    }

    // Read cached referral code for the current user (or for the provided uid)
    public static String getCachedReferralCode(Context ctx, String uid) {
        try {
            SharedPreferences prefs = getPrefs(ctx, uid);
            if (prefs == null) return null;
            String code = prefs.getString(KEY_REFERRAL_CODE, null);
            if (code != null && !code.isEmpty() && !"XXXXXX".equals(code)) return code;
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error reading cached referral code", e);
            return null;
        }
    }

    // Backwards-compatible method used by existing callers
    public static String getCachedReferralCode(Context ctx) {
        return getCachedReferralCode(ctx, null);
    }

    public static String generateReferralCode(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "LYNX" + (System.currentTimeMillis() % 10000);
        }

        try {
            String encoded = Base64.encodeToString(uid.getBytes(), Base64.NO_WRAP);
            String code = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
            if (code.length() >= 6) return code.substring(0, 6);
            StringBuilder padded = new StringBuilder(code);
            while (padded.length() < 6) {
                padded.append(Math.abs(uid.hashCode() % 10));
            }
            return padded.substring(0, 6);
        } catch (Exception e) {
            Log.e(TAG, "Error generating referral code", e);
            return "LYNX" + Math.abs(uid.hashCode() % 10000);
        }
    }

    // Share referral code using the cached value or generate one for the current user
    public static void shareReferral(Context ctx) {
        if (ctx == null) return;
        try {
            // Try to get cached code (per-user)
            String code = getCachedReferralCode(ctx);

            // If not found, try to derive uid from global pointer or Firebase
            if (code == null || code.isEmpty()) {
                String uid = null;
                try {
                    SharedPreferences global = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    uid = global.getString(KEY_USER_ID, null);
                } catch (Exception ignored) {
                }

                if ((uid == null || uid.isEmpty())) {
                    try {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) uid = user.getUid();
                    } catch (Exception ignored) {
                    }
                }

                if (uid != null && !uid.isEmpty()) {
                    code = getCachedReferralCode(ctx, uid);
                    if (code == null || code.isEmpty()) {
                        code = generateReferralCode(uid);
                        // Save generated code back to user prefs
                        saveProfileToPrefs(ctx, uid, null, null, code);
                    }
                }
            }

            if (code == null || code.isEmpty()) return;

            String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + code;
            String message = "Join Lynx Network and start earning!\n\n" +
                    "Use my referral code: " + code + "\n" +
                    "Download here: " + inviteLink + "\n\n" +
                    "We both get mining speed boosts!\n\nYou get 20% of my mining rewards forever!";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join Lynx Network!");
            Intent chooser = Intent.createChooser(shareIntent, "Share via");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Exception e) {
            Log.e(TAG, "Error sharing referral", e);
        }
    }

    // Clear per-user cached prefs for a given uid
    public static void clearUserPrefs(Context ctx, String uid) {
        if (ctx == null || uid == null || uid.isEmpty()) return;
        try {
            ctx.getSharedPreferences(PREFS_NAME + "_" + uid, Context.MODE_PRIVATE).edit().clear().apply();
            // If global pointer points to this uid, remove it
            try {
                SharedPreferences global = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String globalUid = global.getString(KEY_USER_ID, null);
                if (uid.equals(globalUid)) global.edit().remove(KEY_USER_ID).apply();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing user prefs", e);
        }
    }

    // Clear cached data for currently-signed-in user (if any)
    public static void clearCurrentUserPrefs(Context ctx) {
        if (ctx == null) return;
        try {
            String uid = null;
            try {
                SharedPreferences global = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                uid = global.getString(KEY_USER_ID, null);
            } catch (Exception ignored) {}
            if (uid == null || uid.isEmpty()) {
                try {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) uid = user.getUid();
                } catch (Exception ignored) {}
            }
            if (uid != null && !uid.isEmpty()) {
                clearUserPrefs(ctx, uid);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing current user prefs", e);
        }
    }
}
