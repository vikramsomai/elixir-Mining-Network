package network.lynx.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MiningFragment extends Fragment {

    private TextView counterTextView, miningTimerTextView, miningRate,miningPer;
    private MiningBlobView startButton;
    CardView invite;
    private Handler handler;
    private final float baseIncrement = 0.0001f;
    private DatabaseReference miningRef;
    private boolean isMiningActive = false; // Default to false
    private long startTime = 0;
    private final static long MINING_DURATION = 24*60 * 60 * 1000; // 2 minutes
    private SharedPreferences tokenPrefs;
    private String prefsName;
    private boolean isAdLoaded = false;
    private boolean isWatched = false;
    String referralCode;
    private MiningViewModel miningViewModel;
    private RewardedAd rewardedAd;
    private static final String CHANNEL_ID = "mining_channel";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private CircularProgressBar circleProgress;
    private double initialTotalCoins = 0.0; // New variable to store initial totalcoins

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mining, container, false);

        counterTextView = view.findViewById(R.id.balance);
        miningTimerTextView = view.findViewById(R.id.timer);
        startButton = view.findViewById(R.id.miningBlob);
        miningRate = view.findViewById(R.id.miningrate);
        miningPer=view.findViewById(R.id.miningPer);
        invite=view.findViewById(R.id.invite);
//        circleProgress = view.findViewById(R.id.circleProgress);
        handler = new Handler();

        // Initialize progress bar to 0%
//        circleProgress.setProgress(0f);

        loadRewardedAd();

        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        prefsName = "TokenPrefs_" + userID;
        tokenPrefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        miningRef = FirebaseDatabase.getInstance().getReference("users").child(userID).child("mining");

        // Load cached data immediately
        loadCachedData();

        // Fetch latest data from Firebase
        fetchUserData();


        invite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareAppInvite();
            }
        });

        startButton.setOnClickListener(v -> {
            if (!isMiningActive) {
                showAdPrompt();
            } else {
                Toast.makeText(getActivity(), "Mining already started", Toast.LENGTH_SHORT).show();
            }
        });

        miningViewModel = new ViewModelProvider(this).get(MiningViewModel.class);
        miningViewModel.getTotalCoins().observe(getViewLifecycleOwner(), balance -> {
            if (balance != null) {
                // Update UI and cache
                counterTextView.setText(formatLargeNumber(balance));
                tokenPrefs.edit().putFloat("totalCoins", balance.floatValue()).apply();
            }
        });
        miningViewModel.getMiningRate().observe(getViewLifecycleOwner(), rate -> {
            if (rate != null) {
                miningRate.setText(String.format("%.4f LYX", rate));
            }
        });
        createNotificationChannel();
        checkAndRequestNotificationPermission();


        return view;
    }
    private void shareAppInvite() {
          // Fetch dynamically for each user
        String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + referralCode;

        String message = "Join Lynx Network and earn rewards! Use my referral code: "
                + referralCode + "\nDownload here: " + inviteLink;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, message);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share via");
        startActivity(shareIntent);
    }

    private void loadCachedData() {
        // Retrieve cached values
        float cachedTotalCoins = tokenPrefs.getFloat("totalCoins", 0.0f);
        boolean cachedIsMiningActive = tokenPrefs.getBoolean("isMiningActive", false);
        long cachedStartTime = tokenPrefs.getLong("startTime", 0);

        // Set initial UI based on cached data
        counterTextView.setText(formatLargeNumber(cachedTotalCoins));
        if (cachedIsMiningActive && cachedStartTime > 0) {
            long elapsed = System.currentTimeMillis() - cachedStartTime;
            if (elapsed < MINING_DURATION) {
                // Calculate tokens and progress
                double tokens = calculateTokens(elapsed);
                counterTextView.setText(formatLargeNumber(cachedTotalCoins + tokens));
                miningTimerTextView.setText(formatTime(MINING_DURATION - elapsed));
                float progress = (float) elapsed / MINING_DURATION;
//                circleProgress.setProgress(progress);
            } else {
                // Mining completed

                miningTimerTextView.setText("00:00:00");
//                circleProgress.setProgress(1.0f);
            }
        } else {
            miningTimerTextView.setText("00:00:00");
//            circleProgress.setProgress(0f);
        }
    }

    private void fetchUserData() {
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Fetch totalcoins
                Double fetchedTotalCoins = snapshot.child("totalcoins").getValue(Double.class);
                referralCode=snapshot.child("referralCode").getValue(String.class);
                if (fetchedTotalCoins == null) fetchedTotalCoins = 0.0;
                initialTotalCoins = fetchedTotalCoins;
                tokenPrefs.edit().putFloat("totalCoins", fetchedTotalCoins.floatValue()).apply();

                // Fetch mining data
                Long firebaseStartTime = snapshot.child("mining/startTime").getValue(Long.class);
                Boolean miningActive = snapshot.child("mining/isMiningActive").getValue(Boolean.class);

                if (miningActive != null && miningActive && firebaseStartTime != null && firebaseStartTime > 0) {
                    startTime = firebaseStartTime;
                    long elapsed = System.currentTimeMillis() - startTime;

                    if (elapsed >= MINING_DURATION) {
                        // Mining completed while offline
                        isMiningActive = false;
                        double tokens = calculateTokens(MINING_DURATION);
                        double finalTotal = initialTotalCoins + tokens;
                        counterTextView.setText(formatLargeNumber(finalTotal));
                        miningTimerTextView.setText("00:00:00");
//                        circleProgress.setProgress(1.0f);
                        saveMinedTokens(tokens);
                        miningRef.child("isMiningActive").setValue(false);
                        miningRef.child("startTime").setValue(0);
                    } else {
                        // Mining still ongoing
                        isMiningActive = true;
                        startUpdatingUI();
                    }
                } else {
                    // No active mining
                    isMiningActive = false;
                    miningTimerTextView.setText("00:00:00");
//                    circleProgress.setProgress(0f);
                    counterTextView.setText(formatLargeNumber(initialTotalCoins));
                }
                // Cache the mining state
                tokenPrefs.edit()
                        .putBoolean("isMiningActive", isMiningActive)
                        .putLong("startTime", startTime)
                        .apply();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to fetch user data", error.toException());
            }
        });
    }

    private void startUpdatingUI() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isMiningActive && startTime > 0) {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    long remaining = MINING_DURATION - elapsed;
                    if (remaining <= 0) {
                        // Mining completed
                        isMiningActive = false;
                        double tokens = calculateTokens(MINING_DURATION);
                        double finalTotal = initialTotalCoins + tokens;
                        counterTextView.setText(formatLargeNumber(finalTotal));
                        miningTimerTextView.setText("00:00:00");
//                        circleProgress.setProgress(1.0f);
                        saveMinedTokens(tokens);
                        miningRef.child("isMiningActive").setValue(false);
                        miningRef.child("startTime").setValue(0);
                        showMiningCompleteNotification();
                    } else {
                        // Mining in progress
                        double tokens = calculateTokens(elapsed);
                        double currentTotal = initialTotalCoins + tokens;
                        counterTextView.setText(formatLargeNumber(currentTotal));
                        miningTimerTextView.setText(formatTime(remaining));
                        float progress = ((float) elapsed / MINING_DURATION) * 100; // Convert to percentage
                        miningPer.setText(String.format("%.2f%%", progress)); // Display percentage with 2 decimal places
                        handler.postDelayed(this, 1000);
                    }
                }
            }
        }, 1000);
    }

    private void startMining() {
        startButton.setEnabled(false);
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);
        userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                initialTotalCoins = snapshot.getValue(Double.class) != null ? snapshot.getValue(Double.class) : 0.0;
                // Start mining
                startTime = System.currentTimeMillis();
                miningRef.child("startTime").setValue(startTime);
                miningRef.child("isMiningActive").setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            isMiningActive = true;
                            startUpdatingUI();
                            startButton.setEnabled(true);
                            // Enqueue WorkManager if applicable
                            Data inputData = new Data.Builder().putLong("startTime", startTime).build();
                            OneTimeWorkRequest miningWorkRequest = new OneTimeWorkRequest.Builder(MiningWorker.class)
                                    .setInitialDelay(2, TimeUnit.MINUTES)
                                    .setInputData(inputData)
                                    .build();
                            WorkManager.getInstance(requireContext()).enqueue(miningWorkRequest);
                        })
                        .addOnFailureListener(e -> {
                            startButton.setEnabled(true);
                            Log.e("Mining", "Failed to start mining", e);
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                startButton.setEnabled(true);
                Log.e("Firebase", "Failed to fetch totalcoins", error.toException());
            }
        });
    }

    private double calculateTokens(long elapsedMillis) {
        float newBaseIncrement = isWatched ? baseIncrement : 0.00005f; // Adjust as per your logic
        double tokens = new BigDecimal(elapsedMillis)
                .divide(new BigDecimal(1000), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(newBaseIncrement))
                .multiply(new BigDecimal(getReferralBonusMultiplier()))
                .doubleValue();
        tokenPrefs.edit().putFloat("miningTokens", (float) tokens).apply(); // Cache as float if needed
        return tokens;
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    private void saveMinedTokens(double minedTokens) {
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);

        userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double currentTotal = snapshot.getValue(Double.class);
                if (currentTotal == null) currentTotal = 0.0;
                double updatedTotal = currentTotal + minedTokens;
                userRef.child("totalcoins").setValue(updatedTotal)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("Mining", "Mined tokens updated successfully");
                            tokenPrefs.edit().putFloat("miningTokens", 0.0f).apply();
                        })
                        .addOnFailureListener(e -> Log.e("Mining", "Failed to update mined tokens", e));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Error fetching total coins", error.toException());
            }
        });
    }

    private float getReferralBonusMultiplier() {
        int referralCount = tokenPrefs.getInt("referralCount", 0);
        return referralCount <= 0 ? 1.0f : 1.0f + (referralCount * 0.10f);
    }

    private void showAdPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        Animation shakeAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.shake);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.custom_dialog, null);
        builder.setView(dialogView);

        AlertDialog alertDialog = builder.create();

        TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
        TextView dialogMessage = dialogView.findViewById(R.id.dialogMessage);
        Button okButton = dialogView.findViewById(R.id.okButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        dialogTitle.setText("Boost Your Mining?");
        dialogMessage.setText("Watch an ad to increase your mining speed.");

        okButton.setOnClickListener(v -> {
            if (isAdLoaded) {
                isWatched = true;
                showRewardedAd();
                alertDialog.dismiss();
            } else {
                Toast.makeText(getActivity(), "Ad not loaded yet. Please try again later.", Toast.LENGTH_SHORT).show();
            }
        });

        cancelButton.setOnClickListener(v -> {
            isWatched = false;
            showMiningSpeedWarning();
            startMining();
            alertDialog.dismiss();
        });

        alertDialog.show();
    }

    private void showMiningSpeedWarning() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Mining Speed Reduced")
                .setMessage("You skipped the ad. Your mining speed is lower than usual.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(requireContext(), "ca-app-pub-1396109779371789/9096627942", adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        isAdLoaded = true;
                        rewardedAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        isAdLoaded = false;
                        rewardedAd = null;
                    }
                });
    }

    private void showRewardedAd() {
        if (rewardedAd != null) {
            rewardedAd.show(requireActivity(), rewardItem -> {
                startMining();
                loadRewardedAd();
            });
        } else {
            startMining();
        }
    }

    private void showMiningCompleteNotification() {
        Context context = requireContext();
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Mining Completed!")
                .setContentText("Your mining session has finished. Collect your LYX rewards now!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(1001, builder.build());
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Notification permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Permission denied. Notifications won't work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Mining Notifications";
            String description = "Notifications for mining completion";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null); // Clean up handler to prevent memory leaks
    }

    public static String formatLargeNumber(double value) {
        if (value < 1_000) return String.format(Locale.US, "%.2f", value);
        String[] units = {"", "K", "M", "B", "T", "P", "E"};
        int unitIndex = (int) (Math.log10(value) / 3);
        double shortValue = value / Math.pow(1000, unitIndex);
        return String.format(Locale.US, "%.2f%s", shortValue, units[unitIndex]);
    }
}