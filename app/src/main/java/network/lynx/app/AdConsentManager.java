package network.lynx.app;
import static com.google.common.io.Resources.getResource;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class AdConsentManager {
    private static final String TAG = "AdConsentManager";
    private static final String PREFS_NAME = "ad_consent_prefs";
    private static final String KEY_CONSENT_GIVEN = "consent_given_";
    private static final String KEY_CONSENT_DATE = "consent_date_";

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
                "Daily Check-in Reward",
                "Watch a short ad to claim your daily check-in reward and maintain your streak!\n\n" +
                        "🎁 What you'll get:\n" +
                        "• Daily LYX tokens based on your streak\n" +
                        "• Streak multiplier bonus\n" +
                        "• Level progression points\n\n" +
                        "The ad helps us keep the app free and reward our users.",
                "checkin",
                callback);
    }

    /**
     * Show consent dialog for spin feature
     */
    public static void showSpinConsentDialog(Activity activity, ConsentCallback callback) {
//        if (hasRecentConsent(activity, "spin")) {
//            callback.onConsentGiven();
//            return;
//        }

        showConsentDialog(activity,
                "Spin Wheel Reward",
                "Watch a short ad to earn a spin on our reward wheel!\n\n" +
                        "🎰 What you'll get:\n" +
                        "• Chance to win LYX tokens\n" +
                        "• Bonus multipliers\n" +
                        "• Special prizes and rewards\n" +
                        "• Up to 5 spins per day\n\n" +
                        "Each ad gives you one spin opportunity.",
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
                "Start Mining Session",
                "Watch a short ad to start your 24-hour mining session!\n\n" +
                        "⛏️ What you'll get:\n" +
                        "• Higher mining rate (2x normal speed)\n" +
                        "• 24 hours of continuous mining\n" +
                        "• Automatic LYX token generation\n" +
                        "• Referral bonus multipliers\n\n" +
                        "Without the ad, you can still mine at a slower rate.",
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
                "Boost Mining Speed",
                "Watch a short ad to boost your mining speed!\n\n" +
                        "🚀 What you'll get:\n" +
                        "• 20% faster mining rate\n" +
                        "• 1 hour of boosted mining\n" +
                        "• Extra LYX token generation\n" +
                        "• Stackable with other bonuses\n\n" +
                        "This temporary boost will significantly increase your earnings.",
                "boost",
                callback);
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
        Button watchAdButton = dialogView.findViewById(R.id.watchAdButton);
        Button skipButton = dialogView.findViewById(R.id.skipButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

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
     * Check if user has given recent consent (within 1 hour)
     */
    private static boolean hasRecentConsent(Context context, String consentType) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long consentDate = prefs.getLong(KEY_CONSENT_DATE + consentType, 0);
        boolean consentGiven = prefs.getBoolean(KEY_CONSENT_GIVEN + consentType, false);

        long oneHour = 60 * 60 * 1000; // 1 hour in milliseconds
        boolean isRecent = (System.currentTimeMillis() - consentDate) < oneHour;

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
