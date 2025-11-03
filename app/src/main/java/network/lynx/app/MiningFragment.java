package network.lynx.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MiningFragment extends Fragment implements BoostManager.BoostChangeListener {
    private static final String TAG = "MiningFragment";

    // UI Components
    private TextView counterTextView, miningTimerTextView, miningRate, miningPer;
    private MiningBlobView startButton;
    private CardView invite, boostCard;
    private Handler handler;

    // Firebase and Data
    private DatabaseReference miningRef;
    private boolean isMiningActive = false;
    private long startTime = 0;
    private static final long MINING_DURATION = 24 * 60 * 60 * 1000; // 24 hours
    private SharedPreferences tokenPrefs;
    private String prefsName;
    private String referralCode;
    private MiningViewModel miningViewModel;
    private double initialTotalCoins = 0.0;

    // Notification
    private static final String CHANNEL_ID = "mining_channel";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;

    // Managers
    private AdManager adManager;
    private BoostManager boostManager;
    private TaskManager taskManager; // NEW: TaskManager integration

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mining, container, false);

        initializeViews(view);
        initializeManagers();
        initializePreferences();
        setupClickListeners();
        setupViewModel();

        // Load cached data and fetch latest from Firebase
        loadCachedData();
        fetchUserData();
        createNotificationChannel();
        checkAndRequestNotificationPermission();

        // Smart preload ads
        smartPreloadAds();

        return view;
    }

    // Helper method to safely get context
    private Context getSafeContext() {
        if (isAdded() && getContext() != null) {
            return getContext();
        }
        return null;
    }

    // Helper method to safely access SharedPreferences
    private SharedPreferences getSafeSharedPreferences() {
        Context context = getSafeContext();
        if (context != null && prefsName != null) {
            return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        }
        return null;
    }

    private void initializeViews(View view) {
        counterTextView = view.findViewById(R.id.balance);
        miningTimerTextView = view.findViewById(R.id.timer);
        startButton = view.findViewById(R.id.miningblow);
        miningRate = view.findViewById(R.id.miningrate);
        miningPer = view.findViewById(R.id.miningPer);
//        invite = view.findViewById(R.id.invite);
        boostCard = view.findViewById(R.id.boost);
        handler = new Handler(Looper.getMainLooper());
    }

    private void initializeManagers() {
        try {
            // Initialize AdManager
            adManager = AdManager.getInstance();

            // Initialize BoostManager
            Context context = getSafeContext();
            if (context != null) {
                boostManager = BoostManager.getInstance(context);
                boostManager.addBoostChangeListener(this);
                Log.d(TAG, "BoostManager initialized successfully");

                // NEW: Initialize TaskManager
                String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
                if (userID != null) {
                    taskManager = new TaskManager(context, userID);
                    Log.d(TAG, "TaskManager initialized successfully");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing managers", e);
        }
    }

    private void initializePreferences() {
        try {
            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            prefsName = "TokenPrefs_" + userID;
            Context context = getSafeContext();
            if (context != null) {
                tokenPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                miningRef = FirebaseDatabase.getInstance().getReference("users").child(userID).child("mining");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing preferences", e);
        }
    }

    private void smartPreloadAds() {
        try {
            Context context = getSafeContext();
            if (context != null && adManager != null) {
                // Smart preload based on user behavior
                adManager.smartPreloadAd(context, AdManager.AD_UNIT_MINING);
                adManager.smartPreloadAd(context, AdManager.AD_UNIT_BOOST);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error preloading ads", e);
        }
    }

    private void setupClickListeners() {
        if (invite != null) {
            invite.setOnClickListener(v -> shareAppInvite());
        }

        startButton.setOnClickListener(v -> {
            if (!isMiningActive) {
                showMiningConsentDialog();
            } else {
                Context context = getSafeContext();
                if (context != null) {
                    ToastUtils.showInfo(context, "Mining already started");
                }
            }
        });

        boostCard.setOnClickListener(v -> {
            if (isMiningActive) {
                showBoostConsentDialog();
            } else {
                Context context = getSafeContext();
                if (context != null) {
                    ToastUtils.showInfo(context, "Start mining first to boost speed");
                }
            }
        });
    }

    private void showMiningConsentDialog() {
        if (!isAdded()) return;
        try {
            Context context = getSafeContext();
            if (context != null && adManager != null) {
                // Record feature usage for smart preloading
                adManager.recordFeatureUsage(context, AdManager.AD_UNIT_MINING);

                AdConsentManager.showMiningConsentDialog(requireActivity(), new AdConsentManager.ConsentCallback() {
                    @Override
                    public void onConsentGiven() {
                        if (isAdded()) {
                            proceedWithMiningAd();
                        }
                    }

                    @Override
                    public void onConsentDenied() {
                        if (isAdded()) {
                            // Allow slower mining without ad
                            if (boostManager != null) {
                                boostManager.setAdWatched(false);
                            }
                            startMining();
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing mining consent dialog", e);
        }
    }

    private void showBoostConsentDialog() {
        if (!isAdded()) return;

        if (boostManager != null && boostManager.isTemporaryBoostActive()) {
            Context context = getSafeContext();
            if (context != null) {
                long timeRemaining = boostManager.getTemporaryBoostTimeRemaining();
                ToastUtils.showInfo(context, String.format("Boost already active! %d minutes remaining", timeRemaining / 60000));
            }
            return;
        }

        try {
            Context context = getSafeContext();
            if (context != null && adManager != null) {
                // Record feature usage for smart preloading
                adManager.recordFeatureUsage(context, AdManager.AD_UNIT_BOOST);

                AdConsentManager.showBoostConsentDialog(requireActivity(), new AdConsentManager.ConsentCallback() {
                    @Override
                    public void onConsentGiven() {
                        if (isAdded()) {
                            proceedWithBoostAd();
                        }
                    }

                    @Override
                    public void onConsentDenied() {
                        if (isAdded()) {
                            Context ctx = getSafeContext();
                            if (ctx != null) {
                                ToastUtils.showInfo(ctx, "Boost cancelled");
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing boost consent dialog", e);
        }
    }

    private void proceedWithMiningAd() {
        if (!isAdded() || adManager == null) return;

        if (adManager.isAdReady(AdManager.AD_UNIT_MINING)) {
            showMiningRewardedAd();
        } else {
            startButton.setEnabled(false);
            Context context = getSafeContext();
            if (context != null) {
                adManager.loadRewardedAd(context, AdManager.AD_UNIT_MINING, new AdManager.AdLoadCallback() {
                    @Override
                    public void onAdLoaded() {
                        if (isAdded()) {
                            showMiningRewardedAd();
                        }
                    }

                    @Override
                    public void onAdLoadFailed(String error) {
                        if (isAdded()) {
                            Context ctx = getSafeContext();
                            if (ctx != null) {
                                ToastUtils.showInfo(ctx, "Ad not available. Starting slower mining.");
                            }
                            if (boostManager != null) {
                                boostManager.setAdWatched(false);
                            }
                            startMining();
                            startButton.setEnabled(true);
                        }
                    }
                });
            }
        }
    }

    private void proceedWithBoostAd() {
        if (!isAdded() || adManager == null) return;

        if (adManager.isAdReady(AdManager.AD_UNIT_BOOST)) {
            showBoostRewardedAd();
        } else {
            boostCard.setEnabled(false);
            Context context = getSafeContext();
            if (context != null) {
                adManager.loadRewardedAd(context, AdManager.AD_UNIT_BOOST, new AdManager.AdLoadCallback() {
                    @Override
                    public void onAdLoaded() {
                        if (isAdded()) {
                            showBoostRewardedAd();
                        }
                    }

                    @Override
                    public void onAdLoadFailed(String error) {
                        if (isAdded()) {
                            Context ctx = getSafeContext();
                            if (ctx != null) {
                                ToastUtils.showInfo(ctx, "Ad not available. Try again later.");
                            }
                            boostCard.setEnabled(true);
                        }
                    }
                });
            }
        }
    }

    private void showMiningRewardedAd() {
        if (!isAdded() || adManager == null) return;

        startButton.setEnabled(false);
        adManager.showRewardedAd(requireActivity(), AdManager.AD_UNIT_MINING, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {
                Log.d(TAG, "Mining ad showed");
            }

            @Override
            public void onAdShowFailed(String error) {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad failed to show. Starting slower mining.");
                    }
                    if (boostManager != null) {
                        boostManager.setAdWatched(false);
                    }
                    startMining();
                    startButton.setEnabled(true);
                }
            }

            @Override
            public void onAdDismissed() {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad not completed. Starting slower mining.");
                    }
                    if (boostManager != null) {
                        boostManager.setAdWatched(false);
                    }
                    startMining();
                    startButton.setEnabled(true);
                }
            }

            @Override
            public void onAdNotAvailable() {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad not available. Starting slower mining.");
                    }
                    if (boostManager != null) {
                        boostManager.setAdWatched(false);
                    }
                    startMining();
                    startButton.setEnabled(true);
                }
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                if (isAdded()) {
                    // Set ad watched in BoostManager
                    if (boostManager != null) {
                        boostManager.setAdWatched(true);
                    }

                    // NEW: Notify TaskManager that mining ad was watched
                    if (taskManager != null) {
                        taskManager.completeMiningAdTask();
                        Log.d(TAG, "Mining ad watched - synced with TaskManager");
                    }

                    // NEW: Notify BoostActivity to refresh
                    notifyBoostActivityRefresh();

                    startMining();
                    startButton.setEnabled(true);
                }
            }
        });
    }

    private void showBoostRewardedAd() {
        if (!isAdded() || adManager == null) return;

        boostCard.setEnabled(false);
        adManager.showRewardedAd(requireActivity(), AdManager.AD_UNIT_BOOST, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {
                Log.d(TAG, "Boost ad showed");
            }

            @Override
            public void onAdShowFailed(String error) {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad failed to show. Try again later.");
                    }
                    boostCard.setEnabled(true);
                }
            }

            @Override
            public void onAdDismissed() {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad not completed. No boost applied.");
                    }
                    boostCard.setEnabled(true);
                }
            }

            @Override
            public void onAdNotAvailable() {
                if (isAdded()) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Ad not available. Try again later.");
                    }
                    boostCard.setEnabled(true);
                }
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                if (isAdded()) {
                    activateBoost();
                    boostCard.setEnabled(true);
                }
            }
        });
    }

    private void setupViewModel() {
        if (!isAdded()) return;
        try {
            miningViewModel = new ViewModelProvider(this).get(MiningViewModel.class);

            miningViewModel.getTotalCoins().observe(getViewLifecycleOwner(), balance -> {
                if (balance != null && isAdded()) {
                    counterTextView.setText(formatLargeNumber(balance));
                    SharedPreferences prefs = getSafeSharedPreferences();
                    if (prefs != null) {
                        prefs.edit().putFloat("totalCoins", balance.floatValue()).apply();
                    }
                }
            });

            miningViewModel.getMiningRate().observe(getViewLifecycleOwner(), rate -> {
                if (rate != null && isAdded()) {
                    updateMiningRateDisplay();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up ViewModel", e);
        }
    }

    private void shareAppInvite() {
        if (!isAdded() || referralCode == null) return;
        try {
            String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + referralCode;
            boolean hasPermanentBoost = boostManager != null && boostManager.hasPermanentBoost();
            String boostMessage = hasPermanentBoost ? "\n🚀 I have a permanent mining boost - join now!" : "";
            String message = "Join Lynx Network and earn rewards! Use my referral code: "
                    + referralCode + "\nDownload here: " + inviteLink + boostMessage;

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, message);
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, "Share via");
            startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error sharing app invite", e);
        }
    }

    private void loadCachedData() {
        if (!isAdded()) return;
        try {
            SharedPreferences prefs = getSafeSharedPreferences();
            if (prefs == null) return;

            float cachedTotalCoins = prefs.getFloat("totalCoins", 0.0f);
            boolean cachedIsMiningActive = prefs.getBoolean("isMiningActive", false);
            long cachedStartTime = prefs.getLong("startTime", 0);

            counterTextView.setText(formatLargeNumber(cachedTotalCoins));

            if (cachedIsMiningActive && cachedStartTime > 0) {
                long elapsed = System.currentTimeMillis() - cachedStartTime;
                if (elapsed < MINING_DURATION) {
                    double tokens = calculateTokens(elapsed);
                    counterTextView.setText(formatLargeNumber(cachedTotalCoins + tokens));
                    miningTimerTextView.setText(formatTime(MINING_DURATION - elapsed));
                    float progress = ((float) elapsed / MINING_DURATION) * 100;
                    miningPer.setText(String.format("%.2f%%", progress));
                } else {
                    miningTimerTextView.setText("00:00:00");
                    miningPer.setText("100.00%");
                }
            } else {
                miningTimerTextView.setText("00:00:00");
                miningPer.setText("Start");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached data", e);
        }
    }

    private void fetchUserData() {
        if (!isAdded()) return;
        try {
            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Check if Fragment is still attached before proceeding
                    if (!isAdded() || getSafeContext() == null) {
                        Log.w(TAG, "Fragment not attached, skipping UI update");
                        return;
                    }

                    try {
                        Double fetchedTotalCoins = snapshot.child("totalcoins").getValue(Double.class);
                        referralCode = snapshot.child("referralCode").getValue(String.class);

                        if (fetchedTotalCoins == null) fetchedTotalCoins = 0.0;
                        initialTotalCoins = fetchedTotalCoins;

                        SharedPreferences prefs = getSafeSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putFloat("totalCoins", fetchedTotalCoins.floatValue()).apply();
                        }

                        Long firebaseStartTime = snapshot.child("mining/startTime").getValue(Long.class);
                        Boolean miningActive = snapshot.child("mining/isMiningActive").getValue(Boolean.class);

                        if (miningActive != null && miningActive && firebaseStartTime != null && firebaseStartTime > 0) {
                            startTime = firebaseStartTime;
                            long elapsed = System.currentTimeMillis() - startTime;

                            if (elapsed >= MINING_DURATION) {
                                if (isAdded()) {
                                    isMiningActive = false;
                                    double tokens = calculateTokens(MINING_DURATION);
                                    double finalTotal = initialTotalCoins + tokens;
                                    counterTextView.setText(formatLargeNumber(finalTotal));
                                    miningTimerTextView.setText("00:00:00");
                                    miningPer.setText("100.00%");
                                    saveMinedTokens(tokens);
                                    if (miningRef != null) {
                                        miningRef.child("isMiningActive").setValue(false);
                                        miningRef.child("startTime").setValue(0);
                                    }
                                }
                            } else {
                                isMiningActive = true;
                                if (isAdded()) {
                                    startUpdatingUI();
                                }
                            }
                        } else {
                            if (isAdded()) {
                                isMiningActive = false;
                                miningTimerTextView.setText("00:00:00");
                                miningPer.setText("Start");
                                counterTextView.setText(formatLargeNumber(initialTotalCoins));
                            }
                        }

                        // Safe SharedPreferences update
                        SharedPreferences prefs2 = getSafeSharedPreferences();
                        if (prefs2 != null) {
                            prefs2.edit()
                                    .putBoolean("isMiningActive", isMiningActive)
                                    .putLong("startTime", startTime)
                                    .apply();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing user data", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Failed to fetch user data", error.toException());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error fetching user data", e);
        }
    }

    private void startUpdatingUI() {
        if (!isAdded() || handler == null) return;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;

                if (isMiningActive && startTime > 0) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    long remaining = MINING_DURATION - elapsed;

                    if (remaining <= 0) {
                        isMiningActive = false;
                        double tokens = calculateTokens(MINING_DURATION);
                        double finalTotal = initialTotalCoins + tokens;
                        counterTextView.setText(formatLargeNumber(finalTotal));
                        miningTimerTextView.setText("00:00:00");
                        miningPer.setText("100.00%");
                        saveMinedTokens(tokens);

                        if (miningRef != null) {
                            miningRef.child("isMiningActive").setValue(false);
                            miningRef.child("startTime").setValue(0);
                        }
                        showMiningCompleteNotification();
                    } else {
                        double tokens = calculateTokens(elapsed);
                        double currentTotal = initialTotalCoins + tokens;
                        counterTextView.setText(formatLargeNumber(currentTotal));
                        miningTimerTextView.setText(formatTime(remaining));
                        float progress = ((float) elapsed / MINING_DURATION) * 100;
                        miningPer.setText(String.format("%.2f%%", progress));

                        if (handler != null) {
                            handler.postDelayed(this, 1000);
                        }
                    }
                }
            }
        }, 1000);
    }

    private void startMining() {
        if (!isAdded()) return;

        startButton.setEnabled(false);
        try {
            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Always check Fragment state first
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment detached during mining start");
                        return;
                    }

                    try {
                        initialTotalCoins = snapshot.getValue(Double.class) != null ? snapshot.getValue(Double.class) : 0.0;
                        startTime = System.currentTimeMillis();

                        if (miningRef != null) {
                            miningRef.child("startTime").setValue(startTime);
                            miningRef.child("isMiningActive").setValue(true)
                                    .addOnSuccessListener(aVoid -> {
                                        // Check Fragment state before UI updates
                                        if (isAdded()) {
                                            isMiningActive = true;
                                            startUpdatingUI();
                                            startButton.setEnabled(true);

                                            // Safe context access for WorkManager
                                            Context context = getSafeContext();
                                            if (context != null) {
                                                Data inputData = new Data.Builder().putLong("startTime", startTime).build();
                                                OneTimeWorkRequest miningWorkRequest = new OneTimeWorkRequest.Builder(MiningWorker.class)
                                                        .setInitialDelay(24, TimeUnit.HOURS)
                                                        .setInputData(inputData)
                                                        .build();
                                                WorkManager.getInstance(context).enqueue(miningWorkRequest);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (isAdded()) {
                                            startButton.setEnabled(true);
                                            Log.e("Mining", "Failed to start mining", e);
                                        }
                                    });
                        }
                    } catch (Exception e) {
                        if (isAdded()) {
                            startButton.setEnabled(true);
                        }
                        Log.e(TAG, "Error in mining start", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (isAdded()) {
                        startButton.setEnabled(true);
                    }
                    Log.e("Firebase", "Failed to fetch totalcoins", error.toException());
                }
            });
        } catch (Exception e) {
            startButton.setEnabled(true);
            Log.e(TAG, "Error starting mining", e);
        }
    }

    // Updated calculateTokens method using BoostManager
    private double calculateTokens(long elapsedMillis) {
        try {
            if (boostManager != null) {
                // Use BoostManager's calculation method for consistency
                return boostManager.calculateMiningAmount(elapsedMillis) * getReferralBonusMultiplier();
            } else {
                // Fallback calculation if BoostManager is not available
                float baseRate = 0.00125f; // Base rate per second (matches BoostManager)
                double tokens = new BigDecimal(elapsedMillis)
                        .divide(new BigDecimal(1000), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(baseRate))
                        .multiply(new BigDecimal(getReferralBonusMultiplier()))
                        .doubleValue();
                return tokens;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating tokens", e);
            return 0.0;
        }
    }

    private String formatTime(long millis) {
        try {
            long seconds = millis / 1000;
            return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time", e);
            return "00:00:00";
        }
    }

    private void saveMinedTokens(double minedTokens) {
        if (!isAdded()) return;
        try {
            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Check Fragment state
                    if (!isAdded()) {
                        Log.w(TAG, "Fragment detached during token save");
                        return;
                    }

                    try {
                        Double currentTotal = snapshot.getValue(Double.class);
                        if (currentTotal == null) currentTotal = 0.0;
                        double updatedTotal = currentTotal + minedTokens;

                        userRef.child("totalcoins").setValue(updatedTotal)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("Mining", "Mined tokens updated successfully");
                                    // Safe SharedPreferences access
                                    SharedPreferences prefs = getSafeSharedPreferences();
                                    if (prefs != null) {
                                        prefs.edit().putFloat("miningTokens", 0.0f).apply();
                                    }
                                    // Distribute commission to referrers
                                    ReferralCommissionManager.distributeMiningCommission(userID, minedTokens);
                                })
                                .addOnFailureListener(e -> Log.e("Mining", "Failed to update mined tokens", e));
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving mined tokens", e);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Error fetching total coins", error.toException());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in saveMinedTokens", e);
        }
    }

    private float getReferralBonusMultiplier() {
        try {
            SharedPreferences prefs = getSafeSharedPreferences();
            if (prefs != null) {
                int referralCount = prefs.getInt("referralCount", 0);
                return referralCount <= 0 ? 1.0f : 1.0f + (referralCount * 0.10f);
            }
            return 1.0f;
        } catch (Exception e) {
            Log.e(TAG, "Error getting referral bonus", e);
            return 1.0f;
        }
    }

    private void activateBoost() {
        if (!isAdded()) return;
        try {
            if (boostManager != null) {
                // Activate 1-hour temporary boost
                long expirationTime = System.currentTimeMillis() + (60 * 60 * 1000); // 1 hour
                boostManager.activateTemporaryBoost(expirationTime);

                // NEW: Sync with TaskManager for temporary boost completion
                if (taskManager != null) {
                    taskManager.completeTemporaryBoostTask();
                    Log.d(TAG, "Temporary boost activated - synced with TaskManager");
                }

                // NEW: Notify BoostActivity to refresh
                notifyBoostActivityRefresh();

                Context context = getSafeContext();
                if (context != null) {
                    String message = boostManager.hasPermanentBoost() ?
                            "Mining speed boosted by 50% for 1 hour! (Stacks with permanent boost)" :
                            "Mining speed boosted by 50% for 1 hour!";
                    ToastUtils.showInfo(context, message);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error activating boost", e);
        }
    }

    // NEW: Method to notify BoostActivity of task completion
    private void notifyBoostActivityRefresh() {
        try {
            // Send a broadcast to refresh BoostActivity if it's open
            Context context = getSafeContext();
            if (context != null) {
                Intent refreshIntent = new Intent("REFRESH_BOOST_TASKS");
                context.sendBroadcast(refreshIntent);
                Log.d(TAG, "Sent refresh broadcast to BoostActivity");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending refresh broadcast", e);
        }
    }

    // Updated mining rate display to show per hour instead of per second
    private void updateMiningRateDisplay() {
        if (!isAdded()) return;
        try {
            if (boostManager != null) {
                // Use per-hour rate for display consistency with BoostActivity
                float ratePerHour = boostManager.getCurrentMiningRatePerHour();
                String indicators = boostManager.getBoostIndicators();

                // Display rate per hour with boost indicators
                if (!indicators.isEmpty()) {
                    miningRate.setText(String.format("%.2f LYX/h %s", ratePerHour, indicators));
                } else {
                    miningRate.setText(String.format("%.2f LYX/h", ratePerHour));
                }

                Log.d(TAG, "Mining rate updated: " + boostManager.getRateBreakdown());
            } else {
                // Fallback display - convert base rate to per hour
                float baseRatePerHour = 0.00125f * 3600f; // 4.5 LYX/hour
                miningRate.setText(String.format("%.2f LYX/h", baseRatePerHour));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating mining rate display", e);
            if (miningRate != null) {
                miningRate.setText("4.5000 LYX/h");
            }
        }
    }

    // BoostManager.BoostChangeListener implementation
    @Override
    public void onBoostStateChanged(float currentMiningRate, String boostInfo) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updateMiningRateDisplay();
                Log.d(TAG, "Boost state changed: " + boostInfo);
            });
        }
    }

    @Override
    public void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updateMiningRateDisplay();
                if (hasPermanentBoost) {
                    Context context = getSafeContext();
                    if (context != null) {
                        ToastUtils.showInfo(context, "Permanent boost is active! +" +
                                String.format("%.0f", (multiplier - 1) * 100) + "%");
                    }
                }
            });
        }
    }

    private void showMiningCompleteNotification() {
        if (!isAdded()) return;
        try {
            Context context = getSafeContext();
            if (context == null) return;

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
            boolean hasPermanentBoost = boostManager != null && boostManager.hasPermanentBoost();
            String notificationText = hasPermanentBoost ?
                    "🚀 Mining completed with permanent boost! Tap to collect your enhanced rewards!" :
                    "You've earned new LYX tokens. Tap to collect your rewards now!";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo)
                    .setLargeIcon(largeIcon)
                    .setContentTitle("🚀 Mining Completed!")
                    .setContentText(notificationText)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(notificationText + " 🎉"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setColor(ContextCompat.getColor(context, R.color.cardBackground))
                    .setContentIntent(pendingIntent)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(new long[]{0, 300, 200, 300})
                    .setLights(Color.YELLOW, 1000, 1000);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1001, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (!isAdded()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context context = getSafeContext();
                if (context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(),
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking notification permission", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            Context context = getSafeContext();
            if (context != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ToastUtils.showInfo(context, "Notification permission granted!");
                } else {
                    ToastUtils.showInfo(context, "Permission denied. Notifications won't work.");
                }
            }
        }
    }

    private void createNotificationChannel() {
        if (!isAdded()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = "Mining Notifications";
                String description = "Notifications for mining completion";
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);

                Context context = getSafeContext();
                if (context != null) {
                    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                    if (notificationManager != null) {
                        notificationManager.createNotificationChannel(channel);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification channel", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            // Clean up expired ads and smart preload
            if (adManager != null) {
                adManager.cleanupExpiredAds();
                smartPreloadAds();
            }

            // Check if mining is active and update UI
            if (isMiningActive && startTime > 0) {
                startUpdatingUI();
            }

            // Update mining rate display
            updateMiningRateDisplay();
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            // BUG FIX: Properly clean up handler
            if (handler != null) {
                try {
                    handler.removeCallbacksAndMessages(null);
                    Log.d(TAG, "Handler callbacks removed");
                } catch (Exception e) {
                    Log.w(TAG, "Error removing handler callbacks", e);
                }
            }
            // BUG FIX: Remove boost listener
            if (boostManager != null) {
                try {
                    boostManager.removeBoostChangeListener(this);
                    Log.d(TAG, "Boost change listener removed");
                } catch (Exception e) {
                    Log.w(TAG, "Error removing boost listener", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView", e);
        }
    }

    public static String formatLargeNumber(double value) {
        try {
            if (value < 1_000) return String.format(Locale.US, "%.2f", value);
            String[] units = {"", "K", "M", "B", "T", "P", "E"};
            int unitIndex = (int) (Math.log10(value) / 3);
            double shortValue = value / Math.pow(1000, unitIndex);
            return String.format(Locale.US, "%.2f%s", shortValue, units[unitIndex]);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting large number", e);
            return "0.00";
        }
    }
}
