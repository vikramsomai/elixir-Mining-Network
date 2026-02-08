package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

/**
 * MAINNET LAUNCH COUNTDOWN - The Ultimate FOMO Creator
 *
 * This is what makes Pi Network so addictive.
 * Users believe tokens will have real value at mainnet launch.
 * Creates anticipation and daily check-ins.
 *
 * FEATURES:
 * 1. Countdown to "Mainnet Launch"
 * 2. Phases with different mining rates
 * 3. "Pre-mainnet" exclusive rewards
 * 4. Early adopter badges
 * 5. Launch leaderboard position
 *
 * GOOGLE PLAY COMPLIANCE:
 * - DO NOT promise real monetary value
 * - DO NOT mention crypto exchange
 * - Present as "in-app currency"
 * - Use vague terms like "ecosystem launch"
 * - Future value is "planned" not "guaranteed"
 *
 * MESSAGING:
 * - "Mainnet Launch" = "Full Feature Release"
 * - "Token value" = "Ecosystem utility"
 * - "Exchange listing" = "External platform integration"
 */
public class MainnetCountdownManager {
    private static final String TAG = "MainnetCountdown";
    private static final String PREFS_NAME = "mainnet_countdown";

    // Launch date - Set this to a future date
    // This should be controlled from Firebase for flexibility
    private static final long DEFAULT_LAUNCH_TIMESTAMP = 1767225600000L; // Jan 1, 2026 (example)

    private static MainnetCountdownManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long launchTimestamp;
    private CountDownTimer countdownTimer;
    private MainnetUpdateListener listener;

    // Phases
    public enum LaunchPhase {
        PHASE_1("Pioneer Phase", "Earn maximum rewards", 0),
        PHASE_2("Expansion Phase", "Growing community", 1),
        PHASE_3("Pre-Launch Phase", "Preparing for launch", 2),
        PHASE_4("Launch Phase", "Ecosystem going live", 3),
        LAUNCHED("Live", "Ecosystem is live", 4);

        public final String name;
        public final String description;
        public final int order;

        LaunchPhase(String name, String description, int order) {
            this.name = name;
            this.description = description;
            this.order = order;
        }
    }

    public static class MainnetInfo {
        public long launchTimestamp;
        public long remainingMs;
        public String formattedCountdown;
        public int daysRemaining;
        public int hoursRemaining;
        public int minutesRemaining;
        public int secondsRemaining;
        public LaunchPhase currentPhase;
        public float progressPercent;
        public String currentBenefit;
        public String nextMilestone;
        public long usersAtLaunch; // Projected
        public boolean isLaunched;
        public int userPosition; // Early adopter position
        public String badge; // Pioneer, Early Bird, etc.
    }

    public interface MainnetUpdateListener {
        void onCountdownTick(MainnetInfo info);
        void onPhaseChanged(LaunchPhase oldPhase, LaunchPhase newPhase);
        void onLaunched();
    }

    private MainnetCountdownManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        loadLaunchDate();
    }

    public static synchronized MainnetCountdownManager getInstance(Context context) {
        if (instance == null) {
            instance = new MainnetCountdownManager(context);
        }
        return instance;
    }

    public void setListener(MainnetUpdateListener listener) {
        this.listener = listener;
        if (listener != null) {
            startCountdown();
        }
    }

    private void loadLaunchDate() {
        // Try to load from Firebase first
        dbRef.child("config").child("mainnetLaunchDate").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    launchTimestamp = ((Number) snapshot.getValue()).longValue();
                } else {
                    launchTimestamp = DEFAULT_LAUNCH_TIMESTAMP;
                }

                prefs.edit().putLong("launchTimestamp", launchTimestamp).apply();

                if (listener != null) {
                    startCountdown();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error loading launch date", error.toException());
                launchTimestamp = prefs.getLong("launchTimestamp", DEFAULT_LAUNCH_TIMESTAMP);
            }
        });
    }

    private void startCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }

        long remaining = launchTimestamp - System.currentTimeMillis();

        if (remaining <= 0) {
            // Already launched
            if (listener != null) {
                listener.onLaunched();
            }
            return;
        }

        countdownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (listener != null) {
                    listener.onCountdownTick(getMainnetInfo());
                }
            }

            @Override
            public void onFinish() {
                if (listener != null) {
                    listener.onLaunched();
                }
            }
        }.start();
    }

    public MainnetInfo getMainnetInfo() {
        MainnetInfo info = new MainnetInfo();

        info.launchTimestamp = launchTimestamp;
        info.remainingMs = Math.max(0, launchTimestamp - System.currentTimeMillis());
        info.isLaunched = info.remainingMs <= 0;

        // Calculate time components
        long remaining = info.remainingMs;
        info.daysRemaining = (int) (remaining / (24 * 60 * 60 * 1000));
        remaining %= (24 * 60 * 60 * 1000);
        info.hoursRemaining = (int) (remaining / (60 * 60 * 1000));
        remaining %= (60 * 60 * 1000);
        info.minutesRemaining = (int) (remaining / (60 * 1000));
        remaining %= (60 * 1000);
        info.secondsRemaining = (int) (remaining / 1000);

        // Format countdown
        if (info.daysRemaining > 0) {
            info.formattedCountdown = String.format(Locale.US, "%dd %dh %dm %ds",
                    info.daysRemaining, info.hoursRemaining, info.minutesRemaining, info.secondsRemaining);
        } else {
            info.formattedCountdown = String.format(Locale.US, "%dh %dm %ds",
                    info.hoursRemaining, info.minutesRemaining, info.secondsRemaining);
        }

        // Calculate phase
        info.currentPhase = calculateCurrentPhase(info.daysRemaining);

        // Progress (time-based from project start to launch)
        long projectStart = launchTimestamp - (365L * 24 * 60 * 60 * 1000); // Assume 1 year project
        long totalDuration = launchTimestamp - projectStart;
        long elapsed = System.currentTimeMillis() - projectStart;
        info.progressPercent = Math.min(100, Math.max(0, (float) elapsed / totalDuration * 100));

        // Current benefit message
        info.currentBenefit = getCurrentBenefit(info.currentPhase);

        // Next milestone
        info.nextMilestone = getNextMilestone(info.daysRemaining);

        // User position (from saved data)
        info.userPosition = prefs.getInt("userPosition", 10000);

        // Badge
        info.badge = calculateBadge(info.userPosition);

        return info;
    }

    private LaunchPhase calculateCurrentPhase(int daysRemaining) {
        if (daysRemaining <= 0) return LaunchPhase.LAUNCHED;
        if (daysRemaining <= 30) return LaunchPhase.PHASE_4;
        if (daysRemaining <= 90) return LaunchPhase.PHASE_3;
        if (daysRemaining <= 180) return LaunchPhase.PHASE_2;
        return LaunchPhase.PHASE_1;
    }

    private String getCurrentBenefit(LaunchPhase phase) {
        switch (phase) {
            case PHASE_1:
                return "Maximum mining rate active";
            case PHASE_2:
                return "Early adopter bonus active";
            case PHASE_3:
                return "Pre-launch bonus active";
            case PHASE_4:
                return "Final bonus week!";
            default:
                return "Ecosystem is live";
        }
    }

    private String getNextMilestone(int daysRemaining) {
        if (daysRemaining > 180) {
            return "Phase 2 in " + (daysRemaining - 180) + " days";
        } else if (daysRemaining > 90) {
            return "Phase 3 in " + (daysRemaining - 90) + " days";
        } else if (daysRemaining > 30) {
            return "Launch phase in " + (daysRemaining - 30) + " days";
        } else if (daysRemaining > 0) {
            return "LAUNCH in " + daysRemaining + " days!";
        }
        return "Launched!";
    }

    private String calculateBadge(int position) {
        if (position <= 100) return "Founding Member";
        if (position <= 1000) return "Pioneer";
        if (position <= 10000) return "Early Adopter";
        if (position <= 100000) return "Trailblazer";
        return "Member";
    }

    /**
     * Save user's position when they join
     */
    public void recordUserPosition(int position) {
        prefs.edit().putInt("userPosition", position).apply();
    }

    /**
     * Get formatted days until launch
     */
    public String getDaysUntilLaunch() {
        long remaining = launchTimestamp - System.currentTimeMillis();
        int days = (int) (remaining / (24 * 60 * 60 * 1000));

        if (days <= 0) return "Launched!";
        if (days == 1) return "1 day";
        return days + " days";
    }

    /**
     * Get current phase
     */
    public LaunchPhase getCurrentPhase() {
        long remaining = launchTimestamp - System.currentTimeMillis();
        int days = (int) (remaining / (24 * 60 * 60 * 1000));
        return calculateCurrentPhase(days);
    }

    /**
     * Check if in final countdown (last 30 days)
     */
    public boolean isInFinalCountdown() {
        long remaining = launchTimestamp - System.currentTimeMillis();
        int days = (int) (remaining / (24 * 60 * 60 * 1000));
        return days > 0 && days <= 30;
    }

    /**
     * Get urgency message for UI
     */
    public String getUrgencyMessage() {
        MainnetInfo info = getMainnetInfo();

        if (info.isLaunched) {
            return "Ecosystem is LIVE!";
        }

        if (info.daysRemaining <= 7) {
            return "FINAL WEEK! Mine now before launch!";
        }

        if (info.daysRemaining <= 30) {
            return "Launch approaching! Last chance for max rewards!";
        }

        if (info.daysRemaining <= 90) {
            return "Pre-launch phase: Bonus mining active!";
        }

        return "Pioneer phase: Earn maximum rewards!";
    }

    public void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }
}

