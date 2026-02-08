package network.lynx.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

public class AdConsentManager {
    private static final String TAG = "AdConsentManager";
    private static final String PREFS_NAME = "ad_consent_prefs";
    private static final String KEY_CONSENT_GIVEN = "consent_given_";
    private static final String KEY_CONSENT_DATE = "consent_date_";
    private static final long CONSENT_CACHE_DURATION = 30 * 60 * 1000; // 30 minutes - optimized for better UX

    public interface ConsentCallback {
        void onConsentGiven();
        void onConsentDenied();
    }

    /**
     * Show consent dialog for check-in feature
     */
    public static void showCheckInConsentDialog(Activity activity, ConsentCallback callback) {
        if (hasRecentConsent(activity, "checkin")) {
            callback.onConsentGiven();
            return;
        }

        showConsentDialog(activity,
                "Daily Check-in",
                "Watch a short ad to claim your daily reward.\n\n" +
                        "You'll receive:\n" +
                        "• Daily LYX tokens\n" +
                        "• Streak bonus\n" +
                        "• Level progression",
                "checkin",
                callback);
    }

    /**
     * Show consent dialog for spin feature
     */
    public static void showSpinConsentDialog(Activity activity, ConsentCallback callback) {
        if (hasRecentConsent(activity, "spin")) {
            callback.onConsentGiven();
            return;
        }

        showConsentDialog(activity,
                "Spin Wheel",
                "Watch a short ad to earn a spin.\n\n" +
                        "You'll receive:\n" +
                        "• Chance to win LYX tokens\n" +
                        "• Bonus multipliers\n" +
                        "• Special rewards",
                "spin",
                callback);
    }

    /**
     * Show consent dialog for mining feature
     */
    public static void showMiningConsentDialog(Activity activity, ConsentCallback callback) {
        if (hasRecentConsent(activity, "mining")) {
            callback.onConsentGiven();
            return;
        }

        showConsentDialog(activity,
                "Start Mining",
                "Watch a short ad to start mining at 2x speed.\n\n" +
                        "You'll receive:\n" +
                        "• 2x mining rate\n" +
                        "• 24 hours continuous mining\n" +
                        "• Automatic token generation",
                "mining",
                callback);
    }

    /**
     * Show consent dialog for boost feature
     */
    public static void showBoostConsentDialog(Activity activity, ConsentCallback callback) {
        if (hasRecentConsent(activity, "boost")) {
            callback.onConsentGiven();
            return;
        }

        showConsentDialog(activity,
                "Boost Mining",
                "Watch a short ad to boost your mining speed.\n\n" +
                        "You'll receive:\n" +
                        "• 20% faster mining\n" +
                        "• 1 hour boost duration\n" +
                        "• Extra token generation",
                "boost",
                callback);
    }

    /**
     * Show minimal consent dialog for game features (prediction, coinflip, etc.)
     */
    public static void showMinimalConsentDialog(Activity activity, String gameType, ConsentCallback callback) {
        if (hasRecentConsent(activity, gameType)) {
            callback.onConsentGiven();
            return;
        }

        String title;
        String message;

        switch (gameType) {
            case "prediction":
                title = "Daily Prediction";
                message = "Watch a short ad to play the daily prediction game and win up to 25 LYX!";
                break;
            case "coinflip":
                title = "Coin Flip";
                message = "Watch a short ad to play the coin flip game and double your bet!";
                break;
            default:
                title = "Play Game";
                message = "Watch a short ad to unlock this game and win rewards!";
                break;
        }

        showConsentDialog(activity, title, message, gameType, callback);
    }

    /**
     * Generic consent dialog
     */
    private static void showConsentDialog(Activity activity, String title, String message,
                                          String consentType, ConsentCallback callback) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            callback.onConsentDenied();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.ad_consent_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialog);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        TextView dialogTitle = dialogView.findViewById(R.id.consentDialogTitle);
        TextView dialogMessage = dialogView.findViewById(R.id.consentDialogMessage);
        MaterialButton watchAdButton = dialogView.findViewById(R.id.watchAdButton);
        MaterialButton skipButton = dialogView.findViewById(R.id.skipButton);
        TextView cancelButton = dialogView.findViewById(R.id.cancelButton);

        dialogTitle.setText(title);
        dialogMessage.setText(message);

        // Customize buttons based on feature
        if ("mining".equals(consentType)) {
            skipButton.setText("Mine Slower");
            skipButton.setVisibility(View.VISIBLE);
        } else {
            skipButton.setVisibility(View.GONE);
        }

        watchAdButton.setOnClickListener(v -> {
            recordConsent(activity, consentType, true);
            dialog.dismiss();
            callback.onConsentGiven();
        });

        skipButton.setOnClickListener(v -> {
            recordConsent(activity, consentType, false);
            dialog.dismiss();
            if ("mining".equals(consentType)) {
                callback.onConsentGiven(); // Allow slower mining
            } else {
                callback.onConsentDenied();
            }
        });

        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onConsentDenied();
        });

        dialog.show();
    }

    /**
     * Record user consent
     */
    private static void recordConsent(Context context, String consentType, boolean watchAd) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_CONSENT_GIVEN + consentType, watchAd)
                .putLong(KEY_CONSENT_DATE + consentType, System.currentTimeMillis())
                .apply();
    }

    /**
     * Check if user has given recent consent (within 30 minutes)
     */
    private static boolean hasRecentConsent(Context context, String consentType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long consentDate = prefs.getLong(KEY_CONSENT_DATE + consentType, 0);
        boolean consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN + consentType, false);

        boolean isRecent = (System.currentTimeMillis() - consentDate) < CONSENT_CACHE_DURATION;

        return consentGiven && isRecent;
    }

    /**
     * Clear all consent data (for testing or reset)
     */
    public static void clearAllConsent(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * Get consent status for a specific type
     */
    public static boolean hasConsent(Context context, String consentType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CONSENT_GIVEN + consentType, false);
    }
}
