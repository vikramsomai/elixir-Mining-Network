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
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MiningFragment extends Fragment implements BoostManager.BoostChangeListener, MiningSyncManager.MiningSyncListener, WalletManager.BalanceChangeListener {
    private static final String TAG = "MiningFragment";

    // UI Components
    private TextView counterTextView, miningTimerTextView, miningRate, miningPer;
    private TextView miningStatusText, miningSubtext;
    private View statusIndicator;
    private MiningBlobView startButton;
    private View invite;
    private com.google.android.material.card.MaterialCardView boostCard;
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

    // FIXED: Flag to track if authoritative Firebase data has been loaded
    private boolean isFirebaseDataLoaded = false;

    // FIXED: Flag to track if mining complete notification was already shown
    private boolean miningCompleteNotificationShown = false;

    // FIXED: Track last saved mining session to prevent duplicate saves
    private long lastSavedMiningSession = 0;

    // Notification
    private static final String CHANNEL_ID = "mining_channel";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;

    // Managers
    private AdManager adManager;
    private BoostManager boostManager;
    private TaskManager taskManager;
    private MiningSyncManager syncManager;

    // NEW: Engagement Managers
    private SecurityCircleManager securityCircleManager;
    private DailyMissionsManager dailyMissionsManager;
    private TeamMiningManager teamMiningManager;
    private MiningPhaseManager miningPhaseManager;
    private CountdownEventManager countdownEventManager;
    private WalletManager walletManager;

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
        // New Jarvis-style UI elements
        miningStatusText = view.findViewById(R.id.miningStatusText);
        miningSubtext = view.findViewById(R.id.miningSubtext);
        statusIndicator = view.findViewById(R.id.statusIndicator);
        // Previously invite was not initialized which caused click to do nothing. Initialize it here.
        invite = view.findViewById(R.id.invite);
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

                // Initialize TaskManager
                String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
                if (userID != null) {
                    taskManager = new TaskManager(context, userID);
                    Log.d(TAG, "TaskManager initialized successfully");
                }

                // NEW: Initialize MiningSyncManager for cross-device sync
                syncManager = MiningSyncManager.getInstance(context);

                // NEW: Initialize Engagement Managers
                securityCircleManager = SecurityCircleManager.getInstance(context);
                dailyMissionsManager = DailyMissionsManager.getInstance(context);
                teamMiningManager = TeamMiningManager.getInstance(context);
                miningPhaseManager = MiningPhaseManager.getInstance(context);
                countdownEventManager = CountdownEventManager.getInstance(context);
                walletManager = WalletManager.getInstance(context);

                // NEW: Register for balance change notifications from WalletManager
                // This ensures MiningFragment updates immediately when tokens are added
                // from spin wheel, reward zone, purchases, or any other source
                walletManager.addBalanceChangeListener(this);

                // Initialize daily missions
                dailyMissionsManager.initializeMissions();

                Log.d(TAG, "Engagement managers initialized successfully");
                syncManager.setListener(this);
                Log.d(TAG, "MiningSyncManager initialized successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing managers", e);
        }
    }

    private void initializePreferences() {
        try {
            String userID = null;
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            if (userID == null || userID.isEmpty()) {
                Log.e(TAG, "User not logged in - mining will not work");
                return;
            }

            prefsName = "TokenPrefs_" + userID;
            Context context = getSafeContext();
            if (context != null) {
                tokenPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                // CRITICAL: Initialize miningRef for Firebase operations
                miningRef = FirebaseDatabase.getInstance().getReference("users").child(userID).child("mining");
                Log.d(TAG, "Mining reference initialized for user: " + userID);
            } else {
                Log.e(TAG, "Context is null - cannot initialize preferences");
            }

            // Load cached referral code from centralized ReferralUtils so invite works without extra DB reads
            Context ctx = getSafeContext();
            if (ctx != null) {
                String cached = ReferralUtils.getCachedReferralCode(ctx, userID);
                if (cached != null && !cached.isEmpty()) {
                    referralCode = cached;
                }
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
            invite.setOnClickListener(v -> {
                Log.d(TAG, "Invite button clicked in MiningFragment");
                Context context = getSafeContext();
                if (context != null) {
                    // Ensure we have a referral code before sharing
                    if (referralCode == null || referralCode.isEmpty()) {
                        String userId = null;
                        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        }
                        if (userId != null) {
                            referralCode = ReferralUtils.generateReferralCode(userId);
                            ReferralUtils.saveProfileToPrefs(context, userId, null, null, referralCode);
                        }
                    }
                    ReferralUtils.shareReferral(context);
                } else {
                    Log.e(TAG, "Context is null, cannot share referral");
                }
            });
            invite.setClickable(true);
            invite.setFocusable(true);
            Log.d(TAG, "Invite button click listener set up successfully");
        } else {
            Log.w(TAG, "Invite CardView is null - cannot set click listener");
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

        // showRewardedAd will automatically load if not ready
        startButton.setEnabled(false);
        showMiningRewardedAd();
    }

    private void proceedWithBoostAd() {
        if (!isAdded() || adManager == null) return;

        // showRewardedAd will automatically load if not ready
        boostCard.setEnabled(false);
        showBoostRewardedAd();
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

            // FIXED: Don't directly set counterTextView here - this causes flickering
            // Instead, just update the cached value. The display is handled by loadCachedData/fetchUserData
            miningViewModel.getTotalCoins().observe(getViewLifecycleOwner(), balance -> {
                if (balance != null && isAdded()) {
                    // Only update if this is a higher value (Firebase is source of truth)
                    // Don't update display here to prevent flickering
                    SharedPreferences prefs = getSafeSharedPreferences();
                    if (prefs != null) {
                        float cached = prefs.getFloat("totalCoins", 0f);
                        if (balance > cached) {
                            prefs.edit().putFloat("totalCoins", balance.floatValue()).apply();
                            Log.d(TAG, "ViewModel updated cached balance: " + balance);
                        }
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

    private void loadCachedData() {
        if (!isAdded()) return;

        // FIXED: Don't overwrite if Firebase has already loaded authoritative data
        if (isFirebaseDataLoaded) {
            Log.d(TAG, "Skipping cache load - Firebase data already loaded");
            return;
        }

        try {
            SharedPreferences prefs = getSafeSharedPreferences();
            if (prefs == null) {
                Log.w(TAG, "SharedPreferences is null, skipping cache load");
                return;
            }

            // Load cached coins for immediate display (before Firebase returns)
            float cachedTotalCoins = prefs.getFloat("totalCoins", 0.0f);
            initialTotalCoins = cachedTotalCoins;

            // Load mining state from preferences
            boolean cachedIsMiningActive = prefs.getBoolean("isMiningActive", false);
            long cachedStartTime = prefs.getLong("startTime", 0);

            if (cachedIsMiningActive && cachedStartTime > 0) {
                isMiningActive = true;
                startTime = cachedStartTime;
            }

            // Update UI based on cached state
            if (isMiningActive && startTime > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < MINING_DURATION) {
                    double tokens = calculateTokens(elapsed);
                    updateBalanceDisplay(initialTotalCoins + tokens);
                    miningTimerTextView.setText(formatTime(MINING_DURATION - elapsed));
                    float progress = ((float) elapsed / MINING_DURATION) * 100;
                    miningPer.setText(String.format(Locale.US, "%.1f%%", progress));
                } else {
                    // Mining completed - but don't show 100%, show "Completed"
                    updateBalanceDisplay(initialTotalCoins);
                    miningTimerTextView.setText("00:00:00");
                    miningPer.setText("Tap to Start");
                    // Reset mining state locally - will be confirmed by Firebase
                    isMiningActive = false;
                }
            } else {
                updateBalanceDisplay(initialTotalCoins);
                miningTimerTextView.setText("00:00:00");
                miningPer.setText("Tap to Start");
            }

            Log.d(TAG, "Loaded cached data - Mining: " + isMiningActive + ", Coins: " + initialTotalCoins);

            // Update Jarvis-style status UI
            updateMiningStatusUI();
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached data", e);
        }
    }

    private void fetchUserData() {
        if (!isAdded()) return;
        try {
            // Verify user is logged in
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.e(TAG, "User not logged in, cannot fetch data");
                return;
            }

            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // FIXED: Only use direct Firebase call - SyncManager was causing issues
            // Fetch directly from Firebase to get authoritative data
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!isAdded() || getSafeContext() == null) {
                        Log.w(TAG, "Fragment not attached, skipping UI update");
                        return;
                    }

                    try {
                        // FIXED: Get totalcoins from Firebase - this is the authoritative source
                        Double fetchedTotalCoins = snapshot.child("totalcoins").getValue(Double.class);
                        if (fetchedTotalCoins == null) {
                            fetchedTotalCoins = 0.0;
                        }

                        // FIXED: Set flag that Firebase data is loaded
                        isFirebaseDataLoaded = true;
                        initialTotalCoins = fetchedTotalCoins;

                        // Get referral code
                        referralCode = snapshot.child("referralCode").getValue(String.class);

                        // Cache the total coins
                        SharedPreferences prefs = getSafeSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putFloat("totalCoins", fetchedTotalCoins.floatValue()).apply();
                        }

                        // Get mining state
                        Long firebaseStartTime = snapshot.child("mining/startTime").getValue(Long.class);
                        Boolean miningActive = snapshot.child("mining/isMiningActive").getValue(Boolean.class);

                        if (miningActive != null && miningActive && firebaseStartTime != null && firebaseStartTime > 0) {
                            startTime = firebaseStartTime;
                            long elapsed = System.currentTimeMillis() - startTime;

                            if (elapsed >= MINING_DURATION) {
                                // Mining completed - save tokens only once
                                isMiningActive = false;

                                // Check if we already saved this session
                                if (lastSavedMiningSession != firebaseStartTime) {
                                    lastSavedMiningSession = firebaseStartTime;
                                    double tokens = calculateTokens(MINING_DURATION);
                                    double finalTotal = initialTotalCoins + tokens;
                                    updateBalanceDisplay(finalTotal);
                                    saveMinedTokens(tokens);

                                    // Show notification only once
                                    if (!miningCompleteNotificationShown) {
                                        miningCompleteNotificationShown = true;
                                        showMiningCompleteNotification();
                                    }
                                } else {
                                    updateBalanceDisplay(initialTotalCoins);
                                }

                                miningTimerTextView.setText("00:00:00");
                                miningPer.setText("Tap to Start");

                                // Reset mining state in Firebase
                                if (miningRef != null) {
                                    miningRef.child("isMiningActive").setValue(false);
                                    miningRef.child("startTime").setValue(0);
                                }
                            } else {
                                // Mining in progress
                                isMiningActive = true;
                                miningCompleteNotificationShown = false; // Reset for next session
                                double currentTokens = calculateTokens(elapsed);
                                updateBalanceDisplay(initialTotalCoins + currentTokens);
                                startUpdatingUI();
                            }
                        } else {
                            // Not mining
                            isMiningActive = false;
                            miningTimerTextView.setText("00:00:00");
                            miningPer.setText("Tap to Start");
                            updateBalanceDisplay(initialTotalCoins);
                        }

                        // Safe SharedPreferences update
                        SharedPreferences prefs2 = getSafeSharedPreferences();
                        if (prefs2 != null) {
                            prefs2.edit()
                                    .putBoolean("isMiningActive", isMiningActive)
                                    .putLong("startTime", startTime)
                                    .apply();
                        }

                        Log.d(TAG, "Firebase data loaded - TotalCoins: " + initialTotalCoins + ", Mining: " + isMiningActive);
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

    // NEW: Fetch total coins directly for accurate display
    private void fetchTotalCoinsDirectly(String userID) {
        if (!isAdded() || userID == null) return;

        DatabaseReference coinsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userID).child("totalcoins");

        coinsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                try {
                    Double coins = snapshot.getValue(Double.class);
                    if (coins != null) {
                        initialTotalCoins = coins;

                        // Update display
                        if (isMiningActive && startTime > 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double currentTokens = calculateTokens(elapsed);
                            counterTextView.setText(formatLargeNumber(initialTotalCoins + currentTokens));
                        } else {
                            counterTextView.setText(formatLargeNumber(initialTotalCoins));
                        }

                        // Cache it
                        SharedPreferences prefs = getSafeSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putFloat("totalCoins", coins.floatValue()).apply();
                        }

                        Log.d(TAG, "Total coins fetched directly: " + coins);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching total coins", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch total coins", error.toException());
            }
        });
    }

    private void startUpdatingUI() {
        if (!isAdded() || handler == null) return;

        // Remove any pending callbacks to prevent duplicate updates
        handler.removeCallbacksAndMessages(null);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;

                if (isMiningActive && startTime > 0) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    long remaining = MINING_DURATION - elapsed;

                    if (remaining <= 0) {
                        // Mining completed
                        isMiningActive = false;

                        // Only save and notify if not already done for this session
                        if (lastSavedMiningSession != startTime) {
                            lastSavedMiningSession = startTime;
                            double tokens = calculateTokens(MINING_DURATION);
                            double finalTotal = initialTotalCoins + tokens;
                            updateBalanceDisplay(finalTotal);
                            saveMinedTokens(tokens);

                            // Show notification only once
                            if (!miningCompleteNotificationShown) {
                                miningCompleteNotificationShown = true;
                                showMiningCompleteNotification();
                            }
                        }

                        miningTimerTextView.setText("00:00:00");
                        miningPer.setText("Tap to Start");

                        if (miningRef != null) {
                            miningRef.child("isMiningActive").setValue(false);
                            miningRef.child("startTime").setValue(0);
                        }
                    } else {
                        double tokens = calculateTokens(elapsed);
                        double currentTotal = initialTotalCoins + tokens;
                        updateBalanceDisplay(currentTotal);
                        miningTimerTextView.setText(formatTime(remaining));
                        float progress = ((float) elapsed / MINING_DURATION) * 100;
                        miningPer.setText(String.format(Locale.US, "%.1f%%", progress));

                        // Update Jarvis-style status UI
                        updateMiningStatusUI();

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

        // Verify user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "Cannot start mining - user not logged in");
            startButton.setEnabled(true);
            Context ctx = getSafeContext();
            if (ctx != null) {
                ToastUtils.showError(ctx, "Please login to start mining");
            }
            return;
        }

        // Record mining session for streak tracking
        try {
            Context context = getSafeContext();
            if (context != null) {
                MiningStreakManager.getInstance(context).recordMiningSession();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not record mining streak", e);
        }

        try {
            String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Ensure miningRef is initialized
            if (miningRef == null) {
                miningRef = FirebaseDatabase.getInstance().getReference("users").child(userID).child("mining");
                Log.d(TAG, "miningRef initialized in startMining");
            }

            // NEW: Use MiningSyncManager for cross-device sync
            if (syncManager != null) {
                // Start mining with sync manager - handles cross-device coordination
                syncManager.startMining(initialTotalCoins);
                isMiningActive = true;
                startTime = syncManager.getMiningStartTime();

                // Fallback if syncManager doesn't set start time
                if (startTime <= 0) {
                    startTime = System.currentTimeMillis();
                }

                // Reset notification flag for new session
                miningCompleteNotificationShown = false;
                lastSavedMiningSession = 0;

                startUpdatingUI();
                updateMiningStatusUI();
                startButton.setEnabled(true);

                // Schedule work manager for background completion
                Context context = getSafeContext();
                if (context != null) {
                    Data inputData = new Data.Builder().putLong("startTime", startTime).build();
                    OneTimeWorkRequest miningWorkRequest = new OneTimeWorkRequest.Builder(MiningWorker.class)
                            .setInitialDelay(24, TimeUnit.HOURS)
                            .setInputData(inputData)
                            .build();
                    WorkManager.getInstance(context).enqueue(miningWorkRequest);
                }

                Log.d(TAG, "Mining started via MiningSyncManager at: " + startTime);
                return;
            }

            // Fallback to direct Firebase if syncManager not available
            // userID already defined above, reuse it
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                                        if (isAdded()) {
                                            isMiningActive = true;
                                            // Reset notification flag for new session
                                            miningCompleteNotificationShown = false;
                                            lastSavedMiningSession = 0;
                                            startUpdatingUI();
                                            startButton.setEnabled(true);

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
            return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time", e);
            return "00:00:00";
        }
    }

    /**
     * Update the Jarvis-style mining status UI
     */
    private void updateMiningStatusUI() {
        if (!isAdded()) return;

        try {
            // Update blob animation state
            if (startButton != null) {
                startButton.setMining(isMiningActive);
            }

            // Update status indicator
            if (statusIndicator != null) {
                statusIndicator.setBackgroundResource(
                    isMiningActive ? R.drawable.status_indicator_active : R.drawable.status_indicator_idle
                );
            }

            // Update status text
            if (miningStatusText != null) {
                miningStatusText.setText(isMiningActive ? "Mining Active" : "Ready");
            }

            // Update subtext
            if (miningSubtext != null) {
                if (isMiningActive && startTime > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = ((float) elapsed / MINING_DURATION) * 100;
                    miningSubtext.setText(String.format(Locale.US, "%.1f%% Complete", progress));
                } else {
                    miningSubtext.setText("24h Session");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating mining status UI", e);
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

                                    // Check achievements after mining
                                    checkAchievementsAfterMining(updatedTotal);
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

    private void checkAchievementsAfterMining(double totalCoins) {
        try {
            Context context = getSafeContext();
            if (context == null) return;

            AchievementManager achievementManager = AchievementManager.getInstance(context);
            MiningStreakManager streakManager = MiningStreakManager.getInstance(context);

            // Get referral count
            SharedPreferences prefs = getSafeSharedPreferences();
            int referrals = prefs != null ? prefs.getInt("referralCount", 0) : 0;
            int streak = streakManager.getCurrentStreak();
            int spins = prefs != null ? prefs.getInt("totalSpins", 0) : 0;

            // Check all achievements
            achievementManager.checkAchievements(totalCoins, referrals, streak, spins);

            Log.d(TAG, "Achievements checked: coins=" + totalCoins + ", referrals=" + referrals + ", streak=" + streak);
        } catch (Exception e) {
            Log.w(TAG, "Error checking achievements", e);
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
            float baseRatePerHour = 0.00125f * 3600f; // 4.5 LYX/hour base
            float totalMultiplier = 1.0f;
            StringBuilder indicators = new StringBuilder();

            // 1. BoostManager boosts (existing)
            if (boostManager != null) {
                baseRatePerHour = boostManager.getCurrentMiningRatePerHour();
                String boostIndicators = boostManager.getBoostIndicators();
                if (!boostIndicators.isEmpty()) {
                    indicators.append(boostIndicators);
                }
            }

            // 2. Security Circle boost (+10% per active member, max 50%)
            if (securityCircleManager != null) {
                float circleBoost = securityCircleManager.getBoostMultiplier();
                if (circleBoost > 1.0f) {
                    totalMultiplier *= circleBoost;
                    indicators.append(" C");
                }
            }

            // 3. Team Mining boost
            if (teamMiningManager != null && teamMiningManager.hasTeam()) {
                float teamBoost = teamMiningManager.getTeamBoost();
                if (teamBoost > 1.0f) {
                    totalMultiplier *= teamBoost;
                    indicators.append(" T");
                }
            }

            // 4. Mining Phase multiplier (early adopter bonus)
            if (miningPhaseManager != null) {
                float phaseMultiplier = miningPhaseManager.getTotalPhaseMultiplier();
                if (phaseMultiplier > 1.0f) {
                    totalMultiplier *= phaseMultiplier;
                    indicators.append(" P");
                }
            }

            // 5. Event multiplier (flash events, happy hour, etc.)
            if (countdownEventManager != null) {
                float eventMultiplier = countdownEventManager.getMultiplierForFeature("mining");
                if (eventMultiplier > 1.0f) {
                    totalMultiplier *= eventMultiplier;
                    indicators.append(" E");
                }
            }

            // Calculate final rate
            float finalRate = baseRatePerHour * totalMultiplier;

            // Display rate per hour - clean format without indicators
            miningRate.setText(String.format(Locale.US, "%.2f/h", finalRate));

            Log.d(TAG, "Mining rate updated: " + finalRate + " LYX/h (multiplier: " + totalMultiplier + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error updating mining rate display", e);
            if (miningRate != null) {
                miningRate.setText("4.50/h");
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
                    // fixed: added missing parentheses around condition
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

            // NEW: Notify sync manager app is in foreground for smart sync
            if (syncManager != null) {
                syncManager.onAppForeground();
            }

            // FIXED: Only fetch from Firebase if we don't have authoritative data yet
            // or if it's been a while since last fetch
            SharedPreferences prefs = getSafeSharedPreferences();
            if (prefs != null) {
                long lastFetch = prefs.getLong("lastBalanceFetch", 0);
                long now = System.currentTimeMillis();

                // Reset flag on first resume or if stale data
                if (now - lastFetch > 30000) { // 30 second threshold
                    isFirebaseDataLoaded = false;
                }

                if (!isFirebaseDataLoaded || now - lastFetch > 30000) {
                    fetchUserData();
                    prefs.edit().putLong("lastBalanceFetch", now).apply();
                } else {
                    // Just restart the UI update if mining is active
                    Log.d(TAG, "Using existing Firebase data (still fresh)");
                }
            }

            // Check if mining is active and update UI
            if (isMiningActive && startTime > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= MINING_DURATION) {
                    // Mining completed while app was in background
                    isMiningActive = false;

                    // Only save if not already done for this session
                    if (lastSavedMiningSession != startTime) {
                        lastSavedMiningSession = startTime;
                        double tokens = calculateTokens(MINING_DURATION);
                        double finalTotal = initialTotalCoins + tokens;
                        updateBalanceDisplay(finalTotal);
                        saveMinedTokens(tokens);

                        // Show notification only once
                        if (!miningCompleteNotificationShown) {
                            miningCompleteNotificationShown = true;
                            showMiningCompleteNotification();
                        }
                    }

                    miningTimerTextView.setText("00:00:00");
                    miningPer.setText("Tap to Start");

                    if (miningRef != null) {
                        miningRef.child("isMiningActive").setValue(false);
                        miningRef.child("startTime").setValue(0);
                    }
                } else {
                    startUpdatingUI();
                }
            }

            // Update mining rate display
            updateMiningRateDisplay();

            Log.d(TAG, "onResume completed - Mining: " + isMiningActive + ", StartTime: " + startTime);
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            // NEW: Notify sync manager app is going to background
            if (syncManager != null) {
                syncManager.onAppBackground();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
            if (boostManager != null) {
                boostManager.removeBoostChangeListener(this);
            }
            // NEW: Clean up sync manager listener
            if (syncManager != null) {
                syncManager.removeListener();
            }
            // NEW: Clean up WalletManager balance listener to prevent memory leaks
            if (walletManager != null) {
                walletManager.removeBalanceChangeListener(this);
            }
            // Reset the Firebase loaded flag
            isFirebaseDataLoaded = false;
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView", e);
        }
    }

    /**
     * FIXED: Centralized method to update balance display
     * This prevents multiple sources from fighting over the display
     */
    private void updateBalanceDisplay(double balance) {
        if (!isAdded() || counterTextView == null) return;
        try {
            counterTextView.setText(formatLargeNumber(balance));
            Log.d(TAG, "Balance display updated: " + balance);
        } catch (Exception e) {
            Log.e(TAG, "Error updating balance display", e);
        }
    }

    public static String formatLargeNumber(double value) {
        try {
            // Handle negative or invalid values
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return "0.00";
            }
            if (value < 0) {
                return "0.00";
            }

            // For small numbers, show 4 decimal places for precision
            if (value < 10) {
                return String.format(Locale.US, "%.4f", value);
            }
            // For medium numbers, show 2 decimal places
            if (value < 1_000) {
                return String.format(Locale.US, "%.2f", value);
            }

            // For large numbers, use abbreviations
            String[] units = {"", "K", "M", "B", "T", "P", "E"};
            int unitIndex = (int) (Math.log10(value) / 3);
            if (unitIndex >= units.length) {
                unitIndex = units.length - 1;
            }
            double shortValue = value / Math.pow(1000, unitIndex);
            return String.format(Locale.US, "%.2f%s", shortValue, units[unitIndex]);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting large number: " + value, e);
            return "0.00";
        }
    }

    // ==========================================
    // MiningSyncManager.MiningSyncListener Implementation
    // ==========================================

    @Override
    public void onMiningStateChanged(boolean isActive, long miningStartTime, long remainingTime) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            try {
                isMiningActive = isActive;
                startTime = miningStartTime;
                if (isActive && miningStartTime > 0) {
                    if (remainingTime > 0) {
                        miningTimerTextView.setText(formatTime(remainingTime));
                        float progress = ((float) (MINING_DURATION - remainingTime) / MINING_DURATION) * 100;
                        miningPer.setText(String.format(Locale.US, "%.1f%%", progress));
                        startUpdatingUI();
                    } else {
                        // Mining completed
                        miningTimerTextView.setText("00:00:00");
                        miningPer.setText("Tap to Start");
                        isMiningActive = false;
                    }
                } else {
                    miningTimerTextView.setText("00:00:00");
                    miningPer.setText("Tap to Start");
                }
                Log.d(TAG, "Mining state updated from sync - Active: " + isActive);
            } catch (Exception e) {
                Log.e(TAG, "Error updating mining state from sync", e);
            }
        });
    }

    @Override
    public void onMiningConflict(String message) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            Context context = getSafeContext();
            if (context != null) {
                ToastUtils.showInfo(context, message);
            }
            Log.d(TAG, "Mining conflict detected: " + message);
        });
    }

    @Override
    public void onTotalCoinsUpdated(double totalCoins) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            try {
                initialTotalCoins = totalCoins;
                // FIXED: Only update if sync provides a valid value
                // Firebase direct fetch is more authoritative
                if (totalCoins > 0 && !isFirebaseDataLoaded) {
                    initialTotalCoins = totalCoins;
                    if (isMiningActive && startTime > 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double currentTokens = calculateTokens(elapsed);
                        updateBalanceDisplay(totalCoins + currentTokens);
                    } else {
                        updateBalanceDisplay(totalCoins);
                    }
                    SharedPreferences prefs = getSafeSharedPreferences();
                    if (prefs != null) {
                        prefs.edit().putFloat("totalCoins", (float) totalCoins).apply();
                    }
                    Log.d(TAG, "Total coins updated from sync: " + totalCoins);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating total coins from sync", e);
            }
        });
    }

    @Override
    public void onSyncComplete(boolean success) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (success) {
                Log.d(TAG, "Sync completed successfully");
                updateMiningRateDisplay();
            } else {
                Log.w(TAG, "Sync failed, using cached data");
            }
        });
    }

    // ==========================================
    // WalletManager.BalanceChangeListener Implementation
    // ==========================================

    /**
     * NEW: Called whenever tokens are added from any source:
     * - Spin wheel rewards
     * - Reward zone bonuses
     * - Referral rewards
     * - Purchases
     * - Firebase sync updates
     *
     * This ensures the MiningFragment balance display is always in sync
     * regardless of where tokens were earned.
     */
    @Override
    public void onBalanceChanged(double newBalance) {
        if (!isAdded()) return;

        // Run on UI thread for safety
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                try {
                    // Only update if we have a valid balance
                    if (newBalance >= 0) {
                        // Update the authoritative balance
                        initialTotalCoins = newBalance;

                        // Update display considering active mining session
                        if (isMiningActive && startTime > 0) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double currentTokens = calculateTokens(elapsed);
                            updateBalanceDisplay(newBalance + currentTokens);
                        } else {
                            updateBalanceDisplay(newBalance);
                        }

                        // Cache the updated balance
                        SharedPreferences prefs = getSafeSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putFloat("totalCoins", (float) newBalance).apply();
                        }

                        Log.d(TAG, "Balance updated from WalletManager: " + newBalance);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating balance from WalletManager", e);
                }
            });
        }
    }
}
