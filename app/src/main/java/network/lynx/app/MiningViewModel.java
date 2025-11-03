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
    
    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Float> miningRate = new MutableLiveData<>(0.0f);
    private final MutableLiveData<String> boostInfo = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    
    private BoostManagerOptimized boostManager;
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
            boostManager = BoostManagerOptimized.getInstance(getApplication());
            
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId);
            
            loadUserData();
            setupBoostListener();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ViewModel", e);
            error.setValue("Failed to initialize");
        }
    }
    
    private void loadUserData() {
        isLoading.setValue(true);
        
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Double userBalance = snapshot.child("totalcoins").getValue(Double.class);
                    if (userBalance != null) {
                        balance.setValue(userBalance);
                        prefs.edit()
                                .putLong(KEY_LAST_BALANCE, Double.doubleToLongBits(userBalance))
                                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                                .apply();
                    }
                    isLoading.setValue(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading user data", e);
                    error.setValue("Failed to load data");
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User data listener cancelled", error.toException());
                MiningViewModel.this.error.setValue("Connection error");
                isLoading.setValue(false);
            }
        };
        
        userRef.addValueEventListener(userListener);
    }
    
    private void setupBoostListener() {
        boostManager.addBoostChangeListener(new BoostManagerOptimized.BoostChangeListener() {
            @Override
            public void onBoostStateChanged(float currentMiningRate, String boostInfo) {
                miningRate.setValue(currentMiningRate);
                MiningViewModel.this.boostInfo.setValue(boostInfo);
            }
            
            @Override
            public void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier) {
                // Update UI if needed
            }
        });
    }
    
    public LiveData<Double> getBalance() {
        return balance;
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
