package network.lynx.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LoginCalendarActivity - Visual 7-day login streak calendar
 *
 * Features:
 * - Shows 7-day visual calendar
 * - Escalating rewards each day
 * - Weekly completion bonus
 * - Progress tracking
 */
public class LoginCalendarActivity extends AppCompatActivity {
    private static final String TAG = "LoginCalendarActivity";

    private ImageView backButton;
    private TextView weekTitle, weeklyBonusText, totalWeeksText;
    private LinearLayout daysContainer;
    private MaterialButton claimTodayBtn;
    private ProgressBar weekProgress;
    private MaterialCardView weeklyBonusCard;

    private EngagementFeaturesManager engagementManager;
    private EngagementFeaturesManager.LoginCalendarStatus calendarStatus;
    private AdManager adManager;

    // Day views
    private List<View> dayViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_calendar);

        engagementManager = EngagementFeaturesManager.getInstance(this);
        adManager = AdManager.getInstance();

        initViews();
        setupClickListeners();
        loadCalendarData();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        weekTitle = findViewById(R.id.weekTitle);
        weeklyBonusText = findViewById(R.id.weeklyBonusText);
        totalWeeksText = findViewById(R.id.totalWeeksText);
        daysContainer = findViewById(R.id.daysContainer);
        claimTodayBtn = findViewById(R.id.claimTodayBtn);
        weekProgress = findViewById(R.id.weekProgress);
        weeklyBonusCard = findViewById(R.id.weeklyBonusCard);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        claimTodayBtn.setOnClickListener(v -> {
            if (calendarStatus != null) {
                claimTodayReward();
            }
        });
    }

    private void loadCalendarData() {
        engagementManager.getLoginCalendarStatus(new EngagementFeaturesManager.LoginCalendarCallback() {
            @Override
            public void onSuccess(EngagementFeaturesManager.LoginCalendarStatus status) {
                runOnUiThread(() -> {
                    calendarStatus = status;
                    updateUI();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    ToastUtils.showInfo(LoginCalendarActivity.this, "Failed to load calendar: " + error);
                });
            }
        });
    }

    private void updateUI() {
        if (calendarStatus == null) return;

        // Update week title
        weekTitle.setText(String.format(Locale.US, "Week %d", calendarStatus.weekNumber));

        // Update progress
        int claimedDays = 0;
        for (EngagementFeaturesManager.LoginCalendarDay day : calendarStatus.days) {
            if (day.claimed) claimedDays++;
        }
        weekProgress.setProgress((claimedDays * 100) / 7);

        // Update weekly bonus
        weeklyBonusText.setText(String.format(Locale.US, "+%.0f LYX", calendarStatus.weeklyBonusAmount));

        if (calendarStatus.totalWeeksCompleted > 0) {
            totalWeeksText.setText(String.format(Locale.US,
                "🏆 %d weeks completed!", calendarStatus.totalWeeksCompleted));
            totalWeeksText.setVisibility(View.VISIBLE);
        }

        // Build day views
        buildDayViews();

        // Update claim button
        updateClaimButton();
    }

    private void buildDayViews() {
        daysContainer.removeAllViews();
        dayViews.clear();

        for (EngagementFeaturesManager.LoginCalendarDay day : calendarStatus.days) {
            View dayView = getLayoutInflater().inflate(R.layout.item_calendar_day, daysContainer, false);

            TextView dayNumber = dayView.findViewById(R.id.dayNumber);
            TextView dayReward = dayView.findViewById(R.id.dayReward);
            ImageView dayCheck = dayView.findViewById(R.id.dayCheck);
            View dayCard = dayView.findViewById(R.id.dayCard);

            dayNumber.setText(String.format(Locale.US, "Day %d", day.dayNumber));
            dayReward.setText(String.format(Locale.US, "+%.0f", day.reward));

            if (day.claimed) {
                // Already claimed
                dayCard.setAlpha(0.6f);
                dayCheck.setVisibility(View.VISIBLE);
                dayReward.setTextColor(getResources().getColor(R.color.accentGreen, null));
            } else if (day.isToday) {
                // Can claim today
                dayCard.setBackgroundResource(R.drawable.calendar_day_today);
                dayReward.setTextColor(getResources().getColor(R.color.gold, null));
            } else if (day.isLocked) {
                // Locked (future day)
                dayCard.setAlpha(0.4f);
                dayReward.setTextColor(getResources().getColor(R.color.textTertiary, null));
            }

            daysContainer.addView(dayView);
            dayViews.add(dayView);
        }
    }

    private void updateClaimButton() {
        // Find today's day
        EngagementFeaturesManager.LoginCalendarDay todayDay = null;
        for (EngagementFeaturesManager.LoginCalendarDay day : calendarStatus.days) {
            if (day.isToday) {
                todayDay = day;
                break;
            }
        }

        if (todayDay != null && !todayDay.claimed) {
            claimTodayBtn.setEnabled(true);
            claimTodayBtn.setText(String.format(Locale.US,
                "Claim Day %d (+%.0f LYX)", todayDay.dayNumber, todayDay.reward));
        } else {
            claimTodayBtn.setEnabled(false);
            claimTodayBtn.setText("Come Back Tomorrow!");
        }
    }

    private void claimTodayReward() {
        claimTodayBtn.setEnabled(false);
        claimTodayBtn.setText("Loading Ad...");

        // Show ad before claiming
        AdConsentManager.showCheckInConsentDialog(this, new AdConsentManager.ConsentCallback() {
            @Override
            public void onConsentGiven() {
                adManager.showRewardedAd(LoginCalendarActivity.this, "calendar_checkin",
                    new AdManager.AdShowCallback() {
                        @Override
                        public void onAdShowed() {}

                        @Override
                        public void onAdShowFailed(String error) {
                            // Still grant reward on ad failure
                            performClaim();
                        }

                        @Override
                        public void onAdDismissed() {
                            updateClaimButton();
                        }

                        @Override
                        public void onAdNotAvailable() {
                            performClaim();
                        }

                        @Override
                        public void onUserEarnedReward(com.google.android.gms.ads.rewarded.RewardItem rewardItem) {
                            performClaim();
                        }
                    });
            }

            @Override
            public void onConsentDenied() {
                updateClaimButton();
            }
        });
    }

    private void performClaim() {
        if (calendarStatus == null) return;

        engagementManager.claimDailyLoginReward(calendarStatus.currentDay,
            new EngagementFeaturesManager.RewardCallback() {
                @Override
                public void onSuccess(double reward, String message) {
                    runOnUiThread(() -> {
                        ToastUtils.showInfo(LoginCalendarActivity.this, message);
                        loadCalendarData(); // Refresh
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        ToastUtils.showInfo(LoginCalendarActivity.this, error);
                        updateClaimButton();
                    });
                }
            });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCalendarData();
    }
}

