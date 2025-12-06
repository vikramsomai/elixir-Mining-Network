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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Daily Lucky Number Game
 * Users guess a number (1-10) once per day
 * Correct guess = boost multiplier for 24 hours
 * Close guess = smaller bonus
 */
public class DailyLuckyNumberManager {
    private static final String TAG = "DailyLuckyNumber";
    private static DailyLuckyNumberManager instance;

    private static final int MIN_NUMBER = 1;
    private static final int MAX_NUMBER = 10;

    // Rewards
    private static final float EXACT_MATCH_MULTIPLIER = 3.0f;    // 3x mining for exact match
    private static final float CLOSE_MATCH_MULTIPLIER = 1.5f;    // 1.5x for ±1 difference
    private static final float NEAR_MATCH_MULTIPLIER = 1.2f;     // 1.2x for ±2 difference
    private static final long BOOST_DURATION_MS = 24 * 60 * 60 * 1000; // 24 hours

    private static final int EXACT_MATCH_TOKENS = 50;
    private static final int CLOSE_MATCH_TOKENS = 20;
    private static final int NEAR_MATCH_TOKENS = 10;
    private static final int PARTICIPATION_TOKENS = 5;

    private Context context;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private DatabaseReference dailyNumberRef;
    private String userId;

    public enum GuessResult {
        EXACT_MATCH,    // Guessed correctly
        CLOSE_MATCH,    // ±1
        NEAR_MATCH,     // ±2
        MISS,           // Wrong
        ALREADY_PLAYED, // Already played today
        ERROR           // Error occurred
    }

    public static class LuckyNumberResult {
        public GuessResult result;
        public int userGuess;
        public int luckyNumber;
        public float multiplierWon;
        public int tokensWon;
        public long boostExpiresAt;
        public String message;

        public LuckyNumberResult(GuessResult result, int userGuess, int luckyNumber,
                                  float multiplierWon, int tokensWon, long boostExpiresAt, String message) {
            this.result = result;
            this.userGuess = userGuess;
            this.luckyNumber = luckyNumber;
            this.multiplierWon = multiplierWon;
            this.tokensWon = tokensWon;
            this.boostExpiresAt = boostExpiresAt;
            this.message = message;
        }
    }

    public static class GameStatus {
        public boolean canPlay;
        public boolean hasPlayedToday;
        public int lastGuess;
        public int lastLuckyNumber;
        public GuessResult lastResult;
        public boolean hasActiveBoost;
        public float activeMultiplier;
        public long boostTimeRemaining;

        public GameStatus(boolean canPlay, boolean hasPlayedToday, int lastGuess, int lastLuckyNumber,
                         GuessResult lastResult, boolean hasActiveBoost, float activeMultiplier, long boostTimeRemaining) {
            this.canPlay = canPlay;
            this.hasPlayedToday = hasPlayedToday;
            this.lastGuess = lastGuess;
            this.lastLuckyNumber = lastLuckyNumber;
            this.lastResult = lastResult;
            this.hasActiveBoost = hasActiveBoost;
            this.activeMultiplier = activeMultiplier;
            this.boostTimeRemaining = boostTimeRemaining;
        }
    }

    private DailyLuckyNumberManager(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    public static synchronized DailyLuckyNumberManager getInstance(Context context) {
        if (instance == null) {
            instance = new DailyLuckyNumberManager(context);
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
            prefs = context.getSharedPreferences("LuckyNumber_" + userId, Context.MODE_PRIVATE);
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            dailyNumberRef = FirebaseDatabase.getInstance().getReference("dailyLuckyNumber");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing DailyLuckyNumberManager", e);
        }
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
    }

    public GameStatus getGameStatus() {
        String today = getTodayDate();
        String lastPlayedDate = prefs.getString("lastPlayedDate", "");
        boolean hasPlayedToday = today.equals(lastPlayedDate);

        int lastGuess = prefs.getInt("lastGuess", 0);
        int lastLuckyNumber = prefs.getInt("lastLuckyNumber", 0);
        String lastResultStr = prefs.getString("lastResult", "");
        GuessResult lastResult = lastResultStr.isEmpty() ? null : GuessResult.valueOf(lastResultStr);

        long boostExpiresAt = prefs.getLong("boostExpiresAt", 0);
        long currentTime = System.currentTimeMillis();
        boolean hasActiveBoost = boostExpiresAt > currentTime;
        float activeMultiplier = hasActiveBoost ? prefs.getFloat("activeMultiplier", 1.0f) : 1.0f;
        long boostTimeRemaining = hasActiveBoost ? boostExpiresAt - currentTime : 0;

        return new GameStatus(!hasPlayedToday, hasPlayedToday, lastGuess, lastLuckyNumber,
                lastResult, hasActiveBoost, activeMultiplier, boostTimeRemaining);
    }

    public boolean canPlayToday() {
        return getGameStatus().canPlay;
    }

    public boolean hasActiveBoost() {
        long boostExpiresAt = prefs.getLong("boostExpiresAt", 0);
        return boostExpiresAt > System.currentTimeMillis();
    }

    public float getActiveBoostMultiplier() {
        if (hasActiveBoost()) {
            return prefs.getFloat("activeMultiplier", 1.0f);
        }
        return 1.0f;
    }

    public void playGame(int userGuess, GameCallback callback) {
        if (userGuess < MIN_NUMBER || userGuess > MAX_NUMBER) {
            callback.onGameError("Please pick a number between " + MIN_NUMBER + " and " + MAX_NUMBER);
            return;
        }

        if (!canPlayToday()) {
            GameStatus status = getGameStatus();
            callback.onAlreadyPlayed(status.lastGuess, status.lastLuckyNumber, status.lastResult);
            return;
        }

        // Get or generate today's lucky number
        String today = getTodayDate();
        dailyNumberRef.child(today).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int luckyNumber;
                if (snapshot.exists()) {
                    Long num = snapshot.getValue(Long.class);
                    luckyNumber = num != null ? num.intValue() : generateLuckyNumber();
                } else {
                    luckyNumber = generateLuckyNumber();
                    dailyNumberRef.child(today).setValue(luckyNumber);
                }

                processGuess(userGuess, luckyNumber, callback);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onGameError("Database error: " + error.getMessage());
            }
        });
    }

    private int generateLuckyNumber() {
        return new Random().nextInt(MAX_NUMBER - MIN_NUMBER + 1) + MIN_NUMBER;
    }

    private void processGuess(int userGuess, int luckyNumber, GameCallback callback) {
        int difference = Math.abs(userGuess - luckyNumber);

        GuessResult result;
        float multiplier = 1.0f;
        int tokens;
        String message;
        long boostExpiresAt = 0;

        if (difference == 0) {
            result = GuessResult.EXACT_MATCH;
            multiplier = EXACT_MATCH_MULTIPLIER;
            tokens = EXACT_MATCH_TOKENS;
            message = "🎉 JACKPOT! You guessed correctly! Enjoy 3x mining for 24 hours!";
            boostExpiresAt = System.currentTimeMillis() + BOOST_DURATION_MS;
        } else if (difference == 1) {
            result = GuessResult.CLOSE_MATCH;
            multiplier = CLOSE_MATCH_MULTIPLIER;
            tokens = CLOSE_MATCH_TOKENS;
            message = "🔥 So close! You were off by 1. Enjoy 1.5x mining for 24 hours!";
            boostExpiresAt = System.currentTimeMillis() + BOOST_DURATION_MS;
        } else if (difference == 2) {
            result = GuessResult.NEAR_MATCH;
            multiplier = NEAR_MATCH_MULTIPLIER;
            tokens = NEAR_MATCH_TOKENS;
            message = "👍 Not bad! You were off by 2. Enjoy 1.2x mining for 24 hours!";
            boostExpiresAt = System.currentTimeMillis() + BOOST_DURATION_MS;
        } else {
            result = GuessResult.MISS;
            tokens = PARTICIPATION_TOKENS;
            message = "😅 The lucky number was " + luckyNumber + ". Better luck tomorrow! Here's " + tokens + " LYX for playing.";
        }

        // Save to preferences
        String today = getTodayDate();
        prefs.edit()
                .putString("lastPlayedDate", today)
                .putInt("lastGuess", userGuess)
                .putInt("lastLuckyNumber", luckyNumber)
                .putString("lastResult", result.name())
                .putLong("boostExpiresAt", boostExpiresAt)
                .putFloat("activeMultiplier", multiplier)
                .apply();

        // Update Firebase
        final int finalTokens = tokens;
        final float finalMultiplier = multiplier;
        final long finalBoostExpiresAt = boostExpiresAt;

        if (userRef != null) {
            // Add tokens
            userRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Double current = snapshot.getValue(Double.class);
                    if (current == null) current = 0.0;
                    userRef.child("totalcoins").setValue(current + finalTokens);
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });

            // Record game history
            Map<String, Object> gameData = new HashMap<>();
            gameData.put("guess", userGuess);
            gameData.put("luckyNumber", luckyNumber);
            gameData.put("result", result.name());
            gameData.put("tokensWon", finalTokens);
            gameData.put("multiplierWon", finalMultiplier);
            gameData.put("timestamp", System.currentTimeMillis());
            userRef.child("luckyNumberHistory").child(today).setValue(gameData);

            // If won boost, record it
            if (finalBoostExpiresAt > 0) {
                Map<String, Object> boostData = new HashMap<>();
                boostData.put("multiplier", finalMultiplier);
                boostData.put("expiresAt", finalBoostExpiresAt);
                boostData.put("source", "lucky_number");
                userRef.child("activeBoosts").child("luckyNumber").setValue(boostData);
            }
        }

        LuckyNumberResult luckyResult = new LuckyNumberResult(
                result, userGuess, luckyNumber, multiplier, tokens, boostExpiresAt, message);

        callback.onGameComplete(luckyResult);
        Log.d(TAG, "Lucky Number game: Guess=" + userGuess + ", Lucky=" + luckyNumber + ", Result=" + result.name());
    }

    public interface GameCallback {
        void onGameComplete(LuckyNumberResult result);
        void onAlreadyPlayed(int lastGuess, int lastLuckyNumber, GuessResult lastResult);
        void onGameError(String error);
    }

    // For BoostManager integration
    public float getLuckyNumberBoostMultiplier() {
        return getActiveBoostMultiplier();
    }

    public long getLuckyNumberBoostTimeRemaining() {
        long boostExpiresAt = prefs.getLong("boostExpiresAt", 0);
        long remaining = boostExpiresAt - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}

