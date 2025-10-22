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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private CardView spinnerView;
    private GridView gridView;
    private ImageView profilePic;
    private TextView countStreak, TotalStreak;
    private ViewPager2 viewPager;

    // Firebase and Authentication
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPreferences;

    // Tracked realtime listeners and refs
    private ValueEventListener userValueListener;
    private ValueEventListener bannersValueListener;
    private DatabaseReference bannersRef;

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

    // Banner Management
    private List<Banner> bannerList = new ArrayList<>();
    private BannerAdapter bannerAdapter;
    private Handler bannerHandler;
    private Runnable bannerRunnable;

    // NEW: TaskManager integration
    private TaskManager taskManager;

    public HomeFragment() {}

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
        setupBannerCarousel();
        setupGridView();
        setupClickListeners();
        loadInitialData();

        // Smart preload ads based on user behavior
        smartPreloadAds();

        return view;
    }

    private void smartPreloadAds() {
        try {
            if (getContext() != null) adManager.smartPreloadAd(requireContext(), AdManager.AD_UNIT_CHECK_IN);
        } catch (Exception e) {
            Log.w(TAG, "smartPreloadAds failed", e);
        }
    }

    private boolean initializeFragment() {
        todayDate = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Calendar.getInstance().getTime());
        if (getActivity() == null) return false;
        sharedPreferences = requireActivity().getSharedPreferences("userData", MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        String userId = getSafeUserId();

        if (userId != null && !userId.isEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
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
        textView = view.findViewById(R.id.cryptoAddress);
        viewPager = view.findViewById(R.id.viewPager);
    }

    private void setupUserInterface() {
        String userId = getSafeUserId();
        if (userId != null) {
            String address = createUniqueId(userId);
            String addressFull = createFullUniqueId(userId);
            if (textView != null) textView.setText(address);
            if (copyAddress != null) copyAddress.setOnClickListener(v -> {
                copyToClipboard(addressFull);
                ToastUtils.showInfo(getContext(), "Address copied");
            });
        }
        loadCachedUserData();
        loadProfilePicture();
    }

    private void loadCachedUserData() {
        String cachedName = sharedPreferences.getString("username", "");
        String cachedCount = sharedPreferences.getString("streakCount", "0");
        String cachedTotal = sharedPreferences.getString("totalStreak", "0");

        if (!cachedName.isEmpty() && !cachedName.equals("none")) {
            if (username != null) username.setText(cachedName);
            if (imageView != null) imageView.setHash(cachedName.hashCode());
        } else if (username != null) {
            username.setText("Loading...");
        }

        if (TotalStreak != null) TotalStreak.setText(cachedCount);
        if (countStreak != null) countStreak.setText(String.format(Locale.getDefault(), "%s LYX", cachedTotal));
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
            if (profilePic != null) profilePic.setVisibility(View.GONE);
            if (imageView != null) imageView.setVisibility(View.VISIBLE);
        }
    }

    private void setupBannerCarousel() {
        bannerAdapter = new BannerAdapter(getContext(), bannerList);
        if (viewPager != null) viewPager.setAdapter(bannerAdapter);
        bannerHandler = new Handler(Looper.getMainLooper());

        bannerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (isAdded() && !bannerList.isEmpty() && viewPager != null) {
                        int currentItem = viewPager.getCurrentItem();
                        int nextItem = (currentItem + 1) % bannerList.size();
                        viewPager.setCurrentItem(nextItem, true);
                        bannerHandler.postDelayed(this, 3000);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in ViewPager runnable: " + e.getMessage());
                }
            }
        };

        bannerHandler.postDelayed(bannerRunnable, 3000);
        loadBannersFromFirebase();
    }

    private void loadBannersFromFirebase() {
        bannersRef = FirebaseDatabase.getInstance().getReference("banners");
        bannersValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!isAdded()) return;
                bannerList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Banner banner = snapshot.getValue(Banner.class);
                    if (banner != null) bannerList.add(banner);
                }
                if (bannerAdapter != null) bannerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (isAdded()) Log.e(TAG, "Failed to load banners: " + databaseError.getMessage());
            }
        };
        bannersRef.addValueEventListener(bannersValueListener);
    }

    private void setupGridView() {
        String[] optionName = {"LeaderBoard", "Blogs", "Faqs"};
        int[] optionImage = {R.drawable.leaderboard_17595903, R.drawable.news, R.drawable.ic_question};

        homeAdapater adapter = new homeAdapater(requireActivity(), optionName, optionImage);
        if (gridView != null) gridView.setAdapter(adapter);

        if (gridView != null) {
            gridView.setOnItemClickListener((parent, view, position, id) -> {
                if (!isAdded()) return;
                switch (optionName[position]) {
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
        if (spinnerView != null) spinnerView.setOnClickListener(v -> {
            if (isAdded()) startActivity(new Intent(requireActivity(), spinActivity.class));
        });

        if (imageView != null) imageView.setOnClickListener(v -> openProfileEdit());
        if (profilePic != null) profilePic.setOnClickListener(v -> openProfileEdit());
        if (claimbtn != null) claimbtn.setOnClickListener(v -> handleClaimButtonClick());
    }

    private void openProfileEdit() {
        if (isAdded()) startActivity(new Intent(requireActivity(), ProfileEditActivity.class));
    }

    private void handleClaimButtonClick() {
        if (!isAdded() || isProcessingReward) return;
        if (!canClaimReward()) return;
        if (hasClaimedToday()) {
            ToastUtils.showInfo(getContext(), "You have already claimed your reward today!");
            return;
        }

        adManager.recordFeatureUsage(requireContext(), AdManager.AD_UNIT_CHECK_IN);

        AdConsentManager.showCheckInConsentDialog(requireActivity(), new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                proceedWithAdReward();
            }

            @Override
            public void onConsentDenied() {
                ToastUtils.showError(getContext(), "Check-in cancelled");
            }
        });
    }

    private void proceedWithAdReward() {
        if (adManager.isAdReady(AdManager.AD_UNIT_CHECK_IN)) {
            showRewardedAd();
        } else {
            if (claimbtn != null) {
                claimbtn.setText("Loading Ad...");
                claimbtn.setEnabled(false);
            }
            adManager.loadRewardedAd(requireContext(), AdManager.AD_UNIT_CHECK_IN, new AdManager.AdLoadCallback() {
                @Override
                public void onAdLoaded() {
                    if (isAdded()) {
                        showRewardedAd();
                    }
                }

                @Override
                public void onAdLoadFailed(String error) {
                    if (isAdded()) {
                        ToastUtils.showError(getContext(), "Ad not available. Please try again later.");
                        resetClaimButton();
                    }
                }
            });
        }
    }

    private void showRewardedAd() {
        if (!isAdded()) return;
        isProcessingReward = true;
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
                if (isAdded()) {
                    ToastUtils.showError(getContext(), "Ad failed to show. Please try again later.");
                    resetClaimButton();
                }
            }

            @Override
            public void onAdDismissed() {
                Log.d(TAG, "Check-in ad dismissed without reward");
                if (isAdded()) {
                    Toast.makeText(getContext(), "Ad was not completed. Please watch the full ad to claim your reward.", Toast.LENGTH_SHORT).show();
                    resetClaimButton();
                }
            }

            @Override
            public void onAdNotAvailable() {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Ad not available. Please try again later.", Toast.LENGTH_SHORT).show();
                    resetClaimButton();
                }
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                if (isAdded()) {
                    grantCheckInReward();
                }
            }
        });
    }

    private boolean canClaimReward() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastClaim = currentTime - rewardClaimedTime;
        if (timeSinceLastClaim < rewardCooldownMillis && rewardClaimedTime > 0) {
            long remainingTime = rewardCooldownMillis - timeSinceLastClaim;
            long hours = remainingTime / (1000 * 60 * 60);
            long minutes = (remainingTime / (1000 * 60)) % 60;
            ToastUtils.showSuccess(getContext(), String.format("Please wait %d hours and %d minutes before claiming again!", hours, minutes));
            return false;
        }
        return true;
    }

    private boolean hasClaimedToday() {
        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());
        String lastClaimDate = sharedPreferences.getString("lastClaimDate", "");
        Log.d(TAG, "Checking if claimed today - Today: " + today + ", Last claim: " + lastClaimDate);
        return today.equals(lastClaimDate);
    }

    private void grantCheckInReward() {
        if (!isAdded()) {
            Log.w(TAG, "Fragment detached, cannot grant check-in reward");
            return;
        }

        Log.d(TAG, "Granting check-in reward");
        if (claimbtn != null) {
            claimbtn.setText("Processing...");
            claimbtn.setEnabled(false);
        }

        rewardClaimedTime = System.currentTimeMillis();
        saveRewardClaimedTime();

        String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());
        sharedPreferences.edit().putString("lastClaimDate", today).apply();

        if (taskManager != null) {
            taskManager.syncWithHomeFragmentCheckin();
            Log.d(TAG, "Synced check-in with TaskManager for boost integration");
        }

        claimToken();
    }

    private void resetClaimButton() {
        if (claimbtn != null && isAdded()) {
            claimbtn.setText("Check in");
            claimbtn.setEnabled(true);
        }
        isProcessingReward = false;
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

        userValueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                if (snapshot.exists()) updateUserDataFromSnapshot(snapshot);
                else handleMissingUserData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Log.e(TAG, "Database error: " + error.getMessage());
                    handleDatabaseError();
                }
            }
        };
        databaseReference.addValueEventListener(userValueListener);
    }

    private void updateUserDataFromSnapshot(DataSnapshot snapshot) {
        try {
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
            if (streakCount == null) streakCount = 0;

            Double totalDailyStreakValue = snapshot.child("totalStreak").getValue(Double.class);
            if (totalDailyStreakValue == null) totalDailyStreakValue = 0.0;

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("streakCount", String.valueOf(streakCount));
            editor.putString("totalStreak", String.valueOf(totalDailyStreakValue));
            editor.apply();

            if (TotalStreak != null) {
                String currentStreakText = TotalStreak.getText().toString();
                if (!currentStreakText.equals(String.valueOf(streakCount))) {
                    animateTextUpdate(TotalStreak, String.valueOf(streakCount));
                }
            }

            if (countStreak != null) {
                String newRewardText = totalDailyStreakValue + " LYX";
                if (!countStreak.getText().toString().equals(newRewardText)) {
                    animateTextUpdate(countStreak, newRewardText);
                }
            }

            Integer level = snapshot.child("level").getValue(Integer.class);
            currentLevel = (level != null) ? level : 1;

            totalcoins = snapshot.child("totalcoins").getValue(Double.class);
            if (totalcoins == null) totalcoins = 0.0;

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

        Double totalDailyStreak = snapshot.child("totalStreak").getValue(Double.class);
        if (totalDailyStreak == null) totalDailyStreak = 0.0;

        Double totalcoins = snapshot.child("totalcoins").getValue(Double.class);
        if (totalcoins == null) totalcoins = 0.0;

        Integer referrals = snapshot.child("referrals").getValue(Integer.class);
        if (referrals == null) referrals = 0;

        String todayDate = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

        Log.d(TAG, "Processing token claim - Last date: " + lastDate + ", Today: " + todayDate + ", Current streak: " + streakCount);

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
        if (countStreak != null) animateTextUpdate(countStreak, String.valueOf(totalDailyStreak) + " LYX");
        if (TotalStreak != null) animateTextUpdate(TotalStreak, String.valueOf(newStreak));

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
        Log.d(TAG, "calculateNewStreak - lastDate: " + lastDate + ", currentStreak: " + currentStreak + ", todayDate: " + todayDate);

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
            if (!isAdded()) return;
            isProcessingReward = false;

            if (task.isSuccessful()) {
                Log.d(TAG, "Firebase update successful");

                // ✅ Force-refresh user data to reflect latest values
                fetchUserDataFromFirebase();

                ToastUtils.showInfo(getContext(),
                        String.format("Reward claimed! Streak: %d days, Earned: %.1f LYX", newStreak, dailyReward));

                startCountdown();
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

        databaseReference.child("lastDate").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                String firebaseLastDate = snapshot.getValue(String.class);
                String today = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(new Date());

                SharedPreferences.Editor editor = sharedPreferences.edit();

                if (today.equals(firebaseLastDate)) {
                    editor.putString("lastClaimDate", firebaseLastDate);
                    editor.apply();
                    startCountdown();
                    if (claimbtn != null) claimbtn.setEnabled(false);
                } else {
                    editor.putString("lastClaimDate", "");
                    editor.apply();
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
        if (!isAdded()) return;
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
        if (!isAdded()) return;
        long currentTime = System.currentTimeMillis();
        long remainingTimeMillis = rewardClaimedTime + rewardCooldownMillis - currentTime;

        if (remainingTimeMillis > 0) {
            if (claimbtn != null) claimbtn.setEnabled(false);
            if (countdownTimer != null) {
                countdownTimer.cancel();
            }
            countdownTimer = new CountDownTimer(remainingTimeMillis, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (!isAdded()) return;
                    long hours = millisUntilFinished / (1000 * 60 * 60);
                    long minutes = (millisUntilFinished / (1000 * 60)) % 60;
                    long seconds = (millisUntilFinished / 1000) % 60;
                    if (claimbtn != null)
                        claimbtn.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                }

                @Override
                public void onFinish() {
                    if (!isAdded()) return;
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
        if (textView == null || !isAdded()) return;
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(150);
        fadeOut.setFillAfter(true);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(150);
        fadeIn.setFillAfter(true);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                if (isAdded()) {
                    textView.setText(newText);
                    textView.startAnimation(fadeIn);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        textView.startAnimation(fadeOut);
    }

    private void saveRewardClaimedTime() {
        if (getActivity() == null || !isAdded()) return;

        String userId = sharedPreferences.getString("userid", "unknown_user");
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_checkin_" + userId, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("rewardClaimedTime", rewardClaimedTime);
        editor.apply();
    }
    private void loadRewardClaimedTime() {
        if (getActivity() == null || !isAdded()) return;

        String userId = sharedPreferences.getString("userid", "unknown_user");
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_checkin_" + userId, Context.MODE_PRIVATE);
        rewardClaimedTime = prefs.getLong("rewardClaimedTime", 0L);
    }
    private void copyToClipboard(String text) {
        if (getActivity() == null || !isAdded()) return;
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
                if (hex.length() == 1) hexString.append('0');
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
                if (hex.length() == 1) hexString.append('0');
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
        checkIfTokenClaimed();
        if (bannerHandler != null && bannerRunnable != null && bannerList.size() > 0) {
            bannerHandler.postDelayed(bannerRunnable, 3000);
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
        if (bannerHandler != null) {
            bannerHandler.removeCallbacksAndMessages(null);
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
        if (bannerHandler != null) {
            bannerHandler.removeCallbacksAndMessages(null);
            bannerHandler = null;
        }
        bannerRunnable = null;
        // Remove tracked realtime listeners to avoid traffic/leaks
        try {
            if (databaseReference != null && userValueListener != null) databaseReference.removeEventListener(userValueListener);
        } catch (Exception e) { Log.w(TAG, "Failed to remove user listener", e); }
        try {
            if (bannersRef != null && bannersValueListener != null) bannersRef.removeEventListener(bannersValueListener);
        } catch (Exception e) { Log.w(TAG, "Failed to remove banners listener", e); }
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

    // Safely get user id: prefer FirebaseAuth current user, fallback to stored prefs
    private @Nullable String getSafeUserId() {
        try {
            if (auth != null && auth.getCurrentUser() != null) {
                return auth.getCurrentUser().getUid();
            }
            if (sharedPreferences != null) {
                String prefUid = sharedPreferences.getString("userid", null);
                if (prefUid != null && !prefUid.isEmpty()) return prefUid;
            }
        } catch (Exception e) {
            Log.w(TAG, "getSafeUserId failed", e);
        }
        return null;
    }
}

