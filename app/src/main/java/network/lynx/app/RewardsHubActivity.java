package network.lynx.app;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

/**
 * Rewards Hub Activity - ENHANCED VERSION
 * Central place for all reward features:
 * - Daily Check-in Calendar (NEW)
 * - Daily Challenges (NEW)
 * - Spin Wheel (NEW)
 * - Multiplier Rush Events (NEW)
 * - Mystery Box (NEW)
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

    // NEW: Daily Check-in
    private CardView checkInCard;
    private LinearLayout checkInDaysLayout;
    private TextView checkInStreakText;
    private TextView checkInRewardText;
    private Button checkInButton;
    private Button weeklyBonusButton;

    // NEW: Daily Challenges
    private CardView dailyChallengesCard;
    private LinearLayout challengesLayout;
    private TextView challengesProgress;
    private ProgressBar challengesProgressBar;

    // NEW: Spin Wheel - Simplified, now redirects to spinActivity
    private CardView spinWheelCard;
    private TextView spinsRemaining;
    private Button spinButton;

    // NEW: Multiplier Rush
    private CardView multiplierRushCard;
    private TextView rushEventName;
    private TextView rushMultiplier;
    private TextView rushTimeRemaining;
    private View rushActiveIndicator;

    // NEW: Mystery Box
    private CardView mysteryBoxCard;
    private Button bronzeBoxBtn;
    private Button silverBoxBtn;
    private Button goldBoxBtn;
    private TextView bronzeCount;
    private TextView silverCount;
    private TextView goldCount;

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
    // DailyLuckyNumberManager removed - handled by DailyGamesActivity
    private ScratchCardManager scratchCardManager;
    private MiningStreakManager miningStreakManager;
    private AchievementManager achievementManager;
    private AdManager adManager;
    private DailyEventsManager dailyEventsManager; // NEW

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
            // DailyLuckyNumberManager removed - handled by DailyGamesActivity
            scratchCardManager = ScratchCardManager.getInstance(this);
            miningStreakManager = MiningStreakManager.getInstance(this);
            achievementManager = AchievementManager.getInstance(this);
            adManager = AdManager.getInstance();
            dailyEventsManager = DailyEventsManager.getInstance(this); // NEW
        } catch (Exception e) {
            Log.e(TAG, "Error initializing managers", e);
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backNav);

        // NEW: Daily Check-in
        checkInCard = findViewById(R.id.checkInCard);
        checkInDaysLayout = findViewById(R.id.checkInDaysLayout);
        checkInStreakText = findViewById(R.id.checkInStreakText);
        checkInRewardText = findViewById(R.id.checkInRewardText);
        checkInButton = findViewById(R.id.checkInButton);
        weeklyBonusButton = findViewById(R.id.weeklyBonusButton);

        // NEW: Daily Challenges
        dailyChallengesCard = findViewById(R.id.dailyChallengesCard);
        challengesLayout = findViewById(R.id.challengesLayout);
        challengesProgress = findViewById(R.id.challengesProgress);
        challengesProgressBar = findViewById(R.id.challengesProgressBar);

        // NEW: Spin Wheel - Simplified, now redirects to spinActivity
        spinWheelCard = findViewById(R.id.spinWheelCard);
        spinsRemaining = findViewById(R.id.spinsRemaining);
        spinButton = findViewById(R.id.spinButton);

        // NEW: Multiplier Rush
        multiplierRushCard = findViewById(R.id.multiplierRushCard);
        rushEventName = findViewById(R.id.rushEventName);
        rushMultiplier = findViewById(R.id.rushMultiplier);
        rushTimeRemaining = findViewById(R.id.rushTimeRemaining);
        rushActiveIndicator = findViewById(R.id.rushActiveIndicator);

        // NEW: Mystery Box
        mysteryBoxCard = findViewById(R.id.mysteryBoxCard);
        bronzeBoxBtn = findViewById(R.id.bronzeBoxBtn);
        silverBoxBtn = findViewById(R.id.silverBoxBtn);
        goldBoxBtn = findViewById(R.id.goldBoxBtn);
        bronzeCount = findViewById(R.id.bronzeCount);
        silverCount = findViewById(R.id.silverCount);
        goldCount = findViewById(R.id.goldCount);

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

        // NEW: Daily Check-in
        if (checkInButton != null) {
            checkInButton.setOnClickListener(v -> performCheckIn());
        }
        if (weeklyBonusButton != null) {
            weeklyBonusButton.setOnClickListener(v -> claimWeeklyBonus());
        }

        // NEW: Spin Wheel - Navigate to dedicated activity instead of duplicating
        if (spinButton != null) {
            spinButton.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, spinActivity.class));
            });
        }
        // Also make the whole card clickable
        if (spinWheelCard != null) {
            spinWheelCard.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, spinActivity.class));
            });
        }

        // NEW: Mystery Box
        if (bronzeBoxBtn != null) {
            bronzeBoxBtn.setOnClickListener(v -> showMysteryBoxAdConsent(DailyEventsManager.MysteryBoxTier.BRONZE));
        }
        if (silverBoxBtn != null) {
            silverBoxBtn.setOnClickListener(v -> showMysteryBoxAdConsent(DailyEventsManager.MysteryBoxTier.SILVER));
        }
        if (goldBoxBtn != null) {
            goldBoxBtn.setOnClickListener(v -> showMysteryBoxAdConsent(DailyEventsManager.MysteryBoxTier.GOLD));
        }

        // Hourly Bonus
        claimHourlyBonusBtn.setOnClickListener(v -> claimHourlyBonus());

        // Lucky Number - Navigate to Daily Games instead of duplicating
        playLuckyNumberBtn.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, DailyGamesActivity.class));
        });
        if (luckyNumberCard != null) {
            luckyNumberCard.setOnClickListener(v -> {
                startActivity(new android.content.Intent(this, DailyGamesActivity.class));
            });
        }

        // Scratch Card
        earnScratchCardBtn.setOnClickListener(v -> earnScratchCard());
        scratchCardBtn.setOnClickListener(v -> scratchCard());

        // Achievements
        viewAchievementsBtn.setOnClickListener(v -> showAchievementsDialog());
    }

    private void loadAllData() {
        updateCheckInUI();           // NEW
        updateDailyChallengesUI();   // NEW
        updateSpinWheelUI();         // NEW
        updateMultiplierRushUI();    // NEW
        updateMysteryBoxUI();        // NEW
        updateHourlyBonusUI();
        updateLuckyNumberUI();
        updateScratchCardUI();
        updateMiningStreakUI();
        updateAchievementsUI();
    }

    // ===================== NEW: DAILY CHECK-IN =====================

    private void updateCheckInUI() {
        if (dailyEventsManager == null) return;

        DailyEventsManager.CheckInStatus status = dailyEventsManager.getCheckInStatus();

        if (checkInStreakText != null) {
            checkInStreakText.setText(String.format(Locale.US, "%d Day Streak", status.currentStreak));
        }

        if (checkInRewardText != null) {
            checkInRewardText.setText(String.format(Locale.US, "Today: +%.1f LYX", status.todayReward));
        }

        if (checkInButton != null) {
            if (status.checkedInToday) {
                checkInButton.setEnabled(false);
                checkInButton.setText("Checked In!");
            } else {
                checkInButton.setEnabled(true);
                checkInButton.setText("Check In Now");
            }
        }

        if (weeklyBonusButton != null) {
            if (status.canClaimWeeklyBonus) {
                weeklyBonusButton.setVisibility(View.VISIBLE);
                weeklyBonusButton.setEnabled(true);
                weeklyBonusButton.setText(String.format(Locale.US, "Claim +%.0f LYX Weekly Bonus!", status.weeklyBonusReward));
            } else {
                weeklyBonusButton.setVisibility(View.GONE);
            }
        }

        // Update check-in calendar
        if (checkInDaysLayout != null) {
            checkInDaysLayout.removeAllViews();
            String[] dayNames = {"S", "M", "T", "W", "T", "F", "S"};
            for (int i = 0; i < 7; i++) {
                TextView dayView = new TextView(this);
                dayView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                dayView.setGravity(android.view.Gravity.CENTER);
                dayView.setPadding(8, 8, 8, 8);

                if (status.weeklyCheckIns[i] == 1) {
                    dayView.setText("✓");
                    dayView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                } else if (status.weeklyCheckIns[i] == 2) {
                    dayView.setText(dayNames[i]);
                    dayView.setTextColor(ContextCompat.getColor(this, R.color.gold));
                } else {
                    dayView.setText(dayNames[i]);
                    dayView.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
                }
                checkInDaysLayout.addView(dayView);
            }
        }
    }

    private void performCheckIn() {
        if (dailyEventsManager == null) return;

        if (checkInButton != null) {
            checkInButton.setEnabled(false);
            checkInButton.setText("Checking in...");
        }

        dailyEventsManager.performCheckIn(new DailyEventsManager.CheckInCallback() {
            @Override
            public void onCheckInSuccess(double reward, int newStreak) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this,
                            String.format(Locale.US, "Check-in complete! +%.1f LYX (Day %d)", reward, newStreak));
                    updateCheckInUI();
                    updateDailyChallengesUI();
                });
            }

            @Override
            public void onAlreadyCheckedIn() {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "Already checked in today!");
                    updateCheckInUI();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ToastUtils.showError(RewardsHubActivity.this, message);
                    updateCheckInUI();
                });
            }
        });
    }

    private void claimWeeklyBonus() {
        if (dailyEventsManager == null) return;

        dailyEventsManager.claimWeeklyBonus(new DailyEventsManager.CheckInCallback() {
            @Override
            public void onCheckInSuccess(double reward, int newStreak) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this,
                            String.format(Locale.US, "Weekly bonus claimed! +%.0f LYX", reward));
                    updateCheckInUI();
                });
            }

            @Override
            public void onAlreadyCheckedIn() {
                runOnUiThread(() -> updateCheckInUI());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ToastUtils.showError(RewardsHubActivity.this, message);
                    updateCheckInUI();
                });
            }
        });
    }

    // ===================== NEW: DAILY CHALLENGES =====================

    private void updateDailyChallengesUI() {
        if (dailyEventsManager == null) return;

        List<DailyEventsManager.DailyChallenge> challenges = dailyEventsManager.getDailyChallenges();
        int completed = dailyEventsManager.getCompletedChallengesCount();

        if (challengesProgress != null) {
            challengesProgress.setText(String.format(Locale.US, "%d/3 Completed", completed));
        }

        if (challengesProgressBar != null) {
            challengesProgressBar.setProgress((completed * 100) / 3);
        }

        if (challengesLayout != null) {
            challengesLayout.removeAllViews();
            for (DailyEventsManager.DailyChallenge challenge : challenges) {
                View challengeView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, challengesLayout, false);

                TextView title = challengeView.findViewById(android.R.id.text1);
                TextView subtitle = challengeView.findViewById(android.R.id.text2);

                String status = challenge.claimed ? "✓ Claimed" : (challenge.completed ? "✓ Complete" : challenge.getProgressText());
                title.setText(String.format("%s %s - %s", challenge.type.icon, challenge.type.title, status));
                subtitle.setText(String.format(Locale.US, "%s | Reward: %.1f LYX", challenge.type.description, challenge.reward));

                if (challenge.completed && !challenge.claimed) {
                    challengeView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimaryLight));
                    challengeView.setOnClickListener(v -> claimChallengeReward(challenge));
                }

                challengesLayout.addView(challengeView);
            }
        }
    }

    private void claimChallengeReward(DailyEventsManager.DailyChallenge challenge) {
        dailyEventsManager.claimChallengeReward(challenge, new DailyEventsManager.ChallengeClaimCallback() {
            @Override
            public void onClaimed(double reward) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this,
                            String.format(Locale.US, "Challenge complete! +%.1f LYX", reward));
                    updateDailyChallengesUI();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> ToastUtils.showError(RewardsHubActivity.this, message));
            }
        });
    }

    // ===================== NEW: SPIN WHEEL =====================

    private void updateSpinWheelUI() {
        // Simplified - just show navigation prompt since spin is handled in dedicated spinActivity
        if (spinsRemaining != null) {
            spinsRemaining.setText("Tap to open Spin Wheel");
        }

        if (spinButton != null) {
            spinButton.setEnabled(true);
            spinButton.setText("🎰 Go to Spin Wheel →");
        }
    }

    // Spin wheel methods removed - now handled by spinActivity

    // ===================== NEW: MULTIPLIER RUSH =====================

    private void updateMultiplierRushUI() {
        if (dailyEventsManager == null) return;

        DailyEventsManager.MultiplierRushStatus status = dailyEventsManager.getMultiplierRushStatus();

        if (rushEventName != null) {
            rushEventName.setText(status.eventName);
        }

        if (rushMultiplier != null) {
            rushMultiplier.setText(String.format(Locale.US, "%.1fx", status.multiplier));
        }

        if (rushTimeRemaining != null) {
            if (status.isActive) {
                rushTimeRemaining.setText("Ends in: " + status.getTimeRemainingFormatted());
            } else {
                rushTimeRemaining.setText("Next: " + status.getTimeRemainingFormatted());
            }
        }

        if (rushActiveIndicator != null) {
            rushActiveIndicator.setBackgroundColor(status.isActive ?
                    ContextCompat.getColor(this, R.color.colorPrimary) :
                    ContextCompat.getColor(this, R.color.textSecondary));
        }

        if (multiplierRushCard != null) {
            multiplierRushCard.setCardBackgroundColor(status.isActive ?
                    ContextCompat.getColor(this, R.color.colorPrimaryLight) :
                    ContextCompat.getColor(this, R.color.cardBackground));
        }
    }

    // ===================== NEW: MYSTERY BOX =====================

    private void updateMysteryBoxUI() {
        if (dailyEventsManager == null) return;

        // Bronze
        int bronzeRemaining = dailyEventsManager.getMysteryBoxesRemaining(DailyEventsManager.MysteryBoxTier.BRONZE);
        if (bronzeCount != null) bronzeCount.setText(String.valueOf(bronzeRemaining));
        if (bronzeBoxBtn != null) bronzeBoxBtn.setEnabled(bronzeRemaining > 0);

        // Silver
        int silverRemaining = dailyEventsManager.getMysteryBoxesRemaining(DailyEventsManager.MysteryBoxTier.SILVER);
        if (silverCount != null) silverCount.setText(String.valueOf(silverRemaining));
        if (silverBoxBtn != null) silverBoxBtn.setEnabled(silverRemaining > 0);

        // Gold
        int goldRemaining = dailyEventsManager.getMysteryBoxesRemaining(DailyEventsManager.MysteryBoxTier.GOLD);
        if (goldCount != null) goldCount.setText(String.valueOf(goldRemaining));
        if (goldBoxBtn != null) goldBoxBtn.setEnabled(goldRemaining > 0);
    }

    private void showMysteryBoxAdConsent(DailyEventsManager.MysteryBoxTier tier) {
        AdConsentManager.showCheckInConsentDialog(this, new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                showAdForMysteryBox(tier);
            }

            @Override
            public void onConsentDenied() {
                ToastUtils.showInfo(RewardsHubActivity.this, "Watch an ad to open the mystery box!");
            }
        });
    }

    private void showAdForMysteryBox(DailyEventsManager.MysteryBoxTier tier) {
        adManager.showRewardedAd(this, AdManager.AD_UNIT_BOOST, new AdManager.AdShowCallback() {
            @Override
            public void onAdShowed() {}

            @Override
            public void onAdShowFailed(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "Ad failed. Try again.");
                    updateMysteryBoxUI();
                });
            }

            @Override
            public void onAdDismissed() {
                runOnUiThread(() -> updateMysteryBoxUI());
            }

            @Override
            public void onAdNotAvailable() {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(RewardsHubActivity.this, "No ads available. Try later.");
                    updateMysteryBoxUI();
                });
            }

            @Override
            public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                openMysteryBox(tier);
            }
        });
    }

    private void openMysteryBox(DailyEventsManager.MysteryBoxTier tier) {
        if (dailyEventsManager == null) return;

        dailyEventsManager.openMysteryBox(tier, new DailyEventsManager.MysteryBoxCallback() {
            @Override
            public void onBoxOpened(DailyEventsManager.MysteryBoxResult result) {
                runOnUiThread(() -> {
                    showMysteryBoxResult(result);
                    updateMysteryBoxUI();
                    updateDailyChallengesUI();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    ToastUtils.showError(RewardsHubActivity.this, message);
                    updateMysteryBoxUI();
                });
            }
        });
    }

    private void showMysteryBoxResult(DailyEventsManager.MysteryBoxResult result) {
        new AlertDialog.Builder(this)
                .setTitle(result.tier.icon + " " + result.tier.name + " Opened!")
                .setMessage(result.message)
                .setPositiveButton("Awesome!", null)
                .show();
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
        // Simplified - redirect to DailyGamesActivity for better experience
        if (luckyNumberStatus != null) {
            luckyNumberStatus.setText("Daily prediction game, coin flip & more!");
        }

        if (playLuckyNumberBtn != null) {
            playLuckyNumberBtn.setEnabled(true);
            playLuckyNumberBtn.setText("🎮 Play Mini Games →");
        }

        if (luckyNumberResult != null) {
            luckyNumberResult.setVisibility(View.GONE);
        }
    }

    // Lucky Number methods removed - now handled by DailyGamesActivity

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

