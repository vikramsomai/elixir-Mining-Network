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
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WALLET MANAGER - Token Balance & Transaction History
 *
 * Play Store compliant - shows LYX tokens only (in-app currency)
 *
 * NEW: Balance change listeners for real-time UI sync across fragments
 */
public class WalletManager {
    private static final String TAG = "WalletManager";
    private static final String PREFS_NAME = "wallet_manager";
    private static final String PREF_FIRST_SPIN_FREE = "first_spin_free";

    private static WalletManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private String currentUserId;

    // NEW: Balance change listeners for real-time sync
    private final List<BalanceChangeListener> balanceListeners = new CopyOnWriteArrayList<>();

    /**
     * Listener interface for balance changes
     * Used by MiningFragment and other UI components to update immediately
     */
    public interface BalanceChangeListener {
        void onBalanceChanged(double newBalance);
    }

    // Wallet data
    private double totalBalance = 0;
    private double pendingBalance = 0;
    private double todayEarnings = 0;
    private double weeklyEarnings = 0;
    private double monthlyEarnings = 0;
    private double totalMined = 0;
    private double totalReferralEarnings = 0;
    private double totalBonusEarnings = 0;

    private WalletUpdateListener listener;

    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        if (firebaseAuth.getCurrentUser() != null) {
            String uid = firebaseAuth.getCurrentUser().getUid();
            if (currentUserId == null || !currentUserId.equals(uid)) {
                currentUserId = uid;
                Log.d(TAG, "Auth state changed - user signed in: " + uid);
                loadWallet();
            }
        } else {
            // user signed out
            Log.d(TAG, "Auth state changed - user signed out");
            currentUserId = null;
            // reset balances
            totalBalance = 0;
            pendingBalance = 0;
            totalMined = 0;
            todayEarnings = 0;
            notifyListener();
        }
    };

    public static class WalletInfo {
        public double totalBalance;
        public double availableBalance;
        public double pendingBalance;
        public double todayEarnings;
        public double weeklyEarnings;
        public double monthlyEarnings;
        public double totalMined;
        public double referralEarnings;
        public double bonusEarnings;
        public int transactionCount;
        public String formattedBalance;
        public String lastUpdated;
    }

    public static class WalletTransaction {
        public String id;
        public String type;
        public double amount;
        public long timestamp;
        public String description;
        public String status;

        public WalletTransaction() {}

        public WalletTransaction(String type, double amount, String description) {
            this.type = type;
            this.amount = amount;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
            this.status = "completed";
        }
    }

    public interface WalletUpdateListener {
        void onWalletUpdated(WalletInfo info);
        void onTransactionAdded(WalletTransaction transaction);
    }

    private WalletManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadWallet();
        }
    }

    public static synchronized WalletManager getInstance(Context context) {
        if (instance == null) {
            instance = new WalletManager(context);
        }
        return instance;
    }

    public void setListener(WalletUpdateListener listener) {
        this.listener = listener;
    }

    /**
     * Force refresh balance from Firebase and notify all listeners
     * Call this after updating tokens from other parts of the app
     */
    public void refreshBalance() {
        if (currentUserId == null) {
            Log.w(TAG, "Cannot refresh balance - user not logged in");
            return;
        }

        dbRef.child("users").child(currentUserId).child("totalcoins")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.getValue() != null) {
                        double newBalance = toDouble(snapshot.getValue());
                        if (newBalance != totalBalance) {
                            Log.d(TAG, "Balance refreshed from Firebase: " + totalBalance + " -> " + newBalance);
                            totalBalance = newBalance;
                            cacheBalance();
                            notifyListener();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to refresh balance", error.toException());
                }
            });
    }

    private void loadWallet() {
        if (currentUserId == null) return;

        dbRef.child("users").child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // FIXED: Use 'totalcoins' (lowercase) to match rest of app
                    if (snapshot.child("totalcoins").exists() && snapshot.child("totalcoins").getValue() != null) {
                        totalBalance = toDouble(snapshot.child("totalcoins").getValue());
                    }
                    if (snapshot.child("pendingCoins").exists() && snapshot.child("pendingCoins").getValue() != null) {
                        pendingBalance = toDouble(snapshot.child("pendingCoins").getValue());
                    }
                    if (snapshot.child("totalMined").exists() && snapshot.child("totalMined").getValue() != null) {
                        totalMined = toDouble(snapshot.child("totalMined").getValue());
                    }
                    if (snapshot.child("referralEarnings").exists() && snapshot.child("referralEarnings").getValue() != null) {
                        totalReferralEarnings = toDouble(snapshot.child("referralEarnings").getValue());
                    }
                    if (snapshot.child("bonusEarnings").exists() && snapshot.child("bonusEarnings").getValue() != null) {
                        totalBonusEarnings = toDouble(snapshot.child("bonusEarnings").getValue());
                    }
                    cacheBalance();
                    notifyListener();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading wallet", error.toException());
                loadCachedBalance();
            }
        });

        loadTodayEarnings();
    }

    private void loadTodayEarnings() {
        if (currentUserId == null) return;

        String today = String.valueOf(System.currentTimeMillis() / (24 * 60 * 60 * 1000));

        dbRef.child("earnings").child(currentUserId).child("daily").child(today)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            todayEarnings = toDouble(snapshot.getValue());
                            notifyListener();
                        } else {
                            todayEarnings = 0;
                            notifyListener();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading today earnings", error.toException());
                    }
                });
    }

    private void cacheBalance() {
        prefs.edit()
                .putFloat("totalBalance", (float) totalBalance)
                .putFloat("pendingBalance", (float) pendingBalance)
                .putFloat("totalMined", (float) totalMined)
                .putLong("lastUpdate", System.currentTimeMillis())
                .apply();
    }

    private void loadCachedBalance() {
        totalBalance = prefs.getFloat("totalBalance", 0f);
        pendingBalance = prefs.getFloat("pendingBalance", 0f);
        totalMined = prefs.getFloat("totalMined", 0f);
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onWalletUpdated(getWalletInfo());
        }
        // NEW: Notify all balance change listeners
        notifyBalanceChanged();
    }

    /**
     * NEW: Notify all balance change listeners
     * Called whenever totalBalance changes
     */
    private void notifyBalanceChanged() {
        for (BalanceChangeListener l : balanceListeners) {
            try {
                l.onBalanceChanged(totalBalance);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying balance listener", e);
            }
        }
    }

    /**
     * NEW: Add a balance change listener
     * Listener is immediately notified with current balance
     */
    public void addBalanceChangeListener(BalanceChangeListener listener) {
        if (listener != null && !balanceListeners.contains(listener)) {
            balanceListeners.add(listener);
            // Immediately notify with current balance
            try {
                listener.onBalanceChanged(totalBalance);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying new balance listener", e);
            }
        }
    }

    /**
     * NEW: Remove a balance change listener
     * Call this in onDestroyView/onDestroy to prevent memory leaks
     */
    public void removeBalanceChangeListener(BalanceChangeListener listener) {
        balanceListeners.remove(listener);
    }

    public WalletInfo getWalletInfo() {
        WalletInfo info = new WalletInfo();
        info.totalBalance = totalBalance;
        info.availableBalance = totalBalance - pendingBalance;
        info.pendingBalance = pendingBalance;
        info.todayEarnings = todayEarnings;
        info.weeklyEarnings = weeklyEarnings;
        info.monthlyEarnings = monthlyEarnings;
        info.totalMined = totalMined;
        info.referralEarnings = totalReferralEarnings;
        info.bonusEarnings = totalBonusEarnings;
        info.formattedBalance = formatBalance(totalBalance);
        return info;
    }

    /**
     * Add tokens atomically using Firebase runTransaction to avoid races.
     * FIXED: Immediately updates local balance and notifies listeners
     */
    public void addTokens(double amount, String type, String description) {
        if (currentUserId == null || amount <= 0) return;

        // FIXED: Immediately update local balance and notify listeners
        // This ensures UI updates right away, even before Firebase transaction completes
        totalBalance += amount;
        cacheBalance();
        notifyListener();
        Log.d(TAG, "Local balance immediately updated: +" + amount + " = " + totalBalance);

        // FIXED: Use 'totalcoins' (lowercase) to match rest of app
        DatabaseReference totalRef = dbRef.child("users").child(currentUserId).child("totalcoins");
        totalRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                double current = 0;
                Object val = currentData.getValue();
                if (val != null) {
                    current = toDouble(val);
                }
                currentData.setValue(current + amount);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment totalcoins", error.toException());
                    // Rollback local change on error
                    totalBalance -= amount;
                    cacheBalance();
                    notifyListener();
                } else {
                    Log.d(TAG, "totalcoins incremented by " + amount + " in Firebase");
                }
            }
        });

        // Update category (e.g., spinEarnings) atomically
        String categoryKey = type + "Earnings";
        DatabaseReference catRef = dbRef.child("users").child(currentUserId).child(categoryKey);
        catRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                double current = 0;
                Object val = currentData.getValue();
                if (val != null) {
                    current = toDouble(val);
                }
                currentData.setValue(current + amount);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment category " + categoryKey, error.toException());
                }
            }
        });

        // Update daily earnings atomically
        String today = String.valueOf(System.currentTimeMillis() / (24 * 60 * 60 * 1000));
        DatabaseReference dayRef = dbRef.child("earnings").child(currentUserId).child("daily").child(today);
        dayRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull MutableData currentData) {
                double current = 0;
                Object val = currentData.getValue();
                if (val != null) {
                    current = toDouble(val);
                }
                currentData.setValue(current + amount);
                return com.google.firebase.database.Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment daily earnings", error.toException());
                }
            }
        });

        // Add transaction record after kicking off the increments (transaction id generation is independent)
        addTransaction(new WalletTransaction(type, amount, description));
    }

    public void addTransaction(WalletTransaction transaction) {
        if (currentUserId == null) return;

        DatabaseReference pushRef = dbRef.child("transactions").child(currentUserId).push();
        String key = pushRef.getKey();
        if (key == null) {
            Log.e(TAG, "Failed to create transaction key");
            return;
        }
        transaction.id = key;

        pushRef.setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) {
                        listener.onTransactionAdded(transaction);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to write transaction", e));
    }

    private double toDouble(Object value) {
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            Log.w(TAG, "Unable to parse numeric value: " + value, e);
            return 0;
        }
    }

    public String formatBalance(double balance) {
        if (balance >= 1000000) {
            return String.format(java.util.Locale.US, "%.2fM", balance / 1000000);
        } else if (balance >= 1000) {
            return String.format(java.util.Locale.US, "%.2fK", balance / 1000);
        } else {
            return String.format(java.util.Locale.US, "%.2f", balance);
        }
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    public double getTodayEarnings() {
        return todayEarnings;
    }

    public double getTotalMined() {
        return totalMined;
    }

    public double getReferralEarnings() {
        return totalReferralEarnings;
    }

    public double getCachedBalance() {
        return prefs.getFloat("totalBalance", 0f);
    }

    // --- Helpers for first-spin-free feature ---
    public boolean isFirstSpinFree() {
        return prefs.getBoolean(PREF_FIRST_SPIN_FREE, true);
    }

    public void consumeFirstSpinFree() {
        prefs.edit().putBoolean(PREF_FIRST_SPIN_FREE, false).apply();
    }

    // Optional: force reload wallet (useful after testing or manual refresh)
    public void refresh() {
        if (currentUserId != null) {
            loadWallet();
        }
    }
}
