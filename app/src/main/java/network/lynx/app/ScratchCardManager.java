package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Scratch Card Rewards System
 * Daily scratch cards with random rewards
 * Watch ad to get a scratch card, reveal rewards by scratching
 */
public class ScratchCardManager {
    private static final String TAG = "ScratchCardManager";
    private static ScratchCardManager instance;

    private static final int MAX_CARDS_PER_DAY = 3;

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private String userId;
    private List<ScratchCardListener> listeners = new ArrayList<>();

    // Reward types with probabilities
    public enum RewardType {
        LYX_SMALL(30, "💰", "LYX Tokens"),        // 30% chance
        LYX_MEDIUM(15, "💰💰", "LYX Tokens"),     // 15% chance
        LYX_LARGE(5, "💰💰💰", "LYX Jackpot"),    // 5% chance
        SPIN_TICKET(20, "🎰", "Spin Ticket"),      // 20% chance
        BOOST_1H(15, "⚡", "1 Hour Boost"),        // 15% chance
        BOOST_4H(8, "⚡⚡", "4 Hour Boost"),       // 8% chance
        MYSTERY_BOX(5, "📦", "Mystery Box"),       // 5% chance
        BETTER_LUCK(2, "🍀", "Better Luck Next Time"); // 2% chance - minimal reward

        private final int probability;
        private final String icon;
        private final String displayName;

        RewardType(int probability, String icon, String displayName) {
            this.probability = probability;
            this.icon = icon;
            this.displayName = displayName;
        }

        public int getProbability() { return probability; }
        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
    }

    public static class ScratchCardReward {
        public RewardType type;
        public float value; // Amount of LYX or boost duration in hours
        public String description;
        public long timestamp;

        public ScratchCardReward(RewardType type, float value, String description) {
            this.type = type;
            this.value = value;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }

        public String getDisplayText() {
            return type.getIcon() + " " + description;
        }
    }

    public static class CardStatus {
        public int cardsAvailable;
        public int cardsUsedToday;
        public int maxCardsPerDay;
        public boolean hasUnrevealedCard;
        public ScratchCardReward pendingReward;

        public CardStatus(int cardsAvailable, int cardsUsedToday, int maxCardsPerDay,
                         boolean hasUnrevealedCard, ScratchCardReward pendingReward) {
            this.cardsAvailable = cardsAvailable;
            this.cardsUsedToday = cardsUsedToday;
            this.maxCardsPerDay = maxCardsPerDay;
            this.hasUnrevealedCard = hasUnrevealedCard;
            this.pendingReward = pendingReward;
        }
    }

    public interface ScratchCardListener {
        void onCardRevealed(ScratchCardReward reward);
        void onCardEarned();
        void onNoCardsAvailable(int timeUntilReset);
    }

    private ScratchCardManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized ScratchCardManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScratchCardManager(context);
        }
        return instance;
    }

    private void initialize() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in");
                return;
            }
            userId = auth.getCurrentUser().getUid();
            prefs = context.getSharedPreferences("ScratchCard_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            resetDailyCardsIfNeeded();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ScratchCardManager", e);
        }
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    private void resetDailyCardsIfNeeded() {
        String today = getTodayDate();
        String lastResetDate = prefs.getString("lastResetDate", "");

        if (!today.equals(lastResetDate)) {
            prefs.edit()
                    .putString("lastResetDate", today)
                    .putInt("cardsUsedToday", 0)
                    .putInt("cardsAvailable", 0)
                    .putBoolean("hasUnrevealedCard", false)
                    .apply();
            Log.d(TAG, "Daily scratch cards reset for new day");
        }
    }

    public CardStatus getCardStatus() {
        resetDailyCardsIfNeeded();

        int cardsAvailable = prefs.getInt("cardsAvailable", 0);
        int cardsUsedToday = prefs.getInt("cardsUsedToday", 0);
        boolean hasUnrevealedCard = prefs.getBoolean("hasUnrevealedCard", false);

        ScratchCardReward pendingReward = null;
        if (hasUnrevealedCard) {
            String rewardTypeStr = prefs.getString("pendingRewardType", "");
            float rewardValue = prefs.getFloat("pendingRewardValue", 0);
            String rewardDesc = prefs.getString("pendingRewardDesc", "");
            if (!rewardTypeStr.isEmpty()) {
                try {
                    RewardType type = RewardType.valueOf(rewardTypeStr);
                    pendingReward = new ScratchCardReward(type, rewardValue, rewardDesc);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing pending reward", e);
                }
            }
        }

        return new CardStatus(cardsAvailable, cardsUsedToday, MAX_CARDS_PER_DAY,
                hasUnrevealedCard, pendingReward);
    }

    public boolean canEarnCard() {
        CardStatus status = getCardStatus();
        return (status.cardsUsedToday + status.cardsAvailable) < MAX_CARDS_PER_DAY;
    }

    public boolean hasCardsToScratch() {
        CardStatus status = getCardStatus();
        return status.cardsAvailable > 0 || status.hasUnrevealedCard;
    }

    // Call this after user watches an ad to earn a scratch card
    public void earnCard(EarnCallback callback) {
        if (!canEarnCard()) {
            callback.onEarnFailed("Maximum cards reached for today!");
            return;
        }

        int currentCards = prefs.getInt("cardsAvailable", 0);
        prefs.edit().putInt("cardsAvailable", currentCards + 1).apply();

        callback.onCardEarned(currentCards + 1);
        notifyCardEarned();
        Log.d(TAG, "Scratch card earned! Total available: " + (currentCards + 1));
    }

    // Scratch/reveal a card
    public void scratchCard(ScratchCallback callback) {
        CardStatus status = getCardStatus();

        // If there's a pending unrevealed card, reveal it
        if (status.hasUnrevealedCard && status.pendingReward != null) {
            applyReward(status.pendingReward);
            prefs.edit()
                    .putBoolean("hasUnrevealedCard", false)
                    .remove("pendingRewardType")
                    .remove("pendingRewardValue")
                    .remove("pendingRewardDesc")
                    .apply();
            callback.onCardReady(status.pendingReward);
            notifyCardRevealed(status.pendingReward);
            return;
        }

        if (status.cardsAvailable <= 0) {
            callback.onNoCards("No scratch cards available. Watch an ad to earn one!");
            return;
        }

        // Generate reward
        ScratchCardReward reward = generateReward();

        // Decrease available cards, increase used count
        int newAvailable = status.cardsAvailable - 1;
        int newUsed = status.cardsUsedToday + 1;

        // Store as pending (for scratch animation)
        prefs.edit()
                .putInt("cardsAvailable", newAvailable)
                .putInt("cardsUsedToday", newUsed)
                .putBoolean("hasUnrevealedCard", true)
                .putString("pendingRewardType", reward.type.name())
                .putFloat("pendingRewardValue", reward.value)
                .putString("pendingRewardDesc", reward.description)
                .apply();

        // For immediate reveal (no animation), call this:
        // applyReward(reward);

        callback.onCardReady(reward);
    }

    // Reveal and apply the reward
    public void revealCard(RevealCallback callback) {
        CardStatus status = getCardStatus();

        if (!status.hasUnrevealedCard || status.pendingReward == null) {
            callback.onRevealFailed("No card to reveal");
            return;
        }

        ScratchCardReward reward = status.pendingReward;
        applyReward(reward);

        prefs.edit()
                .putBoolean("hasUnrevealedCard", false)
                .remove("pendingRewardType")
                .remove("pendingRewardValue")
                .remove("pendingRewardDesc")
                .apply();

        // Save to Firebase history
        saveRewardHistory(reward);

        callback.onCardRevealed(reward);
        notifyCardRevealed(reward);
    }

    private ScratchCardReward generateReward() {
        Random random = new Random();
        int roll = random.nextInt(100);

        int cumulative = 0;
        RewardType selectedType = RewardType.BETTER_LUCK;

        for (RewardType type : RewardType.values()) {
            cumulative += type.getProbability();
            if (roll < cumulative) {
                selectedType = type;
                break;
            }
        }

        float value;
        String description;

        switch (selectedType) {
            case LYX_SMALL:
                value = 1 + random.nextFloat() * 4; // 1-5 LYX
                description = String.format(Locale.getDefault(), "%.1f LYX", value);
                break;
            case LYX_MEDIUM:
                value = 5 + random.nextFloat() * 15; // 5-20 LYX
                description = String.format(Locale.getDefault(), "%.1f LYX", value);
                break;
            case LYX_LARGE:
                value = 20 + random.nextFloat() * 80; // 20-100 LYX
                description = String.format(Locale.getDefault(), "%.1f LYX JACKPOT!", value);
                break;
            case SPIN_TICKET:
                value = 1;
                description = "1 Free Spin Ticket";
                break;
            case BOOST_1H:
                value = 1;
                description = "1.5x Mining Boost (1 Hour)";
                break;
            case BOOST_4H:
                value = 4;
                description = "1.5x Mining Boost (4 Hours)";
                break;
            case MYSTERY_BOX:
                // Mystery box gives random high value
                value = 10 + random.nextFloat() * 40;
                description = String.format(Locale.getDefault(), "Mystery Box: %.1f LYX!", value);
                break;
            case BETTER_LUCK:
            default:
                value = 0.5f;
                description = "0.5 LYX - Better luck next time!";
                break;
        }

        return new ScratchCardReward(selectedType, value, description);
    }

    private void applyReward(ScratchCardReward reward) {
        if (userRef == null) return;

        switch (reward.type) {
            case LYX_SMALL:
            case LYX_MEDIUM:
            case LYX_LARGE:
            case MYSTERY_BOX:
            case BETTER_LUCK:
                // Add LYX tokens
                userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Double current = snapshot.getValue(Double.class);
                        if (current == null) current = 0.0;
                        userRef.child("totalcoins").setValue(current + reward.value)
                            .addOnCompleteListener(task -> {
                                // FIXED: Notify WalletManager so balance updates immediately
                                if (task.isSuccessful()) {
                                    try {
                                        WalletManager.getInstance(context).refreshBalance();
                                    } catch (Exception e) {
                                        Log.w(TAG, "Failed to notify WalletManager", e);
                                    }
                                }
                            });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
                break;

            case SPIN_TICKET:
                // Add spin ticket
                userRef.child("spinTickets").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Long current = snapshot.getValue(Long.class);
                        if (current == null) current = 0L;
                        userRef.child("spinTickets").setValue(current + 1);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
                break;

            case BOOST_1H:
            case BOOST_4H:
                // Activate boost
                long boostDurationMs = (long) (reward.value * 60 * 60 * 1000);
                long expiresAt = System.currentTimeMillis() + boostDurationMs;
                Map<String, Object> boostData = new HashMap<>();
                boostData.put("multiplier", 1.5f);
                boostData.put("expiresAt", expiresAt);
                boostData.put("source", "scratch_card");
                userRef.child("activeBoosts").child("scratchCardBoost").setValue(boostData);

                // Also update BoostManager if available
                try {
                    BoostManager.getInstance(context).activateTemporaryBoost(expiresAt);
                } catch (Exception e) {
                    Log.w(TAG, "Could not update BoostManager", e);
                }
                break;
        }

        Log.d(TAG, "Applied reward: " + reward.type.name() + " - " + reward.description);
    }

    private void saveRewardHistory(ScratchCardReward reward) {
        if (userRef == null) return;

        String today = getTodayDate();
        Map<String, Object> rewardData = new HashMap<>();
        rewardData.put("type", reward.type.name());
        rewardData.put("value", reward.value);
        rewardData.put("description", reward.description);
        rewardData.put("timestamp", reward.timestamp);

        userRef.child("scratchCardHistory").child(today)
                .child(String.valueOf(reward.timestamp)).setValue(rewardData);
    }

    // Callbacks
    public interface EarnCallback {
        void onCardEarned(int totalCards);
        void onEarnFailed(String error);
    }

    public interface ScratchCallback {
        void onCardReady(ScratchCardReward reward);
        void onNoCards(String message);
    }

    public interface RevealCallback {
        void onCardRevealed(ScratchCardReward reward);
        void onRevealFailed(String error);
    }

    // Listeners
    public void addListener(ScratchCardListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ScratchCardListener listener) {
        listeners.remove(listener);
    }

    private void notifyCardRevealed(ScratchCardReward reward) {
        for (ScratchCardListener listener : listeners) {
            try {
                listener.onCardRevealed(reward);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyCardEarned() {
        for (ScratchCardListener listener : listeners) {
            try {
                listener.onCardEarned();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }
}

