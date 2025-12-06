package network.lynx.app;
import java.util.Random;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class spinActivity extends AppCompatActivity {
    private static final String TAG = "SpinActivity";
    ImageView back;
    private SharedPreferences prefs;
    private SpinWheelView spinWheel;
    private Button spinButton;
    private TextView spinCountText;
    private static final int MAX_SPINS_PER_DAY = 5;
    private static final long SPIN_INTERVAL_MS = (30 + new Random().nextInt(60)) * 1000; // 30-90s
    private AdManager adManager;
    private boolean isSpinning = false;

    // NEW: TaskManager integration
    private TaskManager taskManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_spin);

        initializeViews();
        initializePreferences();
        initializeTaskManager();

        adManager = AdManager.getInstance();

        setupClickListeners();
        updateSpinCountUI();
        loadAdIfNeeded();
    }

    private void initializeViews() {
        spinWheel = findViewById(R.id.spinnerView);
        spinButton = findViewById(R.id.spinBtn);
        back = findViewById(R.id.backNav);
        spinCountText = findViewById(R.id.spinCountText);
    }

    private void initializePreferences() {
        prefs = getSharedPreferences("spinPrefs", MODE_PRIVATE);
    }

    // NEW: Initialize TaskManager
    private void initializeTaskManager() {
        try {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (userId != null) {
                taskManager = new TaskManager(this, userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TaskManager", e);
        }
    }

    private void loadAdIfNeeded() {
        if (!adManager.isAdReady(AdManager.AD_UNIT_SPIN) && !adManager.isLoading(AdManager.AD_UNIT_SPIN)) {
            adManager.loadRewardedAd(this, AdManager.AD_UNIT_SPIN, new AdManager.AdLoadCallback() {
                @Override
                public void onAdLoaded() {
                    Log.d(TAG, "Spin ad loaded successfully");
                }

                @Override
                public void onAdLoadFailed(String error) {
                    Log.e(TAG, "Spin ad load failed: " + error);
                }
            });
        }
    }

    private void setupClickListeners() {
        back.setOnClickListener(view -> finish());

        spinButton.setOnClickListener(v -> {
            if (isSpinning) {
                ToastUtils.showInfo(spinActivity.this, "Please wait for current spin to complete");
                return;
            }

            if (!canSpin()) return;

            boolean spinAvailable = prefs.getBoolean("spinAvailable", false);
            if (spinAvailable) {
                performSpin();
            } else {
                // Show the consent dialog BEFORE showing the ad
                AdConsentManager.showSpinConsentDialog(this, new AdConsentManager.ConsentCallback() {
                    @Override
                    public void onConsentGiven() {
                        showAdForSpin();
                    }

                    @Override
                    public void onConsentDenied() {
                        ToastUtils.showInfo(spinActivity.this, "You need to watch the ad to earn a spin.");
                    }
                });
            }
        });
    }

    private boolean canSpin() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String lastSpinDate = prefs.getString("lastSpinDate", "");
        int spinCount = prefs.getInt("spinCount", 0);
        long lastSpinTime = prefs.getLong("lastSpinTime", 0);
        long currentTime = System.currentTimeMillis();

        if (!lastSpinDate.equals(today)) {
            spinCount = 0;
            prefs.edit()
                    .putString("lastSpinDate", today)
                    .putInt("spinCount", 0)
                    .apply();
            updateSpinCountUI();
        }

        if (spinCount >= MAX_SPINS_PER_DAY) {
            ToastUtils.showInfo(spinActivity.this, "You've used all 5 spins today! Come back tomorrow.");
            return false;
        }

        if (currentTime - lastSpinTime < SPIN_INTERVAL_MS) {
            long secondsLeft = (SPIN_INTERVAL_MS - (currentTime - lastSpinTime)) / 1000;
            ToastUtils.showInfo(this, "Wait " + secondsLeft + " seconds before spinning again.");
            return false;
        }

        return true;
    }

    private void showAdForSpin() {
        // Record feature usage for smart preloading
        adManager.recordFeatureUsage(this, AdManager.AD_UNIT_SPIN);

        // showRewardedAd will automatically load if not ready
        spinButton.setText("Loading Ad...");
        spinButton.setEnabled(false);
        showRewardedAd();
    }


    private void showRewardedAd() {
        spinButton.setText("Loading Ad...");
        spinButton.setEnabled(false);

        adManager.showRewardedAd(this, AdManager.AD_UNIT_SPIN, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {
                Log.d(TAG, "Spin ad showed");
            }

            @Override
            public void onAdShowFailed(String error) {
                ToastUtils.showInfo(spinActivity.this, "Ad failed to show. Please try again.");
                resetSpinButton();
            }

            @Override
            public void onAdDismissed() {
                ToastUtils.showInfo(spinActivity.this, "Ad was not completed. No spin earned.");
                resetSpinButton();
            }

            @Override
            public void onAdNotAvailable() {
                ToastUtils.showInfo(spinActivity.this, "Ad not available. Please try again later.");
                resetSpinButton();
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                grantSpinReward();
            }
        });
    }

    private void grantSpinReward() {
        Log.d(TAG, "Granting spin reward");
        prefs.edit().putBoolean("spinAvailable", true).apply();
        ToastUtils.showInfo(this, "Ad completed! You earned a spin. Tap to spin now!");
        spinButton.setText("SPIN NOW!");
        spinButton.setEnabled(true);
    }

    private void resetSpinButton() {
        boolean spinAvailable = prefs.getBoolean("spinAvailable", false);
        if (spinAvailable) {
            spinButton.setText("SPIN NOW!");
        } else {
            spinButton.setText("WATCH AD TO SPIN");
        }
        spinButton.setEnabled(true);
    }

    // MODIFIED: Enhanced to integrate with TaskManager
    private void performSpin() {
        if (isSpinning) return;

        isSpinning = true;
        spinButton.setEnabled(false);
        spinWheel.spin();

        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        int currentSpinCount = prefs.getInt("spinCount", 0);

        prefs.edit()
                .putInt("spinCount", currentSpinCount + 1)
                .putBoolean("spinAvailable", false)
                .putLong("lastSpinTime", System.currentTimeMillis())
                .putString("lastSpinDate", today)
                .apply();

        updateSpinCountUI();

        // NEW: Notify TaskManager about spin completion
        if (taskManager != null) {
            taskManager.completeSpinTask();
        }

        spinButton.postDelayed(() -> {
            isSpinning = false;
            spinButton.setEnabled(true);
            spinButton.setText("WATCH AD TO SPIN");
        }, 3000);

        ToastUtils.showInfo(this, "Spin completed! Watch another ad for next spin.");
    }

    private void updateSpinCountUI() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String lastSpinDate = prefs.getString("lastSpinDate", "");
        int spinCount = prefs.getInt("spinCount", 0);

        if (!lastSpinDate.equals(today)) {
            spinCount = 0;
            prefs.edit()
                    .putString("lastSpinDate", today)
                    .putInt("spinCount", 0)
                    .apply();
        }

        int spinsLeft = MAX_SPINS_PER_DAY - spinCount;
        spinCountText.setText("Spins left today: " + spinsLeft + "/" + MAX_SPINS_PER_DAY);

        boolean spinAvailable = prefs.getBoolean("spinAvailable", false);
        if (spinAvailable) {
            spinButton.setText("SPIN NOW!");
        } else if (spinsLeft > 0) {
            spinButton.setText("WATCH AD TO SPIN");
        } else {
            spinButton.setText("NO SPINS LEFT TODAY");
            spinButton.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSpinCountUI();
        // Don't load ads on resume - only when needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handled by AdManager
    }
}
