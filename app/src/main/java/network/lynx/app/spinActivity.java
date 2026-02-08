package network.lynx.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * PROFESSIONAL SPIN ACTIVITY - ENHANCED FEATURES
 *
 * NEW FEATURES:
 * 1. Spin Streak - Consecutive days = bonus multiplier
 * 2. Lucky Multiplier - Random 2x-5x on spins
 * 3. Daily Jackpot - Chance to win big
 * 4. Progress to Next Tier - Visual progression
 * 5. Referral Bonus Spins - Extra spins for referrals
 */
public class spinActivity extends AppCompatActivity {
    private static final String TAG = "SpinActivity";

    // UI Elements
    private ImageView back;
    private SharedPreferences prefs;
    private SpinWheelView spinWheel;
    private Button spinButton;
    private TextView spinCountText;
    private TextView rewardInfoText;
    private TextView spinHintText;
    private TextView lastWinText;

    // NEW: Enhanced UI Elements
    private TextView streakText;
    private TextView multiplierText;
    private ProgressBar tierProgress;
    private TextView tierText;
    private CardView jackpotCard;
    private TextView jackpotAmount;

    // Wallet manager (for global first-spin-free)
    private WalletManager walletManager;

    // Configuration - Optimized for revenue
    private static final int FREE_SPINS_PER_DAY = 1;
    private static final int AD_SPINS_PER_DAY = 4;
    private static final int TOTAL_SPINS_PER_DAY = FREE_SPINS_PER_DAY + AD_SPINS_PER_DAY;

    // Cooldown optimized
    private static final long SPIN_AD_COOLDOWN_MS = 3 * 60 * 1000; // 3 minutes
    private static final long SPIN_INTERVAL_MS = 15 * 1000; // 15 seconds

    // NEW: Reward values
    private static final double[] SPIN_REWARDS = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 5.0, 10.0};
    private static final double JACKPOT_CHANCE = 0.02; // 2% chance
    private static final double JACKPOT_REWARD = 50.0;

    // NEW: Tier thresholds
    private static final int[] TIER_THRESHOLDS = {0, 10, 25, 50, 100};
    private static final String[] TIER_NAMES = {"Bronze", "Silver", "Gold", "Platinum", "Diamond"};
    private static final float[] TIER_MULTIPLIERS = {1.0f, 1.1f, 1.25f, 1.5f, 2.0f};

    private AdManager adManager;
    private boolean isSpinning = false;
    private CountDownTimer cooldownTimer;
    private TaskManager taskManager;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_spin);

        initializeViews();
        initializePreferences();
        initializeTaskManager();
        resetDailySpinsIfNeeded();
        checkAndUpdateStreak();

        adManager = AdManager.getInstance();

        // Get WalletManager instance to honor global first-spin-free
        walletManager = WalletManager.getInstance(this);

        setupClickListeners();
        updateUI();
        preloadAd();
    }

    private void initializeViews() {
        spinWheel = findViewById(R.id.spinnerView);
        spinButton = findViewById(R.id.spinBtn);
        back = findViewById(R.id.backNav);
        spinCountText = findViewById(R.id.spinCountText);
        rewardInfoText = findViewById(R.id.rewardInfoText);
        spinHintText = findViewById(R.id.spinHintText);
        lastWinText = findViewById(R.id.lastWinText);

        // Optional enhanced views - gracefully handle if not in layout
        streakText = findViewByIdSafe("streakText");
        multiplierText = findViewByIdSafe("multiplierText");
        tierProgress = findViewByIdSafe("tierProgress");
        tierText = findViewByIdSafe("tierText");
        jackpotCard = findViewByIdSafe("jackpotCard");
        jackpotAmount = findViewByIdSafe("jackpotAmount");
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findViewByIdSafe(String name) {
        try {
            int resId = getResources().getIdentifier(name, "id", getPackageName());
            if (resId != 0) {
                return findViewById(resId);
            }
        } catch (Exception e) {
            Log.d(TAG, "Optional view not found: " + name);
        }
        return null;
    }

    private void initializePreferences() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : "guest";
        prefs = getSharedPreferences("spinPrefs_" + userId, MODE_PRIVATE);
    }

    private void initializeTaskManager() {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                taskManager = new TaskManager(this, userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TaskManager", e);
        }
    }

    private void resetDailySpinsIfNeeded() {
        String today = getTodayDate();
        String lastSpinDate = prefs.getString("lastSpinDate", "");

        if (!today.equals(lastSpinDate)) {
            prefs.edit()
                    .putString("lastSpinDate", today)
                    .putInt("freeSpinsUsed", 0)
                    .putInt("adSpinsUsed", 0)
                    .putBoolean("spinAvailable", false)
                    .putLong("lastSpinAdTime", 0)
                    .apply();
            Log.d(TAG, "Daily spins reset");
        }
    }

    // ============================================
    // NEW: STREAK SYSTEM
    // ============================================

    private void checkAndUpdateStreak() {
        String today = getTodayDate();
        String lastStreakDate = prefs.getString("lastStreakDate", "");
        int currentStreak = prefs.getInt("spinStreak", 0);

        if (lastStreakDate.isEmpty()) {
            // First time user
            prefs.edit().putString("lastStreakDate", today).apply();
        } else if (!lastStreakDate.equals(today)) {
            // Check if yesterday
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                Date lastDate = sdf.parse(lastStreakDate);
                Date todayDate = sdf.parse(today);

                if (lastDate != null && todayDate != null) {
                    long diffDays = (todayDate.getTime() - lastDate.getTime()) / (24 * 60 * 60 * 1000);

                    if (diffDays == 1) {
                        // Consecutive day - increment streak
                        currentStreak++;
                    } else if (diffDays > 1) {
                        // Streak broken
                        currentStreak = 0;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing streak date", e);
            }

            prefs.edit()
                    .putString("lastStreakDate", today)
                    .putInt("spinStreak", currentStreak)
                    .apply();
        }

        updateStreakUI();
    }

    private void updateStreakUI() {
        int streak = prefs.getInt("spinStreak", 0);
        float multiplier = getStreakMultiplier();

        if (streakText != null) {
            streakText.setText(streak + " day streak");
        }

        if (multiplierText != null) {
            if (multiplier > 1.0f) {
                multiplierText.setVisibility(View.VISIBLE);
                multiplierText.setText(String.format(Locale.US, "%.1fx bonus", multiplier));
            } else {
                multiplierText.setVisibility(View.GONE);
            }
        }
    }

    private float getStreakMultiplier() {
        int streak = prefs.getInt("spinStreak", 0);
        // Every 7 days = +10% bonus, max 50%
        float bonus = Math.min(streak / 7 * 0.1f, 0.5f);
        return 1.0f + bonus;
    }

    // ============================================
    // NEW: TIER SYSTEM
    // ============================================

    private int getTotalSpins() {
        return prefs.getInt("totalLifetimeSpins", 0);
    }

    private int getCurrentTier() {
        int totalSpins = getTotalSpins();
        int tier = 0;
        for (int i = TIER_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalSpins >= TIER_THRESHOLDS[i]) {
                tier = i;
                break;
            }
        }
        return tier;
    }

    private void updateTierUI() {
        int tier = getCurrentTier();
        int totalSpins = getTotalSpins();

        if (tierText != null) {
            tierText.setText(TIER_NAMES[tier] + " Tier");
        }

        if (tierProgress != null) {
            int nextTier = Math.min(tier + 1, TIER_THRESHOLDS.length - 1);
            int currentThreshold = TIER_THRESHOLDS[tier];
            int nextThreshold = TIER_THRESHOLDS[nextTier];

            if (tier < TIER_THRESHOLDS.length - 1) {
                int progress = (int) ((float)(totalSpins - currentThreshold) /
                        (nextThreshold - currentThreshold) * 100);
                tierProgress.setProgress(Math.min(progress, 100));
            } else {
                tierProgress.setProgress(100);
            }
        }
    }

    private float getTierMultiplier() {
        return TIER_MULTIPLIERS[getCurrentTier()];
    }

    // ============================================
    // NEW: LUCKY MULTIPLIER
    // ============================================

    private float getLuckyMultiplier() {
        // 20% chance of getting a multiplier
        if (random.nextFloat() < 0.2f) {
            // Random multiplier between 2x and 5x
            return 2.0f + random.nextFloat() * 3.0f;
        }
        return 1.0f;
    }

    // ============================================
    // NEW: JACKPOT SYSTEM
    // ============================================

    private void updateJackpotUI() {
        if (jackpotAmount != null) {
            // Progressive jackpot based on total spins today
            int spinsToday = prefs.getInt("freeSpinsUsed", 0) + prefs.getInt("adSpinsUsed", 0);
            double jackpot = JACKPOT_REWARD + (spinsToday * 5); // Increases with each spin
            jackpotAmount.setText(String.format(Locale.US, "%.1f LYX", jackpot));
        }
    }

    private boolean checkJackpot() {
        return random.nextFloat() < JACKPOT_CHANCE;
    }

    private void preloadAd() {
        if (getAdSpinsRemaining() > 0 && !adManager.isAdReady(AdManager.AD_UNIT_SPIN)) {
            adManager.loadRewardedAd(this, AdManager.AD_UNIT_SPIN, new AdManager.AdLoadCallback() {
                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "Ad preloaded");
                    runOnUiThread(() -> updateUI());
                }

                @Override
                public void onAdLoadFailed(String error) {
                    Log.e(TAG, "Ad preload failed: " + error);
                }
            });
        }
    }

    private void setupClickListeners() {
        back.setOnClickListener(v -> finish());
        spinButton.setOnClickListener(v -> handleSpinClick());

        // NEW: Jackpot card click shows info
        if (jackpotCard != null) {
            jackpotCard.setOnClickListener(v -> showJackpotInfo());
        }
    }

    private void showJackpotInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Daily Jackpot")
                .setMessage("Every spin has a 2% chance to win the jackpot!\n\n" +
                        "The jackpot grows with each spin.\n\n" +
                        "Current jackpot: " + jackpotAmount.getText())
                .setPositiveButton("Got it", null)
                .show();
    }

    private void handleSpinClick() {
        if (isSpinning) return;

        long lastSpinTime = prefs.getLong("lastSpinTime", 0);
        if (System.currentTimeMillis() - lastSpinTime < SPIN_INTERVAL_MS) {
            long seconds = (SPIN_INTERVAL_MS - (System.currentTimeMillis() - lastSpinTime)) / 1000;
            ToastUtils.showInfo(this, "Wait " + seconds + "s");
            return;
        }

        if (prefs.getBoolean("spinAvailable", false)) {
            performSpin(false);
            return;
        }

        // If a global first-free is available, treat it as a free spin even if local free used is 0
        if (getFreeSpinsRemaining() > 0) {
            // Determine if this free spin is the WalletManager global free
            boolean isGlobalFirstFree = walletManager != null && walletManager.isFirstSpinFree() && prefs.getInt("totalLifetimeSpins", 0) == 0;
            performSpin(true);
            if (isGlobalFirstFree) {
                // consume global free so it won't be re-used
                walletManager.consumeFirstSpinFree();
            }
            return;
        }

        if (getAdSpinsRemaining() > 0) {
            if (isAdCooldownActive()) {
                showCooldownMessage();
                return;
            }
            // Show watch ad dialog instead of directly showing ad
            showWatchAdDialog();
            return;
        }

        ToastUtils.showInfo(this, "No spins left. Come back tomorrow!");
    }

    private void showWatchAdDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_watch_ad_spin, null);
        builder.setView(dialogView);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button watchAdBtn = dialogView.findViewById(R.id.watchAdButton);
        Button cancelBtn = dialogView.findViewById(R.id.cancelButton);

        // Watch Ad button
        watchAdBtn.setOnClickListener(v -> {
            watchAdBtn.setText("Loading Ad...");
            watchAdBtn.setEnabled(false);
            cancelBtn.setEnabled(false);

            // Small delay to show loading state, then show ad
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dialog.dismiss();
                showAdForSpin();
            }, 500);
        });

        // Cancel button
        cancelBtn.setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private void performSpin(boolean isFree) {
        if (isFree) {
            int used = prefs.getInt("freeSpinsUsed", 0);
            prefs.edit().putInt("freeSpinsUsed", used + 1).apply();
        } else {
            prefs.edit().putBoolean("spinAvailable", false).apply();
        }

        prefs.edit().putLong("lastSpinTime", System.currentTimeMillis()).apply();

        // Increment lifetime spins
        int totalSpins = prefs.getInt("totalLifetimeSpins", 0);
        prefs.edit().putInt("totalLifetimeSpins", totalSpins + 1).apply();

        doSpinAnimation();
    }

    private void showAdForSpin() {
        spinButton.setText("⏳ Loading Ad...");
        spinButton.setEnabled(false);

        adManager.recordFeatureUsage(this, AdManager.AD_UNIT_SPIN);

        adManager.showRewardedAd(this, AdManager.AD_UNIT_SPIN, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {
                Log.d(TAG, "Ad showing");
            }

            @Override
            public void onAdShowFailed(String error) {
                ToastUtils.showError(spinActivity.this, "Ad unavailable. Try again later.");
                resetButton();
            }

            @Override
            public void onAdDismissed() {
                ToastUtils.showInfo(spinActivity.this, "Complete the ad to earn spin");
                resetButton();
            }

            @Override
            public void onAdNotAvailable() {
                grantBonusSpin();
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                grantAdSpinReward();
            }
        }, true);
    }

    private void grantAdSpinReward() {
        int used = prefs.getInt("adSpinsUsed", 0);
        prefs.edit()
                .putInt("adSpinsUsed", used + 1)
                .putBoolean("spinAvailable", true)
                .putLong("lastSpinAdTime", System.currentTimeMillis())
                .apply();

        ToastUtils.showInfo(this, "🎉 Spin unlocked! Tap to spin.");
        spinButton.setText("🎡  SPIN NOW");
        spinButton.setEnabled(true);
        preloadAd();
    }

    private void grantBonusSpin() {
        prefs.edit().putBoolean("spinAvailable", true).apply();
        ToastUtils.showInfo(this, "🎁 Bonus spin granted!");
        spinButton.setText("🎡  SPIN NOW");
        spinButton.setEnabled(true);
    }

    private void doSpinAnimation() {
        isSpinning = true;
        spinButton.setEnabled(false);
        spinButton.setText("🎡  Spinning...");

        spinWheel.spin();

        if (taskManager != null) {
            taskManager.completeSpinTask();
        }

        // Calculate reward after spin completes
        uiHandler.postDelayed(() -> {
            calculateAndGrantReward();
            isSpinning = false;
            updateUI();
        }, 3500);
    }

    private void calculateAndGrantReward() {
        // Base reward from wheel
        int rewardIndex = random.nextInt(SPIN_REWARDS.length);
        double baseReward = SPIN_REWARDS[rewardIndex];

        // Apply multipliers
        float streakMultiplier = getStreakMultiplier();
        float tierMultiplier = getTierMultiplier();
        float luckyMultiplier = getLuckyMultiplier();

        double finalReward = baseReward * streakMultiplier * tierMultiplier * luckyMultiplier;

        // Check for jackpot
        boolean wonJackpot = checkJackpot();
        if (wonJackpot) {
            int spinsToday = prefs.getInt("freeSpinsUsed", 0) + prefs.getInt("adSpinsUsed", 0);
            finalReward = JACKPOT_REWARD + (spinsToday * 5);
        }

        // Save last win
        prefs.edit().putFloat("lastSpinWin", (float) finalReward).apply();

        // Grant reward to user
        grantRewardToUser(finalReward);

        // Show result
        showSpinResult(baseReward, finalReward, luckyMultiplier, wonJackpot);
    }

    private void grantRewardToUser(double reward) {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

            if (walletManager != null) {
                // Use WalletManager to atomically add tokens and record transaction
                walletManager.addTokens(reward, "spin", "Wheel spin reward");
                return;
            }

            // Fallback: direct firebase updates (kept for backward compatibility)
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users").child(userId);

            // FIXED: Use 'totalcoins' (lowercase) to match rest of app
            userRef.child("totalcoins").get().addOnSuccessListener(snapshot -> {
                double current = 0;
                if (snapshot.exists() && snapshot.getValue() != null) {
                    Object val = snapshot.getValue();
                    if (val instanceof Number) {
                        current = ((Number) val).doubleValue();
                    } else {
                        try { current = Double.parseDouble(String.valueOf(val)); } catch (Exception ignored) {}
                    }
                }
                userRef.child("totalcoins").setValue(current + reward);
            });

            // Track spin stats
            userRef.child("spinStats").child("totalWon").get().addOnSuccessListener(snapshot -> {
                double totalWon = 0;
                if (snapshot.exists() && snapshot.getValue() != null) {
                    Object val = snapshot.getValue();
                    if (val instanceof Number) {
                        totalWon = ((Number) val).doubleValue();
                    } else {
                        try { totalWon = Double.parseDouble(String.valueOf(val)); } catch (Exception ignored) {}
                    }
                }
                userRef.child("spinStats").child("totalWon").setValue(totalWon + reward);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error granting reward", e);
        }
    }

    private void showSpinResult(double baseReward, double finalReward, float luckyMultiplier, boolean wonJackpot) {
        StringBuilder message = new StringBuilder();

        if (wonJackpot) {
            message.append("JACKPOT! ");
        }

        message.append(String.format(Locale.US, "You won %.2f LYX!", finalReward));

        if (luckyMultiplier > 1.0f) {
            message.append(String.format(Locale.US, "\nLucky %.1fx multiplier!", luckyMultiplier));
        }

        if (wonJackpot) {
            // Show special jackpot dialog
            new AlertDialog.Builder(this)
                    .setTitle("JACKPOT WINNER!")
                    .setMessage(message.toString())
                    .setPositiveButton("Awesome!", null)
                    .show();
        } else {
            ToastUtils.showInfo(this, message.toString());
        }

        updateJackpotUI();
    }

    private void resetButton() {
        spinButton.setEnabled(true);
        updateUI();
    }

    private void updateUI() {
        if (isSpinning) return;

        resetDailySpinsIfNeeded();

        int freeRemaining = getFreeSpinsRemaining();
        int adRemaining = getAdSpinsRemaining();
        int total = freeRemaining + adRemaining;
        boolean spinPending = prefs.getBoolean("spinAvailable", false);
        boolean isFirstEverSpin = walletManager != null && walletManager.isFirstSpinFree() && prefs.getInt("totalLifetimeSpins", 0) == 0;

        if (spinCountText != null) {
            spinCountText.setText(total + "/" + TOTAL_SPINS_PER_DAY + " spins");
        }

        // Update reward info text
        if (rewardInfoText != null) {
            if (isFirstEverSpin) {
                rewardInfoText.setText("Your first spin is FREE!");
            } else if (freeRemaining > 0) {
                rewardInfoText.setText("Free spin available!");
            } else if (spinPending) {
                rewardInfoText.setText("Spin now to win!");
            } else if (adRemaining > 0) {
                rewardInfoText.setText("Watch ad to earn spin");
            } else {
                rewardInfoText.setText("Come back tomorrow!");
            }
        }

        // Update hint text
        if (spinHintText != null) {
            if (isFirstEverSpin) {
                spinHintText.setText("🎁 First spin is FREE!");
                spinHintText.setVisibility(View.VISIBLE);
            } else if (freeRemaining > 0) {
                spinHintText.setText("Daily free spin ready");
                spinHintText.setVisibility(View.VISIBLE);
            } else if (spinPending) {
                spinHintText.setText("Tap to spin the wheel");
                spinHintText.setVisibility(View.VISIBLE);
            } else if (adRemaining > 0) {
                spinHintText.setText("Watch a short ad to spin");
                spinHintText.setVisibility(View.VISIBLE);
            } else {
                spinHintText.setText("Daily limit reached");
                spinHintText.setVisibility(View.VISIBLE);
            }
        }

        // Update last win display
        if (lastWinText != null) {
            float lastWin = prefs.getFloat("lastSpinWin", 0f);
            if (lastWin > 0) {
                lastWinText.setText(String.format(Locale.US, "Last: %.1f LYX", lastWin));
                lastWinText.setVisibility(View.VISIBLE);
            } else {
                lastWinText.setVisibility(View.GONE);
            }
        }

        // Update button state
        if (spinPending) {
            spinButton.setText("🎡  SPIN NOW");
            spinButton.setEnabled(true);
            stopCooldownTimer();
        } else if (freeRemaining > 0) {
            spinButton.setText("🎁  FREE SPIN");
            spinButton.setEnabled(true);
            stopCooldownTimer();
        } else if (adRemaining > 0) {
            if (isAdCooldownActive()) {
                startCooldownTimer();
            } else {
                spinButton.setText("▶  WATCH AD TO SPIN");
                spinButton.setEnabled(true); // Always enable - dialog will handle ad state
                stopCooldownTimer();
            }
        } else {
            spinButton.setText("🔒  Come Back Tomorrow");
            spinButton.setEnabled(false);
            stopCooldownTimer();
        }

        // Update new UI elements
        updateStreakUI();
        updateTierUI();
        updateJackpotUI();
    }

    private void startCooldownTimer() {
        long remaining = getRemainingCooldown();
        if (remaining <= 0) {
            updateUI();
            return;
        }

        stopCooldownTimer();

        cooldownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millis) {
                int mins = (int) (millis / 60000);
                int secs = (int) ((millis % 60000) / 1000);
                spinButton.setText(String.format(Locale.US, "⏱  Wait %d:%02d", mins, secs));
                spinButton.setEnabled(false);
            }

            @Override
            public void onFinish() {
                updateUI();
                ToastUtils.showInfo(spinActivity.this, "Ready to spin!");
                preloadAd();
            }
        }.start();
    }

    private void stopCooldownTimer() {
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
            cooldownTimer = null;
        }
    }

    private void showCooldownMessage() {
        long remaining = getRemainingCooldown();
        if (remaining > 0) {
            int mins = (int) (remaining / 60000);
            int secs = (int) ((remaining % 60000) / 1000);
            ToastUtils.showInfo(this, String.format(Locale.US, "Available in %d:%02d", mins, secs));
        }
    }

    private long getRemainingCooldown() {
        long lastAd = prefs.getLong("lastSpinAdTime", 0);
        return Math.max(0, SPIN_AD_COOLDOWN_MS - (System.currentTimeMillis() - lastAd));
    }

    private int getFreeSpinsRemaining() {
        int localFreeUsed = prefs.getInt("freeSpinsUsed", 0);
        int baseRemaining = Math.max(0, FREE_SPINS_PER_DAY - localFreeUsed);
        // If WalletManager marks a global first-spin-free (never used), grant one additional free
        boolean globalFirstFreeAvailable = walletManager != null && walletManager.isFirstSpinFree() && prefs.getInt("totalLifetimeSpins", 0) == 0;
        return baseRemaining + (globalFirstFreeAvailable ? 1 : 0);
    }

    private int getAdSpinsRemaining() {
        return Math.max(0, AD_SPINS_PER_DAY - prefs.getInt("adSpinsUsed", 0));
    }

    private boolean isAdCooldownActive() {
        if (prefs.getInt("adSpinsUsed", 0) == 0) return false;
        return getRemainingCooldown() > 0;
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetDailySpinsIfNeeded();
        updateUI();
        if (isAdCooldownActive() && getAdSpinsRemaining() > 0) {
            startCooldownTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCooldownTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCooldownTimer();
    }
}
