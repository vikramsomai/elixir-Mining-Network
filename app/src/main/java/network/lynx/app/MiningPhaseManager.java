package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

/**
 * MINING PHASES & HALVING SYSTEM - Inspired by Pi Network & Bitcoin
 *
 * Creates FOMO and urgency by implementing phases and halving.
 * Users want to mine NOW before rate decreases.
 */
public class MiningPhaseManager {
    private static final String TAG = "MiningPhaseManager";
    private static final String PREFS_NAME = "mining_phase";

    // Phase thresholds
    public static final long PHASE_1_THRESHOLD = 100_000;
    public static final long PHASE_2_THRESHOLD = 1_000_000;
    public static final long PHASE_3_THRESHOLD = 10_000_000;
    public static final long PHASE_4_THRESHOLD = 100_000_000;

    // Base rates per phase (LYX per hour)
    public static final double PHASE_1_RATE = 2.0;
    public static final double PHASE_2_RATE = 1.0;
    public static final double PHASE_3_RATE = 0.5;
    public static final double PHASE_4_RATE = 0.25;

    // Phase names
    public static final String PHASE_1_NAME = "Pioneer";
    public static final String PHASE_2_NAME = "Contributor";
    public static final String PHASE_3_NAME = "Ambassador";
    public static final String PHASE_4_NAME = "Node";

    private static MiningPhaseManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;

    private long totalUsers = 0;
    private int currentPhase = 1;
    private double currentBaseRate = PHASE_1_RATE;
    private PhaseUpdateListener listener;

    public static class PhaseInfo {
        public int phase;
        public String phaseName;
        public double baseRate;
        public long currentUsers;
        public long usersToNextPhase;
        public float progressToNextPhase;
        public long nextPhaseThreshold;
        public double nextPhaseRate;
        public String nextPhaseName;
        public long userJoinPosition;
        public float earlyAdopterBonus;
    }

    public interface PhaseUpdateListener {
        void onPhaseUpdated(PhaseInfo info);
        void onPhaseChanged(int oldPhase, int newPhase);
    }

    private MiningPhaseManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        loadCachedData();
        startListening();
    }

    public static synchronized MiningPhaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new MiningPhaseManager(context);
        }
        return instance;
    }

    public void setListener(PhaseUpdateListener listener) {
        this.listener = listener;
    }

    private void loadCachedData() {
        totalUsers = prefs.getLong("totalUsers", 1000);
        currentPhase = prefs.getInt("currentPhase", 1);
        currentBaseRate = prefs.getFloat("currentBaseRate", (float) PHASE_1_RATE);
    }

    private void startListening() {
        dbRef.child("stats").child("totalUsers").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    long newTotal = ((Number) snapshot.getValue()).longValue();

                    if (newTotal != totalUsers) {
                        totalUsers = newTotal;
                        int oldPhase = currentPhase;
                        updatePhase();

                        if (currentPhase != oldPhase && listener != null) {
                            listener.onPhaseChanged(oldPhase, currentPhase);
                        }

                        prefs.edit()
                                .putLong("totalUsers", totalUsers)
                                .putInt("currentPhase", currentPhase)
                                .putFloat("currentBaseRate", (float) currentBaseRate)
                                .apply();

                        notifyListener();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening to user count", error.toException());
            }
        });
    }

    private void updatePhase() {
        if (totalUsers < PHASE_1_THRESHOLD) {
            currentPhase = 1;
            currentBaseRate = PHASE_1_RATE;
        } else if (totalUsers < PHASE_2_THRESHOLD) {
            currentPhase = 2;
            currentBaseRate = PHASE_2_RATE;
        } else if (totalUsers < PHASE_3_THRESHOLD) {
            currentPhase = 3;
            currentBaseRate = PHASE_3_RATE;
        } else {
            currentPhase = 4;
            currentBaseRate = PHASE_4_RATE;
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onPhaseUpdated(getPhaseInfo());
        }
    }

    public PhaseInfo getPhaseInfo() {
        PhaseInfo info = new PhaseInfo();

        info.phase = currentPhase;
        info.phaseName = getPhaseName(currentPhase);
        info.baseRate = currentBaseRate;
        info.currentUsers = totalUsers;

        switch (currentPhase) {
            case 1:
                info.nextPhaseThreshold = PHASE_1_THRESHOLD;
                info.nextPhaseRate = PHASE_2_RATE;
                info.nextPhaseName = PHASE_2_NAME;
                break;
            case 2:
                info.nextPhaseThreshold = PHASE_2_THRESHOLD;
                info.nextPhaseRate = PHASE_3_RATE;
                info.nextPhaseName = PHASE_3_NAME;
                break;
            case 3:
                info.nextPhaseThreshold = PHASE_3_THRESHOLD;
                info.nextPhaseRate = PHASE_4_RATE;
                info.nextPhaseName = PHASE_4_NAME;
                break;
            default:
                info.nextPhaseThreshold = PHASE_4_THRESHOLD;
                info.nextPhaseRate = PHASE_4_RATE;
                info.nextPhaseName = "Final";
                break;
        }

        info.usersToNextPhase = Math.max(0, info.nextPhaseThreshold - totalUsers);

        long prevThreshold = getPreviousThreshold(currentPhase);
        long range = info.nextPhaseThreshold - prevThreshold;
        long progress = totalUsers - prevThreshold;
        info.progressToNextPhase = range > 0 ? (float) progress / range * 100 : 100;

        info.userJoinPosition = getUserJoinPosition();
        info.earlyAdopterBonus = calculateEarlyAdopterBonus(info.userJoinPosition);

        return info;
    }

    private String getPhaseName(int phase) {
        switch (phase) {
            case 1: return PHASE_1_NAME;
            case 2: return PHASE_2_NAME;
            case 3: return PHASE_3_NAME;
            default: return PHASE_4_NAME;
        }
    }

    private long getPreviousThreshold(int phase) {
        switch (phase) {
            case 2: return PHASE_1_THRESHOLD;
            case 3: return PHASE_2_THRESHOLD;
            case 4: return PHASE_3_THRESHOLD;
            default: return 0;
        }
    }

    private long getUserJoinPosition() {
        return prefs.getLong("userJoinPosition", totalUsers);
    }

    public void recordUserJoinPosition() {
        if (!prefs.contains("userJoinPosition")) {
            prefs.edit().putLong("userJoinPosition", totalUsers).apply();

            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                dbRef.child("stats").child("totalUsers").setValue(totalUsers + 1);
            }
        }
    }

    private float calculateEarlyAdopterBonus(long position) {
        if (position < 1000) {
            return 1.0f;
        } else if (position < 10000) {
            return 0.5f;
        } else if (position < 100000) {
            return 0.25f;
        } else if (position < 1000000) {
            return 0.10f;
        }
        return 0f;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public double getCurrentBaseRate() {
        return currentBaseRate;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public float getEarlyAdopterBonus() {
        return calculateEarlyAdopterBonus(getUserJoinPosition());
    }

    public float getTotalPhaseMultiplier() {
        return 1.0f + getEarlyAdopterBonus();
    }

    public String getUsersToNextPhaseFormatted() {
        PhaseInfo info = getPhaseInfo();
        if (info.usersToNextPhase >= 1000000) {
            return String.format(Locale.US, "%.1fM", info.usersToNextPhase / 1000000.0);
        } else if (info.usersToNextPhase >= 1000) {
            return String.format(Locale.US, "%.1fK", info.usersToNextPhase / 1000.0);
        }
        return String.valueOf(info.usersToNextPhase);
    }
}

