package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * KYC VERIFICATION SYSTEM - Inspired by Pi Network
 *
 * KYC creates value perception - tokens are "real" and valuable.
 * Users complete verification to unlock full features.
 *
 * VERIFICATION LEVELS:
 * 1. Phone Verification - +5% mining boost
 * 2. Email Verification - +5% mining boost
 * 3. ID Verification - +10% mining boost + withdraw enabled
 *
 * WHY IT WORKS:
 * - Creates perception of legitimacy
 * - Users invested in the process don't leave
 * - Reduces bots/fake accounts
 * - Enables future token distribution
 *
 * NOTE: For now, we implement simple verification.
 * Full KYC with ID can be added later when needed.
 */
public class KYCManager {
    private static final String TAG = "KYCManager";
    private static final String PREFS_NAME = "kyc_verification";

    // Verification levels
    public static final int LEVEL_UNVERIFIED = 0;
    public static final int LEVEL_PHONE = 1;
    public static final int LEVEL_EMAIL = 2;
    public static final int LEVEL_FULL = 3;

    // Boosts per level
    public static final float PHONE_BOOST = 0.05f;  // 5%
    public static final float EMAIL_BOOST = 0.05f;  // 5%
    public static final float FULL_KYC_BOOST = 0.10f; // 10%

    private static KYCManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private String currentUserId;

    private int verificationLevel = LEVEL_UNVERIFIED;
    private boolean phoneVerified = false;
    private boolean emailVerified = false;
    private boolean idVerified = false;
    private KYCUpdateListener listener;

    public static class KYCStatus {
        public int level;
        public String levelName;
        public boolean phoneVerified;
        public boolean emailVerified;
        public boolean idVerified;
        public float totalBoost;
        public boolean canWithdraw;
        public int completionPercent;
        public String nextStep;
        public String nextStepReward;
    }

    public interface KYCUpdateListener {
        void onKYCUpdated(KYCStatus status);
        void onVerificationComplete(int level, float newBoost);
    }

    private KYCManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadKYCStatus();
        }
    }

    public static synchronized KYCManager getInstance(Context context) {
        if (instance == null) {
            instance = new KYCManager(context);
        }
        return instance;
    }

    public void setListener(KYCUpdateListener listener) {
        this.listener = listener;
    }

    private void loadKYCStatus() {
        if (currentUserId == null) return;

        dbRef.child("users").child(currentUserId).child("kyc")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            phoneVerified = Boolean.TRUE.equals(snapshot.child("phoneVerified").getValue(Boolean.class));
                            emailVerified = Boolean.TRUE.equals(snapshot.child("emailVerified").getValue(Boolean.class));
                            idVerified = Boolean.TRUE.equals(snapshot.child("idVerified").getValue(Boolean.class));
                        }

                        updateVerificationLevel();
                        cacheStatus();
                        notifyListener();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Error loading KYC", error.toException());
                        loadCachedStatus();
                    }
                });
    }

    private void loadCachedStatus() {
        phoneVerified = prefs.getBoolean("phoneVerified", false);
        emailVerified = prefs.getBoolean("emailVerified", false);
        idVerified = prefs.getBoolean("idVerified", false);
        updateVerificationLevel();
    }

    private void cacheStatus() {
        prefs.edit()
                .putBoolean("phoneVerified", phoneVerified)
                .putBoolean("emailVerified", emailVerified)
                .putBoolean("idVerified", idVerified)
                .putInt("verificationLevel", verificationLevel)
                .apply();
    }

    private void updateVerificationLevel() {
        if (idVerified) {
            verificationLevel = LEVEL_FULL;
        } else if (emailVerified) {
            verificationLevel = LEVEL_EMAIL;
        } else if (phoneVerified) {
            verificationLevel = LEVEL_PHONE;
        } else {
            verificationLevel = LEVEL_UNVERIFIED;
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onKYCUpdated(getKYCStatus());
        }
    }

    public KYCStatus getKYCStatus() {
        KYCStatus status = new KYCStatus();

        status.level = verificationLevel;
        status.levelName = getLevelName(verificationLevel);
        status.phoneVerified = phoneVerified;
        status.emailVerified = emailVerified;
        status.idVerified = idVerified;
        status.totalBoost = getTotalBoost();
        status.canWithdraw = idVerified;

        // Calculate completion
        int steps = 0;
        if (phoneVerified) steps++;
        if (emailVerified) steps++;
        if (idVerified) steps++;
        status.completionPercent = (steps * 100) / 3;

        // Next step
        if (!phoneVerified) {
            status.nextStep = "Verify Phone Number";
            status.nextStepReward = "+5% mining boost";
        } else if (!emailVerified) {
            status.nextStep = "Verify Email";
            status.nextStepReward = "+5% mining boost";
        } else if (!idVerified) {
            status.nextStep = "Complete ID Verification";
            status.nextStepReward = "+10% boost + Withdrawals";
        } else {
            status.nextStep = "Fully Verified";
            status.nextStepReward = "All benefits unlocked";
        }

        return status;
    }

    private String getLevelName(int level) {
        switch (level) {
            case LEVEL_PHONE: return "Phone Verified";
            case LEVEL_EMAIL: return "Email Verified";
            case LEVEL_FULL: return "Fully Verified";
            default: return "Unverified";
        }
    }

    public float getTotalBoost() {
        float boost = 0f;
        if (phoneVerified) boost += PHONE_BOOST;
        if (emailVerified) boost += EMAIL_BOOST;
        if (idVerified) boost += FULL_KYC_BOOST;
        return boost;
    }

    /**
     * Mark phone as verified (after OTP verification)
     */
    public void verifyPhone(VerificationCallback callback) {
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        // In production, this would verify OTP first
        // For now, we'll mark as verified directly

        Map<String, Object> update = new HashMap<>();
        update.put("phoneVerified", true);
        update.put("phoneVerifiedAt", System.currentTimeMillis());

        dbRef.child("users").child(currentUserId).child("kyc")
                .updateChildren(update)
                .addOnSuccessListener(aVoid -> {
                    phoneVerified = true;
                    updateVerificationLevel();
                    cacheStatus();

                    if (listener != null) {
                        listener.onVerificationComplete(LEVEL_PHONE, PHONE_BOOST);
                    }

                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Mark email as verified (after email link verification)
     */
    public void verifyEmail(VerificationCallback callback) {
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("emailVerified", true);
        update.put("emailVerifiedAt", System.currentTimeMillis());

        dbRef.child("users").child(currentUserId).child("kyc")
                .updateChildren(update)
                .addOnSuccessListener(aVoid -> {
                    emailVerified = true;
                    updateVerificationLevel();
                    cacheStatus();

                    if (listener != null) {
                        listener.onVerificationComplete(LEVEL_EMAIL, EMAIL_BOOST);
                    }

                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Submit ID for verification
     * In production, this would upload ID images for manual review
     */
    public void submitIDVerification(String idType, String idNumber, VerificationCallback callback) {
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        Map<String, Object> submission = new HashMap<>();
        submission.put("idType", idType);
        submission.put("idNumber", idNumber);
        submission.put("submittedAt", System.currentTimeMillis());
        submission.put("status", "pending");

        dbRef.child("users").child(currentUserId).child("kyc").child("idSubmission")
                .setValue(submission)
                .addOnSuccessListener(aVoid -> {
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Check if email is verified through Firebase Auth
     */
    public void checkFirebaseEmailVerification() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            boolean verified = FirebaseAuth.getInstance().getCurrentUser().isEmailVerified();
            if (verified && !emailVerified) {
                verifyEmail(new VerificationCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Email verification synced from Firebase Auth");
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "Error syncing email verification: " + message);
                    }
                });
            }
        }
    }

    public int getVerificationLevel() {
        return verificationLevel;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isFullyVerified() {
        return idVerified;
    }

    public boolean canWithdraw() {
        return idVerified;
    }

    public interface VerificationCallback {
        void onSuccess();
        void onError(String message);
    }
}

