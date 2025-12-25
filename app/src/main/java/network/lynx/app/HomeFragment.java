package network.lynx.app;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.lelloman.identicon.view.IdenticonView;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final String DATE_FORMAT = "yyyy-MM-dd"; // Consistent date format

    // UI Components
    private View view;
    private TextView username;
    private IdenticonView imageView;
    private TextView textView;
    private ImageView copyAddress;
    private MaterialButton claimbtn;
    private LinearLayout spinnerView, boostCard;
    private GridView gridView;
    private ImageView profilePic;
    private TextView countStreak, TotalStreak;

    // Firebase and Authentication
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPreferences;

    // Tracked realtime listeners and refs
    private ValueEventListener userValueListener;

    // Data Variables
    private String todayDate;
    private Double totalDailyStreak, totalcoins;
    private Integer currentLevel = 1;

    // Ad Management
    private AdManager adManager;
    private boolean isProcessingReward = false;

    // Timer and UI Management
    private CountDownTimer countdownTimer;
    private long rewardClaimedTime;
    private final long rewardCooldownMillis = 24L * 60L * 60L * 1000L; // 24 hours, explicit long

    // NEW: TaskManager integration
    private TaskManager taskManager;

    // FirebaseManager for optimized operations
    private FirebaseManager firebaseManager;

    public HomeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "HomeFragment onCreate");
        adManager = AdManager.getInstance();
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "HomeFragment onCreateView");
        view = inflater.inflate(R.layout.fragment_home, container, false);

        if (!initializeFragment()) {
            return view;
        }

        initializeViews();
        setupUserInterface();
        setupGridView();
        setupClickListeners();
        loadInitialData();

        // Smart preload ads based on user behavior
        smartPreloadAds();

        return view;
    }

    private void smartPreloadAds() {
        try {
            if (getContext() != null)
                adManager.smartPreloadAd(requireContext(), AdManager.AD_UNIT_CHECK_IN);
        } catch (Exception e) {
            Log.w(TAG, "smartPreloadAds failed", e);
        }
    }

    private boolean initializeFragment() {
        todayDate = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Calendar.getInstance().getTime());
        if (getActivity() == null)
            return false;
        sharedPreferences = requireActivity().getSharedPreferences("userData", MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        String userId = getSafeUserId();

        if (userId != null && !userId.isEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);

            // Initialize FirebaseManager for optimized operations
            try {
                firebaseManager = FirebaseManager.getInstance(requireContext());
                Log.d(TAG, "FirebaseManager initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing FirebaseManager", e);
            }

            try {
                taskManager = new TaskManager(requireContext(), userId);
                Log.d(TAG, "TaskManager initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing TaskManager", e);
            }
            return true;
        } else {
            ToastUtils.showInfo(getContext(), "User not authenticated");
            return false;
        }
    }

    private void initializeViews() {
        username = view.findViewById(R.id.usernameTextView);
        imageView = view.findViewById(R.id.profileCircleImageView);
        claimbtn = view.findViewById(R.id.claimButton);
        countStreak = view.findViewById(R.id.streakCountReward);
        gridView = view.findViewById(R.id.gridview);
        TotalStreak = view.findViewById(R.id.streakCount);
        copyAddress = view.findViewById(R.id.copyAddress);
        profilePic = view.findViewById(R.id.profilePic);
        spinnerView = view.findViewById(R.id.spinWheelCard);
        boostCard = view.findViewById(R.id.boostCard);
        textView = view.findViewById(R.id.cryptoAddress);
    }

    private void setupUserInterface() {
        String userId = getSafeUserId();
        if (userId != null) {
            String address = createUniqueId(userId);
            String addressFull = createFullUniqueId(userId);
            if (textView != null)
                textView.setText(address);
            if (copyAddress != null)
                copyAddress.setOnClickListener(v -> {
                    copyToClipboard(addressFull);
                    ToastUtils.showInfo(getContext(), "Address copied");
                });
        }
        loadCachedUserData();
        loadProfilePicture();
    }

    private void loadCachedUserData() {
        // FIX: Verify cached data belongs to current user
        String cachedUserId = sharedPreferences.getString("userid", "");
        String currentUserId = getSafeUserId();

        // If cached data is from a different user, clear old data but DON'T set streak
        // to 0
        // Let Firebase fetch the real values
        if (currentUserId != null && !currentUserId.equals(cachedUserId)) {
            Log.w(TAG, "Cached data mismatch - cached: " + cachedUserId + ", current: " + currentUserId);
            // Clear old cached data - streak values will be fetched from Firebase
            sharedPreferences.edit()
                    .putString("userid", currentUserId)
                    .remove("streakCount") // Remove instead of setting to 0
                    .remove("totalStreak") // Remove instead of setting to 0
                    .putString("lastClaimDate", "")
                    .apply();
        }

        String cachedName = sharedPreferences.getString("username", "");
        String cachedCount = sharedPreferences.getString("streakCount", null); // Use null as default
        String cachedTotal = sharedPreferences.getString("totalStreak", null); // Use null as default

        Log.d(TAG, "loadCachedUserData: cachedCount=" + cachedCount + ", cachedTotal=" + cachedTotal);

        // Show cached username or fetch from Firebase user display name
        if (!cachedName.isEmpty() && !cachedName.equals("none")) {
            if (username != null)
                username.setText(cachedName);
            if (imageView != null)
                imageView.setHash(cachedName.hashCode());
        } else {
            // Try to get display name from Firebase Auth
            if (currentUser != null && currentUser.getDisplayName() != null
                    && !currentUser.getDisplayName().isEmpty()) {
                String displayName = currentUser.getDisplayName();
                if (username != null)
                    username.setText(displayName);
                if (imageView != null)
                    imageView.setHash(displayName.hashCode());
                // Cache it
                sharedPreferences.edit().putString("username", displayName).apply();
            } else if (currentUser != null && currentUser.getEmail() != null) {
                // Use email prefix as fallback
                String emailName = currentUser.getEmail().split("@")[0];
                if (username != null)
                    username.setText(emailName);
                if (imageView != null)
                    imageView.setHash(emailName.hashCode());
            } else {
                if (username != null)
                    username.setText("User");
            }
        }

        // Show cached values or "Loading..." if no cache
        if (TotalStreak != null) {
            TotalStreak.setText(cachedCount != null ? cachedCount : "...");
        }
        if (countStreak != null) {
            if (cachedTotal != null) {
                countStreak.setText(String.format(Locale.getDefault(), "%s LYX", cachedTotal));
            } else {
                countStreak.setText("...");
            }
        }
    }

    private void loadProfilePicture() {
        String picture = sharedPreferences.getString("profilePicUrl", null);
        if (picture != null && !picture.isEmpty()) {
            if (imageView != null) {
                imageView.setEnabled(false);
                imageView.setVisibility(View.GONE);
            }
            if (profilePic != null) {
                profilePic.setVisibility(View.VISIBLE);
                Glide.with(this).load(picture).centerCrop().into(profilePic);
            }
        } else {
            if (profilePic != null)
                profilePic.setVisibility(View.GONE);
            if (imageView != null)
                imageView.setVisibility(View.VISIBLE);
        }
    }

    private void setupGridView() {
        String[] optionName = { "Rewards", "LeaderBoard", "Blogs", "Faqs" };
        int[] optionImage = { R.drawable.ic_gift, R.drawable.leaderboard_17595903, R.drawable.news,
                R.drawable.ic_question };

        homeAdapater adapter = new homeAdapater(requireActivity(), optionName, optionImage);
        if (gridView != null)
            gridView.setAdapter(adapter);

        if (gridView != null) {
            gridView.setOnItemClickListener((parent, view, position, id) -> {
                if (!isAdded())
                    return;
                switch (optionName[position]) {
                    case "Rewards":
                        startActivity(new Intent(requireActivity(), RewardsHubActivity.class));
                        break;
                    case "LeaderBoard":
                        startActivity(new Intent(requireActivity(), LeaderBoardActivity.class));
                        break;
                    case "Blogs":
                        startActivity(new Intent(requireActivity(), NewsFeedActivity.class));
                        break;
                    case "Faqs":
                        startActivity(new Intent(requireActivity(), FaqsActivity.class));
                        break;
                }
            });
        }
    }

    private void setupClickListeners() {
        if (spinnerView != null)
            spinnerView.setOnClickListener(v -> {
                if (isAdded())
                    startActivity(new Intent(requireActivity(), spinActivity.class));
            });

        if (boostCard != null)
            boostCard.setOnClickListener(v -> {
                if (isAdded())
                    startActivity(new Intent(requireActivity(), BoostActivity.class));
            });

        if (imageView != null)
            imageView.setOnClickListener(v -> openProfileEdit());
        if (profilePic != null)
            profilePic.setOnClickListener(v -> openProfileEdit());
        if (claimbtn != null)
            claimbtn.setOnClickListener(v -> handleClaimButtonClick());
    }

    private void openProfileEdit() {
        if (isAdded())
            startActivity(new Intent(requireActivity(), ProfileEditActivity.class));
    }

    private void handleClaimButtonClick() {
        // Enhanced null safety and state checks to prevent crashes
        if (!isAdded() || getContext() == null || getActivity() == null) {
            Log.w(TAG, "Fragment not properly attached, cannot process claim");
            return;
        }

        if (isProcessingReward) {
            Log.d(TAG, "Already processing reward, ignoring click");
            return;
        }

        if (!canClaimReward()) {
            return;
        }

        if (hasClaimedToday()) {
            showSafeToast("You have already claimed your reward today!");
            return;
        }

        try {
            // Mark as processing early to prevent double-clicks
            isProcessingReward = true;

            if (adManager != null) {
                adManager.recordFeatureUsage(requireContext(), AdManager.AD_UNIT_CHECK_IN);
            }

            AdConsentManager.showCheckInConsentDialog(requireActivity(), new AdConsentManager.ConsentCallback() {
                @Override
                public void onConsentGiven() {
                    if (isAdded() && getActivity() != null) {
                        proceedWithAdReward();
                    } else {
                        isProcessingReward = false;
                    }
                }

                @Override
                public void onConsentDenied() {
                    isProcessingReward = false;
                    showSafeToast("Check-in cancelled");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in handleClaimButtonClick", e);
            isProcessingReward = false;
            showSafeToast("An error occurred. Please try again.");
        }
    }

    /**
     * Thread-safe toast helper that checks fragment state
     */
    private void showSafeToast(String message) {
        if (!isAdded() || getContext() == null)
            return;
        try {
            Context context = getContext();
            if (context != null) {
                ToastUtils.showInfo(context, message);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to show toast: " + e.getMessage());
        }
    }

    private void proceedWithAdReward() {
        if (!isAdded() || getContext() == null || getActivity() == null) {
            Log.w(TAG, "Fragment not attached, cannot proceed with ad reward");
            isProcessingReward = false;
            return;
        }

        if (adManager == null) {
            Log.e(TAG, "AdManager is null");
            isProcessingReward = false;
            showSafeToast("Unable to load ad. Please try again.");
            resetClaimButton();
            return;
        }

        try {
            // showRewardedAd will automatically load if not ready
            if (claimbtn != null) {
                claimbtn.setText("Loading Ad...");
                claimbtn.setEnabled(false);
            }
            showRewardedAd();
        } catch (Exception e) {
            Log.e(TAG, "Error in proceedWithAdReward", e);
            isProcessingReward = false;
            resetClaimButton();
        }
    }

    private void showRewardedAd() {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            Log.w(TAG, "Cannot show ad - fragment/activity not in valid state");
            isProcessingReward = false;
            return;
        }

        if (adManager == null) {
            Log.e(TAG, "AdManager is null in showRewardedAd");
            isProcessingReward = false;
            resetClaimButton();
            return;
        }

        try {
            if (claimbtn != null) {
                claimbtn.setText("Loading Ad...");
                claimbtn.setEnabled(false);
            }

            adManager.showRewardedAd(requireActivity(), AdManager.AD_UNIT_CHECK_IN, new AdManager.AdShowCallback() {
                @Override
                public void onAdShowed() {
                    Log.d(TAG, "Check-in ad showed");
                }

                @Override
                public void onAdShowFailed(String error) {
                    Log.e(TAG, "Ad show failed: " + error);
                    if (isAdded()) {
                        showSafeToast("Ad failed to show. Please try again later.");
                        resetClaimButton();
                    }
                    isProcessingReward = false;
                }

                @Override
                public void onAdDismissed() {
                    Log.d(TAG, "Check-in ad dismissed without reward");
                    if (isAdded()) {
                        showSafeToast("Ad was not completed. Please watch the full ad to claim your reward.");
                        resetClaimButton();
                    }
                    isProcessingReward = false;
                }

                @Override
                public void onAdNotAvailable() {
                    Log.w(TAG, "Ad not available");
                    if (isAdded()) {
                        showSafeToast("Ad not available. Please try again later.");
                        resetClaimButton();
                    }
                    isProcessingReward = false;
                }

                @Override
                public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                    Log.d(TAG, "User earned reward: " + rewardItem.getAmount());
                    if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                        // Run on UI thread to ensure safe UI updates
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded()) {
                                grantCheckInReward();
                            }
                        });
                    } else {
                        isProcessingReward = false;
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in showRewardedAd", e);
            isProcessingReward = false;
            resetClaimButton();
        }
    }

    private boolean canClaimReward() {
        try {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastClaim = currentTime - rewardClaimedTime;

            if (timeSinceLastClaim < rewardCooldownMillis && rewardClaimedTime > 0) {
                long remainingTime = rewardCooldownMillis - timeSinceLastClaim;
                long hours = remainingTime / (1000 * 60 * 60);
                long minutes = (remainingTime / (1000 * 60)) % 60;
                showSafeToast(String.format(Locale.getDefault(),
                        "Please wait %d hours and %d minutes before claiming again!", hours, minutes));
                // Update the button label immediately with remaining time
                if (claimbtn != null) {
                    claimbtn.setEnabled(false);
                    claimbtn.setText(String.format(Locale.getDefault(), "%02d:%02d:00", hours, minutes));
                }
                // Ensure countdown runs to keep the label updated
                startCountdown();
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in canClaimReward", e);
            return true; // Allow claim attempt on error
        }
    }

    private boolean hasClaimedToday() {
        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());
        String lastClaimDate = sharedPreferences.getString("lastClaimDate", "");
        Log.d(TAG, "Checking if claimed today - Today: " + today + ", Last claim: " + lastClaimDate);
        return today.equals(lastClaimDate);
    }

    private void grantCheckInReward() {
        if (!isAdded() || getActivity() == null) {
            Log.w(TAG, "Fragment detached, cannot grant check-in reward");
            isProcessingReward = false;
            return;
        }

        Log.d(TAG, "Granting check-in reward");

        try {
            if (claimbtn != null) {
                claimbtn.setText("Processing...");
                claimbtn.setEnabled(false);
            }

            rewardClaimedTime = System.currentTimeMillis();
            saveRewardClaimedTime();

            String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());
            if (sharedPreferences != null) {
                sharedPreferences.edit().putString("lastClaimDate", today).apply();
            }

            // Sync with TaskManager safely
            if (taskManager != null) {
                try {
                    taskManager.syncWithHomeFragmentCheckin();
                    Log.d(TAG, "Synced check-in with TaskManager for boost integration");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to sync with TaskManager", e);
                }
            }

            claimToken();
        } catch (Exception e) {
            Log.e(TAG, "Error in grantCheckInReward", e);
            isProcessingReward = false;
            resetClaimButton();
            showSafeToast("Error processing reward. Please try again.");
        }
    }

    private void resetClaimButton() {
        isProcessingReward = false;

        if (!isAdded() || getActivity() == null)
            return;

        // Ensure UI updates happen on main thread
        requireActivity().runOnUiThread(() -> {
            if (claimbtn != null && isAdded()) {
                claimbtn.setText("Check in");
                claimbtn.setEnabled(true);
            }
        });
    }

    private void loadInitialData() {
        loadRewardClaimedTime();
        fetchUserDataFromFirebase();
        checkIfTokenClaimed();
    }

    private void fetchUserDataFromFirebase() {
        if (databaseReference == null || !isAdded()) {
            Log.e(TAG, "Database reference is null or fragment is detached");
            return;
        }

        // Remove existing listener to prevent duplicates
        if (userValueListener != null) {
            databaseReference.removeEventListener(userValueListener);
        }

        // OPTIMIZATION: Use single value event listener to reduce Firebase reads
        // We'll refresh data manually when needed (on resume, after claim, etc.)
        userValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || getActivity() == null)
                    return;

                // Run on UI thread to ensure safe UI updates
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded())
                        return;
                    if (snapshot.exists()) {
                        Log.d(TAG, "Firebase data received, updating UI");
                        updateUserDataFromSnapshot(snapshot);
                    } else {
                        Log.w(TAG, "No data exists for this user");
                        handleMissingUserData();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded() && getActivity() != null) {
                    Log.e(TAG, "Database error: " + error.getMessage());
                    requireActivity().runOnUiThread(() -> handleDatabaseError());
                }
            }
        };
        databaseReference.addListenerForSingleValueEvent(userValueListener);
    }

    private void updateUserDataFromSnapshot(DataSnapshot snapshot) {
        try {
            Log.d(TAG, "updateUserDataFromSnapshot: Processing snapshot, exists=" + snapshot.exists());

            String fir_name = snapshot.child("username").getValue(String.class);
            if (fir_name != null && !fir_name.isEmpty()) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("username", fir_name);
                editor.apply();

                if (username != null && !fir_name.equals(username.getText().toString())) {
                    animateTextUpdate(username, fir_name);
                }

                if (imageView != null && imageView.getVisibility() == View.VISIBLE) {
                    imageView.setHash(fir_name.hashCode());
                }
            }

            Integer streakCount = snapshot.child("streakCount").getValue(Integer.class);
            if (streakCount == null)
                streakCount = 0;

            // FIX: totalStreak can be Integer or Double in Firebase, handle both
            Double totalDailyStreakValue = 0.0;
            Object totalStreakRaw = snapshot.child("totalStreak").getValue();
            if (totalStreakRaw != null) {
                if (totalStreakRaw instanceof Double) {
                    totalDailyStreakValue = (Double) totalStreakRaw;
                } else if (totalStreakRaw instanceof Long) {
                    totalDailyStreakValue = ((Long) totalStreakRaw).doubleValue();
                } else if (totalStreakRaw instanceof Integer) {
                    totalDailyStreakValue = ((Integer) totalStreakRaw).doubleValue();
                } else {
                    try {
                        totalDailyStreakValue = Double.parseDouble(totalStreakRaw.toString());
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse totalStreak: " + totalStreakRaw);
                    }
                }
            }

            Log.d(TAG, "updateUserDataFromSnapshot: streakCount=" + streakCount + ", totalStreak="
                    + totalDailyStreakValue);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("streakCount", String.valueOf(streakCount));
            editor.putString("totalStreak", String.valueOf(totalDailyStreakValue));
            editor.apply();

            if (TotalStreak != null) {
                Log.d(TAG, "updateUserDataFromSnapshot: Updating TotalStreak UI to " + streakCount);
                // Always update - don't check if equal since formats may differ
                animateTextUpdate(TotalStreak, String.valueOf(streakCount));
            } else {
                Log.w(TAG, "updateUserDataFromSnapshot: TotalStreak TextView is null!");
            }

            if (countStreak != null) {
                String newRewardText = String.format(java.util.Locale.US, "%.0f LYX", totalDailyStreakValue);
                Log.d(TAG, "updateUserDataFromSnapshot: Updating countStreak UI to " + newRewardText);
                // Always update - don't check if equal since formats may differ
                animateTextUpdate(countStreak, newRewardText);
            } else {
                Log.w(TAG, "updateUserDataFromSnapshot: countStreak TextView is null!");
            }

            Integer level = snapshot.child("level").getValue(Integer.class);
            currentLevel = (level != null) ? level : 1;

            totalcoins = snapshot.child("totalcoins").getValue(Double.class);
            if (totalcoins == null)
                totalcoins = 0.0;

            checkIfTokenClaimed();

        } catch (Exception e) {
            Log.e(TAG, "Error updating user data: " + e.getMessage());
        }
    }

    private void handleMissingUserData() {
        animateTextUpdate(username, "Unknown");
        animateTextUpdate(TotalStreak, "0");
        animateTextUpdate(countStreak, "0 LYX");
        ToastUtils.showInfo(getContext(), "User data not found");
    }

    private void handleDatabaseError() {
        ToastUtils.showInfo(getContext(), "Failed to fetch user data");
        String cachedName = sharedPreferences.getString("username", "Unknown");
        String cachedCount = sharedPreferences.getString("streakCount", "0");
        String cachedTotal = sharedPreferences.getString("totalStreak", "0");
        animateTextUpdate(username, cachedName);
        animateTextUpdate(TotalStreak, cachedCount);
        animateTextUpdate(countStreak, cachedTotal + " LYX");
    }

    private void claimToken() {
        if (!isAdded() || getActivity() == null) {
            Log.e(TAG, "Fragment is detached or activity is null, cannot claim token");
            return;
        }

        String userId = getSafeUserId();
        if (userId == null || databaseReference == null) {
            Log.e(TAG, "User ID or database reference is null");
            if (isAdded()) {
                ToastUtils.showInfo(getContext(), "Cannot claim token at this time");
                resetClaimButton();
            }
            return;
        }

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    Log.w(TAG, "Fragment is detached, skipping data update");
                    return;
                }

                try {
                    processTokenClaim(snapshot);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing streak data: " + e.getMessage());
                    if (isAdded()) {
                        ToastUtils.showInfo(getContext(), "Error processing reward. Please try again.");
                        resetClaimButton();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Log.e(TAG, "Failed to update streak: " + error.getMessage());
                    ToastUtils.showInfo(getContext(), "Failed to claim reward. Please try again.");
                    resetClaimButton();
                }
            }
        });
    }

    private void processTokenClaim(DataSnapshot snapshot) {
        String lastDate = "";
        Integer streakCount = 0;

        if (snapshot.hasChild("lastDate")) {
            Object raw = snapshot.child("lastDate").getValue();
            if (raw != null) {
                lastDate = raw.toString();
            }
        }

        if (snapshot.hasChild("streakCount")) {
            Object raw = snapshot.child("streakCount").getValue();
            try {
                streakCount = Integer.parseInt(raw.toString());
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid streakCount: " + raw);
                streakCount = 0;
            }
        }

        // FIX: totalStreak can be Integer or Double in Firebase, handle both
        Double totalDailyStreak = 0.0;
        Object totalStreakRaw = snapshot.child("totalStreak").getValue();
        if (totalStreakRaw != null) {
            if (totalStreakRaw instanceof Double) {
                totalDailyStreak = (Double) totalStreakRaw;
            } else if (totalStreakRaw instanceof Long) {
                totalDailyStreak = ((Long) totalStreakRaw).doubleValue();
            } else if (totalStreakRaw instanceof Integer) {
                totalDailyStreak = ((Integer) totalStreakRaw).doubleValue();
            } else {
                try {
                    totalDailyStreak = Double.parseDouble(totalStreakRaw.toString());
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse totalStreak: " + totalStreakRaw);
                }
            }
        }

        Double totalcoins = snapshot.child("totalcoins").getValue(Double.class);
        if (totalcoins == null)
            totalcoins = 0.0;

        Integer referrals = snapshot.child("referrals").getValue(Integer.class);
        if (referrals == null)
            referrals = 0;

        String todayDate = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

        Log.d(TAG, "Processing token claim - Last date: " + lastDate + ", Today: " + todayDate + ", Current streak: "
                + streakCount);

        int newStreak = calculateNewStreak(lastDate, streakCount, todayDate);

        if (newStreak == -1) {
            ToastUtils.showInfo(getContext(), "You have already claimed your reward today.");
            resetClaimButton();
            return;
        }

        double dailyReward = newStreak * 5.0;
        totalDailyStreak += dailyReward;
        double newTotalCoins = totalcoins + dailyReward;

        updateLocalCache(newStreak, totalDailyStreak);
        if (countStreak != null)
            animateTextUpdate(countStreak, String.valueOf(totalDailyStreak) + " LYX");
        if (TotalStreak != null)
            animateTextUpdate(TotalStreak, String.valueOf(newStreak));

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastDate", todayDate);
        updates.put("streakCount", newStreak);
        updates.put("totalStreak", totalDailyStreak);
        updates.put("totalcoins", newTotalCoins);

        int newLevel = LevelSystem.getNewLevel((int) newTotalCoins, referrals);
        if (newLevel > currentLevel) {
            updates.put("level", newLevel);
            currentLevel = newLevel;
            if (isAdded()) {
                showLevelUpDialog(newLevel);
            }
        }

        updateFirebaseData(updates, newStreak, dailyReward);
    }

    private int calculateNewStreak(String lastDate, int currentStreak, String todayDate) {
        Log.d(TAG, "calculateNewStreak - lastDate: " + lastDate + ", currentStreak: " + currentStreak + ", todayDate: "
                + todayDate);

        if (lastDate == null || lastDate.isEmpty()) {
            Log.d(TAG, "No previous streak found, starting new");
            return 1;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
            Date today = sdf.parse(todayDate);
            Date lastClaimDate = sdf.parse(lastDate);

            if (today == null || lastClaimDate == null) {
                Log.e(TAG, "Failed to parse dates");
                return 1;
            }

            long diffInMillies = today.getTime() - lastClaimDate.getTime();
            long diffInDays = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);

            Log.d(TAG, "Today: " + sdf.format(today));
            Log.d(TAG, "Last Claim Date: " + sdf.format(lastClaimDate));
            Log.d(TAG, "Difference in days: " + diffInDays);

            if (diffInDays == 0) {
                Log.d(TAG, "Same day claim attempt");
                return -1; // Already claimed today
            } else if (diffInDays == 1) {
                Log.d(TAG, "Continuing streak: " + (currentStreak + 1));
                return currentStreak + 1;
            } else if (diffInDays > 1 && diffInDays <= 7) {
                Log.d(TAG, "Missed up to 7 days, continuing streak");
                ToastUtils.showInfo(getContext(), "You missed some days, but your streak continues!");
                return currentStreak + 1;
            } else {
                Log.d(TAG, "Streak broken due to long gap, resetting to 1");
                return 1;
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing dates", e);
            return 1;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error calculating streak", e);
            return 1;
        }
    }

    private void updateLocalCache(int newStreak, double totalDailyStreak) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("streakCount", String.valueOf(newStreak));
        editor.putString("totalStreak", String.valueOf(totalDailyStreak));
        editor.apply();
    }

    private void updateFirebaseData(Map<String, Object> updates, int newStreak, double dailyReward) {
        databaseReference.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isAdded())
                return;
            isProcessingReward = false;

            if (task.isSuccessful()) {
                Log.d(TAG, "Firebase update successful");

                // ✅ Set reward claimed time NOW and save it
                rewardClaimedTime = System.currentTimeMillis();
                saveRewardClaimedTime();

                // ✅ Force-refresh user data to reflect latest values
                fetchUserDataFromFirebase();

                ToastUtils.showInfo(getContext(),
                        String.format(Locale.getDefault(), "Reward claimed! Streak: %d days, Earned: %.1f LYX",
                                newStreak, dailyReward));

                // ✅ Start countdown timer immediately
                startCountdown();

                // ✅ Disable button immediately
                if (claimbtn != null) {
                    claimbtn.setEnabled(false);
                }
            } else {
                String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                Log.e(TAG, "Firebase update failed: " + errorMsg);
                ToastUtils.showInfo(getContext(), "Failed to save reward: " + errorMsg);
                resetClaimButton();
            }
        });
    }

    private void checkIfTokenClaimed() {
        if (databaseReference == null || !isAdded()) {
            Log.e(TAG, "Database reference is null or fragment is detached in checkIfTokenClaimed");
            return;
        }

        // Load reward claimed time from local storage first
        loadRewardClaimedTime();

        databaseReference.child("lastDate").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded())
                    return;

                String firebaseLastDate = snapshot.getValue(String.class);
                String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

                SharedPreferences.Editor editor = sharedPreferences.edit();

                if (today.equals(firebaseLastDate)) {
                    editor.putString("lastClaimDate", firebaseLastDate);
                    editor.apply();

                    // If rewardClaimedTime is 0, calculate from today's date
                    if (rewardClaimedTime == 0) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());
                            java.util.Date claimDate = sdf.parse(firebaseLastDate);
                            if (claimDate != null) {
                                // Use stored time if available, otherwise fallback to claim date start
                                rewardClaimedTime = Math.max(rewardClaimedTime, claimDate.getTime());
                                if (rewardClaimedTime == 0) {
                                    rewardClaimedTime = System.currentTimeMillis();
                                }
                                saveRewardClaimedTime();
                                Log.d(TAG, "Set rewardClaimedTime from Firebase date: " + rewardClaimedTime);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing claim date", e);
                            rewardClaimedTime = System.currentTimeMillis() - (12 * 60 * 60 * 1000);
                            saveRewardClaimedTime();
                        }
                    }

                    // Disable button and start timer
                    if (claimbtn != null)
                        claimbtn.setEnabled(false);
                    startCountdown();
                } else {
                    editor.putString("lastClaimDate", "");
                    editor.apply();
                    rewardClaimedTime = 0;
                    if (claimbtn != null) {
                        claimbtn.setText("Check in");
                        claimbtn.setEnabled(true);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Log.e(TAG, "Failed to check token claim status: " + error.getMessage());
                }
            }
        });
    }

    private void showLevelUpDialog(int newLevel) {
        if (!isAdded())
            return;
        String benefits = LevelSystem.getLevelBenefits(newLevel);
        new AlertDialog.Builder(requireContext())
                .setTitle("Level Up! 🎉")
                .setMessage("Congratulations! You've reached Level " + newLevel +
                        "\n\nNew Benefits:\n" + benefits)
                .setPositiveButton("Awesome!", null)
                .setCancelable(false)
                .show();
    }

    private void startCountdown() {
        if (!isAdded())
            return;
        long currentTime = System.currentTimeMillis();
        long remainingTimeMillis = rewardClaimedTime + rewardCooldownMillis - currentTime;

        if (remainingTimeMillis > 0) {
            if (claimbtn != null)
                claimbtn.setEnabled(false);
            if (countdownTimer != null) {
                countdownTimer.cancel();
            }
            countdownTimer = new CountDownTimer(remainingTimeMillis, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (!isAdded())
                        return;
                    long hours = millisUntilFinished / (1000 * 60 * 60);
                    long minutes = (millisUntilFinished / (1000 * 60)) % 60;
                    long seconds = (millisUntilFinished / 1000) % 60;
                    if (claimbtn != null)
                        claimbtn.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                }

                @Override
                public void onFinish() {
                    if (!isAdded())
                        return;
                    if (claimbtn != null) {
                        claimbtn.setText("Check in");
                        claimbtn.setEnabled(true);
                    }
                    rewardClaimedTime = 0L;
                    saveRewardClaimedTime();
                }
            }.start();
        } else {
            if (claimbtn != null) {
                claimbtn.setText("Check in");
                claimbtn.setEnabled(true);
            }
        }
    }

    private void animateTextUpdate(final TextView textView, final String newText) {
        if (textView == null || !isAdded())
            return;
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(150);
        fadeOut.setFillAfter(true);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(150);
        fadeIn.setFillAfter(true);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (isAdded()) {
                    textView.setText(newText);
                    textView.startAnimation(fadeIn);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        textView.startAnimation(fadeOut);
    }

    private void saveRewardClaimedTime() {
        if (getActivity() == null || !isAdded())
            return;

        // FIX: Use current Firebase UID instead of cached SharedPreferences value
        String userId = getSafeUserId();
        if (userId == null || userId.isEmpty()) {
            userId = sharedPreferences.getString("userid", "unknown_user");
        }
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_checkin_" + userId,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("rewardClaimedTime", rewardClaimedTime);
        editor.apply();
    }

    private void loadRewardClaimedTime() {
        if (getActivity() == null || !isAdded())
            return;

        // FIX: Use current Firebase UID instead of cached SharedPreferences value
        String userId = getSafeUserId();
        if (userId == null || userId.isEmpty()) {
            userId = sharedPreferences.getString("userid", "unknown_user");
        }
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_checkin_" + userId,
                Context.MODE_PRIVATE);
        rewardClaimedTime = prefs.getLong("rewardClaimedTime", 0L);
    }

    private void copyToClipboard(String text) {
        if (getActivity() == null || !isAdded())
            return;
        ClipboardManager clipboard = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Crypto Address", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    public String createUniqueId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userId.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            String fullHash = hexString.toString();
            return "0x" + fullHash.substring(0, 4) + "..." + fullHash.substring(fullHash.length() - 6);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error creating unique ID: " + e.getMessage());
            return "0x0000...000000";
        }
    }

    public String createFullUniqueId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userId.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return "0x" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error creating full unique ID: " + e.getMessage());
            return "0x0000000000000000000000000000000000000000";
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "HomeFragment onResume");
        adManager.cleanupExpiredAds();
        smartPreloadAds();
        fetchUserDataFromFirebase();

        // Restore countdown timer immediately if there's remaining time
        restoreCountdownTimer();

        // Also check Firebase for latest claim status
        checkIfTokenClaimed();
    }

    private void restoreCountdownTimer() {
        if (!isAdded() || claimbtn == null)
            return;

        // Load the saved reward claimed time
        loadRewardClaimedTime();

        // Check if there's remaining cooldown time
        long currentTime = System.currentTimeMillis();
        long remainingTimeMillis = rewardClaimedTime + rewardCooldownMillis - currentTime;

        if (remainingTimeMillis > 0) {
            Log.d(TAG, "Restoring countdown timer with " + remainingTimeMillis + "ms remaining");
            // Disable button and start countdown immediately
            claimbtn.setEnabled(false);
            startCountdown();
        } else {
            // No cooldown, enable button
            if (claimbtn != null) {
                claimbtn.setText("Check in");
                claimbtn.setEnabled(true);
            }
            rewardClaimedTime = 0L;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "HomeFragment onPause");
        if (TotalStreak != null && countStreak != null) {
            String currentStreak = TotalStreak.getText().toString();
            String currentReward = countStreak.getText().toString();
            if (!currentStreak.equals("0") && !currentReward.equals("0 LYX")) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("streakCount", currentStreak);
                editor.putString("totalStreak", currentReward.replace(" LYX", ""));
                editor.apply();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "HomeFragment onDestroyView");
        isProcessingReward = false;
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        // Remove tracked realtime listeners to avoid traffic/leaks
        try {
            if (databaseReference != null && userValueListener != null)
                databaseReference.removeEventListener(userValueListener);
        } catch (Exception e) {
            Log.w(TAG, "Failed to remove user listener", e);
        }
        view = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (TotalStreak != null && countStreak != null) {
            outState.putString("currentStreak", TotalStreak.getText().toString());
            outState.putString("currentReward", countStreak.getText().toString());
        }
        outState.putBoolean("isProcessingReward", isProcessingReward);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            String savedStreak = savedInstanceState.getString("currentStreak");
            String savedReward = savedInstanceState.getString("currentReward");
            if (savedStreak != null && !savedStreak.equals("0") && TotalStreak != null) {
                TotalStreak.setText(savedStreak);
            }
            if (savedReward != null && !savedReward.equals("0 LYX") && countStreak != null) {
                countStreak.setText(savedReward);
            }
            isProcessingReward = savedInstanceState.getBoolean("isProcessingReward", false);
        }
    }

    // Safely get user id: prefer FirebaseAuth current user, fallback to stored
    // prefs
    private @Nullable String getSafeUserId() {
        try {
            if (auth != null && auth.getCurrentUser() != null) {
                return auth.getCurrentUser().getUid();
            }
            if (sharedPreferences != null) {
                String prefUid = sharedPreferences.getString("userid", null);
                if (prefUid != null && !prefUid.isEmpty())
                    return prefUid;
            }
        } catch (Exception e) {
            Log.w(TAG, "getSafeUserId failed", e);
        }
        return null;
    }
}
