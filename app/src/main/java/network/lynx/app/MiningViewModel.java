package network.lynx.app;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * ViewModel for Mining Fragment
 * Handles mining data and calculations
 */
public class MiningViewModel extends AndroidViewModel {
    private static final String TAG = "MiningViewModel";

    private final MutableLiveData<Double> totalCoins = new MutableLiveData<>(0.0);
    private final MutableLiveData<Float> miningRate = new MutableLiveData<>(0.0f);
    private final MutableLiveData<String> boostInfo = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private BoostManager boostManager; // Changed from BoostManagerOptimized to BoostManager
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "mining_prefs";
    private static final String KEY_LAST_BALANCE = "last_balance";
    private static final String KEY_LAST_UPDATE = "last_update";

    public MiningViewModel(@NonNull Application application) {
        super(application);
        initializeData();
    }

    private void initializeData() {
        try {
            prefs = getApplication().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

            // Changed to use BoostManager instead of BoostManagerOptimized
            boostManager = BoostManager.getInstance(getApplication());

            // Check if user is authenticated before accessing Firebase
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.e(TAG, "User not authenticated during ViewModel initialization");
                error.setValue("User not authenticated");
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId);

            loadUserData();
            setupBoostListener();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel", e);
            error.setValue("Failed to initialize: " + e.getMessage());
        }
    }

    private void loadUserData() {
        if (userRef == null) {
            error.setValue("Database reference not initialized");
            return;
        }

        isLoading.setValue(true);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Double userBalance = snapshot.child("totalcoins").getValue(Double.class);
                    if (userBalance != null) {
                        totalCoins.setValue(userBalance);
                        prefs.edit()
                                .putLong(KEY_LAST_BALANCE, Double.doubleToLongBits(userBalance))
                                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                                .apply();
                    } else {
                        // Set default value if null
                        totalCoins.setValue(0.0);
                    }
                    isLoading.setValue(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading user data", e);
                    error.setValue("Failed to load data: " + e.getMessage());
                    isLoading.setValue(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User data listener cancelled", error.toException());
                MiningViewModel.this.error.setValue("Connection error: " + error.getMessage());
                isLoading.setValue(false);
            }
        };

        userRef.addValueEventListener(userListener);
    }

    private void setupBoostListener() {
        if (boostManager == null) {
            Log.w(TAG, "BoostManager is null, cannot setup listener");
            return;
        }

        boostManager.addBoostChangeListener(new BoostManager.BoostChangeListener() {
            @Override
            public void onBoostStateChanged(float currentMiningRate, String boostInfo) {
                miningRate.setValue(currentMiningRate);
                MiningViewModel.this.boostInfo.setValue(boostInfo);
            }

            @Override
            public void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier) {
                // Update mining rate when permanent boost changes
                if (boostManager != null) {
                    miningRate.setValue(boostManager.getCurrentMiningRate());
                }
            }
        });

        // Set initial mining rate
        if (boostManager != null) {
            miningRate.setValue(boostManager.getCurrentMiningRate());
        }
    }

    // Fixed: Changed from getBalance() to getTotalCoins() to match fragment usage
    public LiveData<Double> getTotalCoins() {
        return totalCoins;
    }

    public LiveData<Float> getMiningRate() {
        return miningRate;
    }

    public LiveData<String> getBoostInfo() {
        return boostInfo;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void refreshData() {
        loadUserData();
    }

    // Helper method to update coins (useful for mining updates)
    public void updateCoins(double newBalance) {
        totalCoins.setValue(newBalance);

        // Also update Firebase
        if (userRef != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            userRef.child("totalcoins").setValue(newBalance);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Cleanup
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
        }

        if (boostManager != null) {
            boostManager.cleanup();
        }
    }
}