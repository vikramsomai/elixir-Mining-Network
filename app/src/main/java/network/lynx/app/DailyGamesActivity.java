package network.lynx.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.Random;

/**
 * DailyGamesActivity - Fun mini-games that engage users daily
 *
 * Games:
 * 1. Daily Number Prediction (free)
 * 2. Coin Flip (bet tokens)
 * 3. Lucky Treasure Hunt info
 *
 * All games designed to:
 * - Create daily habit
 * - Generate ad revenue
 * - Increase session time
 */
public class DailyGamesActivity extends AppCompatActivity {
    private static final String TAG = "DailyGamesActivity";

    // UI Components
    private ImageView backButton;
    private TextView balanceText;

    // Daily Prediction
    private MaterialCardView predictionCard;
    private TextView predictionTitle, predictionStatus;
    private LinearLayout numberPickerLayout;
    private TextView selectedNumberText;
    private MaterialButton playPredictionBtn;
    private Slider numberSlider;
    private int selectedNumber = 5;
    private boolean predictionPlayed = false;

    // Coin Flip
    private MaterialCardView coinFlipCard;
    private ImageView coinImage;
    private TextView coinFlipResult, coinFlipStreak;
    private MaterialButton betLowBtn, betMedBtn, betHighBtn, flipBtn;
    private TextView betAmountText;
    private double currentBet = 5;
    private boolean isFlipping = false;

    // VIP Status
    private MaterialCardView vipCard;
    private TextView vipTierText, vipProgressText, vipPerksText;
    private ProgressBar vipProgressBar;

    // Managers
    private EngagementFeaturesManager engagementManager;
    private AdManager adManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_games);

        engagementManager = EngagementFeaturesManager.getInstance(this);
        // FIXED: Ensure user is refreshed in case they logged in after manager was created
        engagementManager.refreshUser();
        adManager = AdManager.getInstance();

        initViews();
        setupClickListeners();
        loadData();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        balanceText = findViewById(R.id.balanceText);

        // Daily Prediction
        predictionCard = findViewById(R.id.predictionCard);
        predictionTitle = findViewById(R.id.predictionTitle);
        predictionStatus = findViewById(R.id.predictionStatus);
        numberSlider = findViewById(R.id.numberSlider);
        selectedNumberText = findViewById(R.id.selectedNumberText);
        playPredictionBtn = findViewById(R.id.playPredictionBtn);

        // Coin Flip
        coinFlipCard = findViewById(R.id.coinFlipCard);
        coinImage = findViewById(R.id.coinImage);
        coinFlipResult = findViewById(R.id.coinFlipResult);
        coinFlipStreak = findViewById(R.id.coinFlipStreak);
        betLowBtn = findViewById(R.id.betLowBtn);
        betMedBtn = findViewById(R.id.betMedBtn);
        betHighBtn = findViewById(R.id.betHighBtn);
        flipBtn = findViewById(R.id.flipBtn);
        betAmountText = findViewById(R.id.betAmountText);

        // VIP
        vipCard = findViewById(R.id.vipCard);
        vipTierText = findViewById(R.id.vipTierText);
        vipProgressText = findViewById(R.id.vipProgressText);
        vipPerksText = findViewById(R.id.vipPerksText);
        vipProgressBar = findViewById(R.id.vipProgressBar);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        // Number Slider
        if (numberSlider != null) {
            numberSlider.addOnChangeListener((slider, value, fromUser) -> {
                selectedNumber = (int) value;
                selectedNumberText.setText(String.valueOf(selectedNumber));
            });
        }

        // Play Prediction Button
        playPredictionBtn.setOnClickListener(v -> {
            if (!predictionPlayed) {
                showAdThenPlayPrediction();
            }
        });

        // Bet amount buttons
        betLowBtn.setOnClickListener(v -> setBetAmount(5));
        betMedBtn.setOnClickListener(v -> setBetAmount(15));
        betHighBtn.setOnClickListener(v -> setBetAmount(30));

        // Flip coin button
        flipBtn.setOnClickListener(v -> {
            if (!isFlipping) {
                showAdThenFlipCoin();
            }
        });
    }

    private void loadData() {
        loadBalance();
        loadPredictionStatus();
        loadVIPStatus();
    }

    private void loadBalance() {
        WalletManager wallet = WalletManager.getInstance(this);
        double balance = wallet.getTotalBalance();
        balanceText.setText(String.format(Locale.US, "%.2f LYX", balance));
    }

    private void loadPredictionStatus() {
        engagementManager.getDailyPredictionStatus(new EngagementFeaturesManager.DailyPredictionCallback() {
            @Override
            public void onResult(EngagementFeaturesManager.DailyPredictionGame game) {
                runOnUiThread(() -> {
                    if (game.played) {
                        predictionPlayed = true;
                        playPredictionBtn.setEnabled(false);
                        playPredictionBtn.setText("Played Today");

                        if (game.won) {
                            predictionStatus.setText(String.format(Locale.US,
                                "🎉 You WON! Guessed %d, it was %d! +%.0f LYX",
                                game.predictedNumber, game.actualNumber, game.reward));
                            predictionStatus.setTextColor(getResources().getColor(R.color.accentGreen, null));
                        } else {
                            predictionStatus.setText(String.format(Locale.US,
                                "You guessed %d, it was %d. +%.0f LYX consolation",
                                game.predictedNumber, game.actualNumber, game.reward));
                            predictionStatus.setTextColor(getResources().getColor(R.color.textSecondary, null));
                        }
                        predictionStatus.setVisibility(View.VISIBLE);
                        numberSlider.setEnabled(false);
                    } else {
                        predictionPlayed = false;
                        playPredictionBtn.setEnabled(true);
                        playPredictionBtn.setText("🎯 Predict & Win");
                        predictionStatus.setVisibility(View.GONE);
                        numberSlider.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    private void loadVIPStatus() {
        engagementManager.getVIPStatus(new EngagementFeaturesManager.VIPStatusCallback() {
            @Override
            public void onSuccess(EngagementFeaturesManager.VIPStatus status) {
                runOnUiThread(() -> {
                    vipTierText.setText(status.currentTier.icon + " " + status.currentTier.name);

                    if (status.currentTier != status.nextTier) {
                        vipProgressText.setText(String.format(Locale.US,
                            "%.0f / %d to %s",
                            status.currentCoins, status.nextTier.requiredCoins, status.nextTier.name));
                        vipProgressBar.setProgress((int) (status.progressToNextTier * 100));
                    } else {
                        vipProgressText.setText("MAX LEVEL REACHED! 👑");
                        vipProgressBar.setProgress(100);
                    }

                    StringBuilder perks = new StringBuilder();
                    for (String perk : status.unlockedPerks) {
                        perks.append("✓ ").append(perk).append("\n");
                    }
                    if (perks.length() > 0) {
                        vipPerksText.setText(perks.toString().trim());
                        vipPerksText.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Handle error
            }
        });
    }

    private void showAdThenPlayPrediction() {
        playPredictionBtn.setEnabled(false);
        playPredictionBtn.setText("Loading...");

        // Show ad consent dialog
        AdConsentManager.showMinimalConsentDialog(this, "prediction", new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                adManager.showRewardedAd(DailyGamesActivity.this, "games_prediction", new AdManager.AdShowCallback() {
                    @Override
                    public void onAdShowed() {}

                    @Override
                    public void onAdShowFailed(String error) {
                        // Still let them play without ad
                        playPrediction();
                    }

                    @Override
                    public void onAdDismissed() {
                        resetPredictionButton();
                    }

                    @Override
                    public void onAdNotAvailable() {
                        playPrediction();
                    }

                    @Override
                    public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                        playPrediction();
                    }
                });
            }

            @Override
            public void onConsentDenied() {
                resetPredictionButton();
            }
        });
    }

    private void resetPredictionButton() {
        runOnUiThread(() -> {
            if (!predictionPlayed) {
                playPredictionBtn.setEnabled(true);
                playPredictionBtn.setText("🎯 Predict & Win");
            }
        });
    }

    private void playPrediction() {
        engagementManager.playDailyPrediction(selectedNumber, new EngagementFeaturesManager.RewardCallback() {
            @Override
            public void onSuccess(double reward, String message) {
                runOnUiThread(() -> {
                    predictionPlayed = true;
                    playPredictionBtn.setEnabled(false);
                    playPredictionBtn.setText("Played Today");

                    predictionStatus.setText(message);
                    predictionStatus.setTextColor(reward > 5 ?
                        getResources().getColor(R.color.accentGreen, null) :
                        getResources().getColor(R.color.textSecondary, null));
                    predictionStatus.setVisibility(View.VISIBLE);

                    // Animate the result
                    predictionStatus.setAlpha(0f);
                    predictionStatus.animate().alpha(1f).setDuration(500).start();

                    // FIXED: Refresh wallet to sync balance with MiningFragment
                    try {
                        WalletManager.getInstance(DailyGamesActivity.this).refreshBalance();
                    } catch (Exception e) {
                        // Wallet manager not initialized
                    }

                    loadBalance();
                    ToastUtils.showInfo(DailyGamesActivity.this, message);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(DailyGamesActivity.this, error);
                    resetPredictionButton();
                });
            }
        });
    }

    private void setBetAmount(double amount) {
        currentBet = amount;
        betAmountText.setText(String.format(Locale.US, "Bet: %.0f LYX", amount));

        // Highlight selected button
        betLowBtn.setStrokeWidth(amount == 5 ? 3 : 0);
        betMedBtn.setStrokeWidth(amount == 15 ? 3 : 0);
        betHighBtn.setStrokeWidth(amount == 30 ? 3 : 0);
    }

    private void showAdThenFlipCoin() {
        flipBtn.setEnabled(false);
        flipBtn.setText("Loading...");

        AdConsentManager.showMinimalConsentDialog(this, "coinflip", new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                adManager.showRewardedAd(DailyGamesActivity.this, "games_coinflip", new AdManager.AdShowCallback() {
                    @Override
                    public void onAdShowed() {}

                    @Override
                    public void onAdShowFailed(String error) {
                        flipCoin(true); // Pick heads by default
                    }

                    @Override
                    public void onAdDismissed() {
                        resetFlipButton();
                    }

                    @Override
                    public void onAdNotAvailable() {
                        flipCoin(true);
                    }

                    @Override
                    public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                        // Let user pick heads or tails
                        showHeadsTailsPicker();
                    }
                });
            }

            @Override
            public void onConsentDenied() {
                resetFlipButton();
            }
        });
    }

    private void resetFlipButton() {
        runOnUiThread(() -> {
            flipBtn.setEnabled(true);
            flipBtn.setText("🪙 FLIP");
        });
    }

    private void showHeadsTailsPicker() {
        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pick Your Side")
                .setMessage("Bet: " + currentBet + " LYX")
                .setPositiveButton("Heads 🦅", (d, w) -> flipCoin(true))
                .setNegativeButton("Tails 🔢", (d, w) -> flipCoin(false))
                .setCancelable(false)
                .show();
        });
    }

    private void flipCoin(boolean predictHeads) {
        isFlipping = true;

        // Animate coin flip
        ObjectAnimator animator = ObjectAnimator.ofFloat(coinImage, "rotationY", 0f, 1800f);
        animator.setDuration(2000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                engagementManager.playCoinFlip(currentBet, predictHeads, new EngagementFeaturesManager.CoinFlipCallback() {
                    @Override
                    public void onResult(EngagementFeaturesManager.CoinFlipGame game) {
                        runOnUiThread(() -> {
                            isFlipping = false;
                            resetFlipButton();

                            String result = game.isHeads ? "HEADS! 🦅" : "TAILS! 🔢";
                            coinFlipResult.setText(result);
                            coinFlipResult.setVisibility(View.VISIBLE);

                            if (game.won) {
                                coinFlipResult.setTextColor(getResources().getColor(R.color.accentGreen, null));
                                ToastUtils.showInfo(DailyGamesActivity.this,
                                    String.format(Locale.US, "🎉 You WON! +%.0f LYX", game.winAmount));
                            } else {
                                coinFlipResult.setTextColor(getResources().getColor(R.color.error, null));
                                ToastUtils.showInfo(DailyGamesActivity.this,
                                    String.format(Locale.US, "😔 You lost %.0f LYX", game.betAmount));
                            }

                            // FIXED: Refresh wallet to sync balance with MiningFragment
                            try {
                                WalletManager.getInstance(DailyGamesActivity.this).refreshBalance();
                            } catch (Exception e) {
                                // Wallet manager not initialized
                            }

                            loadBalance();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            isFlipping = false;
                            resetFlipButton();
                            ToastUtils.showInfo(DailyGamesActivity.this, error);
                        });
                    }
                });
            }
        });

        animator.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }
}

