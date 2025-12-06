package network.lynx.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.Locale;

/**
 * Rewards Hub Activity
 * Central place for all reward features:
 * - Hourly Bonus
 * - Daily Lucky Number
 * - Scratch Cards
 * - Mining Streak
 * - Achievements
 */
public class RewardsHubActivity extends AppCompatActivity {
    private static final String TAG = "RewardsHubActivity";

    // Views
    private ImageView backButton;

    // Hourly Bonus
    private CardView hourlyBonusCard;
    private TextView hourlyBonusTimer;
    private TextView hourlyBonusAmount;
    private Button claimHourlyBonusBtn;
    private TextView hourlyClaimsLeft;

    // Lucky Number
    private CardView luckyNumberCard;
    private TextView luckyNumberStatus;
    private LinearLayout numberPickerLayout;
    private Button playLuckyNumberBtn;
    private TextView luckyNumberResult;

    // Scratch Card
    private CardView scratchCardCard;
    private TextView scratchCardCount;
    private Button earnScratchCardBtn;
    private Button scratchCardBtn;
    private TextView scratchCardResult;

    // Mining Streak
    private CardView miningStreakCard;
    private TextView streakDays;
    private TextView streakMultiplier;
    private ProgressBar streakProgress;
    private TextView streakNextMilestone;

    // Achievements
    private CardView achievementsCard;
    private TextView achievementCount;
    private TextView achievementBonus;
    private ProgressBar achievementProgress;
    private Button viewAchievementsBtn;

    // Managers
    private HourlyBonusManager hourlyBonusManager;
    private DailyLuckyNumberManager luckyNumberManager;
    private ScratchCardManager scratchCardManager;
    private MiningStreakManager miningStreakManager;
    private AchievementManager achievementManager;
    private AdManager adManager;

    // Timer
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards_hub);

        initializeManagers();
        initializeViews();
        setupClickListeners();
        loadAllData();
        startTimerUpdates();
    }

    private void initializeManagers() {
        try {
            hourlyBonusManager = HourlyBonusManager.getInstance(this);
            luckyNumberManager = DailyLuckyNumberManager.getInstance(this);
            scratchCardManager = ScratchCardManager.getInstance(this);
            miningStreakManager = MiningStreakManager.getInstance(this);
            achievementManager = AchievementManager.getInstance(this);
            adManager = AdManager.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing managers", e);
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backNav);

        // Hourly Bonus
        hourlyBonusCard = findViewById(R.id.hourlyBonusCard);
        hourlyBonusTimer = findViewById(R.id.hourlyBonusTimer);
        hourlyBonusAmount = findViewById(R.id.hourlyBonusAmount);
        claimHourlyBonusBtn = findViewById(R.id.claimHourlyBonusBtn);
        hourlyClaimsLeft = findViewById(R.id.hourlyClaimsLeft);

        // Lucky Number
        luckyNumberCard = findViewById(R.id.luckyNumberCard);
        luckyNumberStatus = findViewById(R.id.luckyNumberStatus);
        numberPickerLayout = findViewById(R.id.numberPickerLayout);
        playLuckyNumberBtn = findViewById(R.id.playLuckyNumberBtn);
        luckyNumberResult = findViewById(R.id.luckyNumberResult);

        // Scratch Card
        scratchCardCard = findViewById(R.id.scratchCardCard);
        scratchCardCount = findViewById(R.id.scratchCardCount);
        earnScratchCardBtn = findViewById(R.id.earnScratchCardBtn);
        scratchCardBtn = findViewById(R.id.scratchCardBtn);
        scratchCardResult = findViewById(R.id.scratchCardResult);

        // Mining Streak
        miningStreakCard = findViewById(R.id.miningStreakCard);
        streakDays = findViewById(R.id.streakDays);
        streakMultiplier = findViewById(R.id.streakMultiplier);
        streakProgress = findViewById(R.id.streakProgress);
        streakNextMilestone = findViewById(R.id.streakNextMilestone);

        // Achievements
        achievementsCard = findViewById(R.id.achievementsCard);
        achievementCount = findViewById(R.id.achievementCount);
        achievementBonus = findViewById(R.id.achievementBonus);
        achievementProgress = findViewById(R.id.achievementProgress);
        viewAchievementsBtn = findViewById(R.id.viewAchievementsBtn);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        // Hourly Bonus
        claimHourlyBonusBtn.setOnClickListener(v -> claimHourlyBonus());

        // Lucky Number
        playLuckyNumberBtn.setOnClickListener(v -> showLuckyNumberPicker());

        // Scratch Card
        earnScratchCardBtn.setOnClickListener(v -> earnScratchCard());
        scratchCardBtn.setOnClickListener(v -> scratchCard());

        // Achievements
        viewAchievementsBtn.setOnClickListener(v -> showAchievementsDialog());
    }

    private void loadAllData() {
        updateHourlyBonusUI();
        updateLuckyNumberUI();
        updateScratchCardUI();
        updateMiningStreakUI();
        updateAchievementsUI();
    }

    // ===================== HOURLY BONUS =====================

    private void updateHourlyBonusUI() {
        if (hourlyBonusManager == null) return;

        HourlyBonusManager.BonusStatus status = hourlyBonusManager.getBonusStatus();

        if (status.isAvailable) {
            hourlyBonusTimer.setText("Ready to claim!");
            hourlyBonusAmount.setText(String.format(Locale.getDefault(), "~%.1f LYX", status.potentialReward));
            claimHourlyBonusBtn.setEnabled(true);
            claimHourlyBonusBtn.setText("Claim Now!");
        } else {
            hourlyBonusTimer.setText(status.getTimeRemainingFormatted());
            hourlyBonusAmount.setText("Coming soon...");
            claimHourlyBonusBtn.setEnabled(false);
            claimHourlyBonusBtn.setText("Wait...");
        }

        hourlyClaimsLeft.setText(String.format(Locale.getDefault(),
                "%d/%d claims left today",
                status.maxClaimsPerDay - status.claimsToday,
                status.maxClaimsPerDay));
    }

    private void claimHourlyBonus() {
        if (hourlyBonusManager == null) return;

        claimHourlyBonusBtn.setEnabled(false);
        claimHourlyBonusBtn.setText("Claiming...");

        hourlyBonusManager.claimBonus(new HourlyBonusManager.ClaimCallback() {
            @Override
            public void onClaimSuccess(float bonusAmount, double newBalance, int claimsRemaining) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this,
                            String.format(Locale.getDefault(), "Claimed %.2f LYX!", bonusAmount));
                    updateHourlyBonusUI();
                });
            }

            @Override
            public void onClaimFailed(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, error);
                    updateHourlyBonusUI();
                });
            }
        });
    }

    // ===================== LUCKY NUMBER =====================

    private void updateLuckyNumberUI() {
        if (luckyNumberManager == null) return;

        DailyLuckyNumberManager.GameStatus status = luckyNumberManager.getGameStatus();

        if (status.hasPlayedToday) {
            playLuckyNumberBtn.setEnabled(false);
            playLuckyNumberBtn.setText("Come back tomorrow!");

            if (status.lastResult != null) {
                String resultText = "You guessed " + status.lastGuess +
                        " | Lucky number: " + status.lastLuckyNumber;
                luckyNumberResult.setText(resultText);
                luckyNumberResult.setVisibility(View.VISIBLE);
            }

            if (status.hasActiveBoost) {
                long hours = status.boostTimeRemaining / (60 * 60 * 1000);
                luckyNumberStatus.setText(String.format(Locale.getDefault(),
                        "%.1fx boost active (%dh left)", status.activeMultiplier, hours));
            } else {
                luckyNumberStatus.setText("Already played today");
            }
        } else {
            playLuckyNumberBtn.setEnabled(true);
            playLuckyNumberBtn.setText("Pick Your Lucky Number!");
            luckyNumberStatus.setText("Guess 1-10 for a chance to win!");
            luckyNumberResult.setVisibility(View.GONE);
        }
    }

    private void showLuckyNumberPicker() {
        String[] numbers = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};

        new AlertDialog.Builder(this)
                .setTitle("🎲 Pick Your Lucky Number!")
                .setItems(numbers, (dialog, which) -> {
                    int selectedNumber = which + 1;
                    playLuckyNumber(selectedNumber);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void playLuckyNumber(int guess) {
        if (luckyNumberManager == null) return;

        playLuckyNumberBtn.setEnabled(false);
        playLuckyNumberBtn.setText("Revealing...");

        luckyNumberManager.playGame(guess, new DailyLuckyNumberManager.GameCallback() {
            @Override
            public void onGameComplete(DailyLuckyNumberManager.LuckyNumberResult result) {
                runOnUiThread(() -> {
                    showLuckyNumberResult(result);
                    updateLuckyNumberUI();
                });
            }

            @Override
            public void onAlreadyPlayed(int lastGuess, int lastLuckyNumber,
                                        DailyLuckyNumberManager.GuessResult lastResult) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "Already played today!");
                    updateLuckyNumberUI();
                });
            }

            @Override
            public void onGameError(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, error);
                    updateLuckyNumberUI();
                });
            }
        });
    }

    private void showLuckyNumberResult(DailyLuckyNumberManager.LuckyNumberResult result) {
        new AlertDialog.Builder(this)
                .setTitle(result.result == DailyLuckyNumberManager.GuessResult.EXACT_MATCH ?
                        "🎉 JACKPOT!" : "🎲 Result")
                .setMessage(result.message + "\n\nTokens won: " + result.tokensWon + " LYX")
                .setPositiveButton("Awesome!", null)
                .show();
    }

    // ===================== SCRATCH CARD =====================

    private void updateScratchCardUI() {
        if (scratchCardManager == null) return;

        ScratchCardManager.CardStatus status = scratchCardManager.getCardStatus();

        scratchCardCount.setText(String.format(Locale.getDefault(),
                "%d cards available", status.cardsAvailable));

        // Earn card button
        if (scratchCardManager.canEarnCard()) {
            earnScratchCardBtn.setEnabled(true);
            earnScratchCardBtn.setText("Watch Ad to Earn Card");
        } else {
            earnScratchCardBtn.setEnabled(false);
            earnScratchCardBtn.setText("Max cards reached today");
        }

        // Scratch button
        if (status.cardsAvailable > 0 || status.hasUnrevealedCard) {
            scratchCardBtn.setEnabled(true);
            scratchCardBtn.setText(status.hasUnrevealedCard ? "Reveal Card!" : "Scratch Card!");
        } else {
            scratchCardBtn.setEnabled(false);
            scratchCardBtn.setText("No cards available");
        }
    }

    private void earnScratchCard() {
        // Show ad consent dialog first
        AdConsentManager.showCheckInConsentDialog(this, new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                showAdForScratchCard();
            }

            @Override
            public void onConsentDenied() {
                ToastUtils.showInfo(RewardsHubActivity.this, "Watch an ad to earn a scratch card!");
            }
        });
    }

    private void showAdForScratchCard() {
        earnScratchCardBtn.setEnabled(false);
        earnScratchCardBtn.setText("Loading ad...");

        adManager.showRewardedAd(this, AdManager.AD_UNIT_CHECK_IN, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {}

            @Override
            public void onAdShowFailed(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "Ad failed. Try again.");
                    updateScratchCardUI();
                });
            }

            @Override
            public void onAdDismissed() {
                runOnUiThread(() -> updateScratchCardUI());
            }

            @Override
            public void onAdNotAvailable() {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "No ads available. Try later.");
                    updateScratchCardUI();
                });
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                scratchCardManager.earnCard(new ScratchCardManager.EarnCallback() {
                    @Override
                    public void onCardEarned(int totalCards) {
                        runOnUiThread(() -> {
                            ToastUtils.showInfo(RewardsHubActivity.this, "Scratch card earned!");
                            updateScratchCardUI();
                        });
                    }

                    @Override
                    public void onEarnFailed(String error) {
                        runOnUiThread(() -> {
                            ToastUtils.showInfo(RewardsHubActivity.this, error);
                            updateScratchCardUI();
                        });
                    }
                });
            }
        });
    }

    private void scratchCard() {
        if (scratchCardManager == null) return;

        ScratchCardManager.CardStatus status = scratchCardManager.getCardStatus();

        if (status.hasUnrevealedCard) {
            // Reveal existing card
            scratchCardManager.revealCard(new ScratchCardManager.RevealCallback() {
                @Override
                public void onCardRevealed(ScratchCardManager.ScratchCardReward reward) {
                    runOnUiThread(() -> {
                        showScratchCardResult(reward);
                        updateScratchCardUI();
                    });
                }

                @Override
                public void onRevealFailed(String error) {
                    runOnUiThread(() -> {
                        ToastUtils.showInfo(RewardsHubActivity.this, error);
                        updateScratchCardUI();
                    });
                }
            });
        } else {
            // Scratch new card
            scratchCardManager.scratchCard(new ScratchCardManager.ScratchCallback() {
                @Override
                public void onCardReady(ScratchCardManager.ScratchCardReward reward) {
                    runOnUiThread(() -> {
                        showScratchCardResult(reward);
                        updateScratchCardUI();
                    });
                }

                @Override
                public void onNoCards(String message) {
                    runOnUiThread(() -> {
                        ToastUtils.showInfo(RewardsHubActivity.this, message);
                        updateScratchCardUI();
                    });
                }
            });
        }
    }

    private void showScratchCardResult(ScratchCardManager.ScratchCardReward reward) {
        new AlertDialog.Builder(this)
                .setTitle("🎫 Scratch Card Reward!")
                .setMessage(reward.getDisplayText())
                .setPositiveButton("Awesome!", null)
                .show();

        scratchCardResult.setText("Last reward: " + reward.description);
        scratchCardResult.setVisibility(View.VISIBLE);
    }

    // ===================== MINING STREAK =====================

    private void updateMiningStreakUI() {
        if (miningStreakManager == null) return;

        MiningStreakManager.StreakStatus status = miningStreakManager.getStreakStatus();

        streakDays.setText(status.getStreakDisplay());
        streakMultiplier.setText(String.format(Locale.getDefault(), "%.1fx Mining Bonus", status.currentMultiplier));

        if (status.nextMilestone != null) {
            streakProgress.setProgress((int) (status.getProgressToNextMilestone() * 100));
            streakNextMilestone.setText(String.format(Locale.getDefault(),
                    "%d days to %s %s",
                    status.daysToNextMilestone,
                    status.nextMilestone.badge,
                    status.nextMilestone.title));
        } else {
            streakProgress.setProgress(100);
            streakNextMilestone.setText("Max streak achieved! 👑");
        }
    }

    // ===================== ACHIEVEMENTS =====================

    private void updateAchievementsUI() {
        if (achievementManager == null) return;

        int unlocked = achievementManager.getUnlockedCount();
        int total = achievementManager.getTotalCount();
        float bonus = achievementManager.getTotalAchievementBoost();

        achievementCount.setText(String.format(Locale.getDefault(), "%d/%d", unlocked, total));
        achievementBonus.setText(String.format(Locale.getDefault(), "+%.0f%% Boost", bonus * 100));
        achievementProgress.setProgress((int) ((float) unlocked / total * 100));
    }

    private void showAchievementsDialog() {
        if (achievementManager == null) return;

        StringBuilder achievements = new StringBuilder();
        for (AchievementManager.Achievement achievement : achievementManager.getAllAchievements()) {
            String status = achievement.isUnlocked() ? "✅" : "🔒";
            achievements.append(status).append(" ")
                    .append(achievement.getType().getIcon()).append(" ")
                    .append(achievement.getType().getTitle()).append("\n")
                    .append("   ").append(achievement.getType().getDescription()).append("\n")
                    .append("   Reward: ").append(achievement.getType().getTokenReward())
                    .append(" LYX + ").append((int)(achievement.getType().getBoostBonus() * 100))
                    .append("% boost\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("🏅 Achievements")
                .setMessage(achievements.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    // ===================== TIMER UPDATES =====================

    private void startTimerUpdates() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateHourlyBonusUI();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllData();
    }
}

