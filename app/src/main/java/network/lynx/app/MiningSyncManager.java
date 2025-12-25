package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * MiningSyncManager - Handles cross-device mining synchronization with minimal Firebase usage
 * 
 * Strategy:
 * 1. Local-first: Store mining state locally, sync to Firebase only on key events
 * 2. Smart sync: Only sync when app opens, mining starts/stops, and periodically (every 5 min)
 * 3. Conflict resolution: Use server timestamps to determine which device started first
 * 4. Optimistic UI: Show local state immediately, reconcile with server in background
 */
public class MiningSyncManager {
    private static final String TAG = "MiningSyncManager";
    
    // Sync intervals (reduced Firebase reads)
    private static final long SYNC_INTERVAL_MS = 5 * 60 * 1000; // Sync every 5 minutes
    private static final long MIN_SYNC_INTERVAL_MS = 30 * 1000; // Minimum 30 seconds between syncs
    
    // Mining duration
    public static final long MINING_DURATION_MS = 24 * 60 * 60 * 1000; // 24 hours
    
    private static MiningSyncManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final String userId;
    private final DatabaseReference miningRef;
    private final Handler syncHandler;
    
    // Cached mining state
    private boolean isMiningActive = false;
    private long miningStartTime = 0;
    private long lastSyncTime = 0;
    private String activeDeviceId;
    private double cachedTotalCoins = 0.0;
    
    // Device identification
    private final String deviceId;
    
    // Listeners
    private MiningSyncListener listener;
    private Runnable periodicSyncRunnable;
    
    public interface MiningSyncListener {
        void onMiningStateChanged(boolean isActive, long startTime, long remainingTime);
        void onMiningConflict(String message);
        void onTotalCoinsUpdated(double totalCoins);
        void onSyncComplete(boolean success);
    }
    
    private MiningSyncManager(Context context) {
        this.context = context.getApplicationContext();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            throw new IllegalStateException("No user logged in, cannot create MiningSyncManager");
        }

        this.userId = auth.getCurrentUser().getUid();
        this.prefs = context.getSharedPreferences("MiningSync_" + userId, Context.MODE_PRIVATE);
        this.miningRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("mining");
        this.syncHandler = new Handler(Looper.getMainLooper());
        
        // Generate unique device ID
        this.deviceId = getOrCreateDeviceId();
        
        // Load cached state
        loadCachedState();
    }
    
    public static synchronized MiningSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new MiningSyncManager(context);
        }
        return instance;
    }
    
    public void setListener(MiningSyncListener listener) {
        this.listener = listener;
    }
    
    public void removeListener() {
        this.listener = null;
    }
    
    private String getOrCreateDeviceId() {
        String id = prefs.getString("device_id", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }
    
    private void loadCachedState() {
        isMiningActive = prefs.getBoolean("isMiningActive", false);
        miningStartTime = prefs.getLong("miningStartTime", 0);
        lastSyncTime = prefs.getLong("lastSyncTime", 0);
        activeDeviceId = prefs.getString("activeDeviceId", null);
        cachedTotalCoins = prefs.getFloat("cachedTotalCoins", 0.0f);
        
        // Check if mining has expired
        if (isMiningActive && miningStartTime > 0) {
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= MINING_DURATION_MS) {
                completeMiningLocally();
            }
        }
        
        Log.d(TAG, "Loaded cached state - Active: " + isMiningActive + ", StartTime: " + miningStartTime);
    }
    
    private void saveCachedState() {
        prefs.edit()
                .putBoolean("isMiningActive", isMiningActive)
                .putLong("miningStartTime", miningStartTime)
                .putLong("lastSyncTime", lastSyncTime)
                .putString("activeDeviceId", activeDeviceId)
                .putFloat("cachedTotalCoins", (float) cachedTotalCoins)
                .apply();
    }
    
    /**
     * Initial sync when app opens - SINGLE Firebase read
     */
    public void syncOnAppOpen() {
        Log.d(TAG, "Syncing on app open...");
        
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    DataSnapshot miningSnapshot = snapshot.child("mining");
                    Boolean serverActive = miningSnapshot.child("isMiningActive").getValue(Boolean.class);
                    Long serverStartTime = miningSnapshot.child("startTime").getValue(Long.class);
                    String serverDeviceId = miningSnapshot.child("deviceId").getValue(String.class);
                    
                    Double serverTotalCoins = snapshot.child("totalcoins").getValue(Double.class);
                    if (serverTotalCoins != null) {
                        cachedTotalCoins = serverTotalCoins;
                    }
                    
                    reconcileMiningState(
                            serverActive != null && serverActive,
                            serverStartTime != null ? serverStartTime : 0,
                            serverDeviceId
                    );
                    
                    lastSyncTime = System.currentTimeMillis();
                    saveCachedState();
                    
                    if (listener != null) {
                        listener.onTotalCoinsUpdated(cachedTotalCoins);
                        listener.onSyncComplete(true);
                    }
                    
                    startPeriodicSync();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing on app open", e);
                    if (listener != null) {
                        listener.onSyncComplete(false);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Sync cancelled", error.toException());
                notifyCurrentState();
                if (listener != null) {
                    listener.onSyncComplete(false);
                }
            }
        });
    }
    
    private void reconcileMiningState(boolean serverActive, long serverStartTime, String serverDeviceId) {
        Log.d(TAG, "Reconciling - Server: active=" + serverActive + ", startTime=" + serverStartTime + 
                ", deviceId=" + serverDeviceId + " | Local: active=" + isMiningActive + ", startTime=" + miningStartTime);
        
        if (serverActive && serverStartTime > 0) {
            long elapsed = System.currentTimeMillis() - serverStartTime;
            
            if (elapsed >= MINING_DURATION_MS) {
                Log.d(TAG, "Mining completed on server, completing locally");
                completeMiningWithSync(serverStartTime);
            } else {
                isMiningActive = true;
                miningStartTime = serverStartTime;
                activeDeviceId = serverDeviceId;
                
                if (serverDeviceId != null && !serverDeviceId.equals(deviceId)) {
                    Log.d(TAG, "Mining active on different device: " + serverDeviceId);
                    if (listener != null) {
                        listener.onMiningConflict("Mining is active on another device. Syncing...");
                    }
                }
                
                notifyCurrentState();
            }
        } else if (!serverActive && isMiningActive && miningStartTime > 0) {
            long localElapsed = System.currentTimeMillis() - miningStartTime;
            if (localElapsed >= MINING_DURATION_MS) {
                completeMiningLocally();
            } else {
                pushMiningStateToServer();
            }
        } else {
            isMiningActive = serverActive;
            miningStartTime = serverStartTime;
            notifyCurrentState();
        }
    }
    
    public void startMining(double currentTotalCoins) {
        if (isMiningActive) {
            Log.d(TAG, "Mining already active");
            return;
        }
        
        isMiningActive = true;
        miningStartTime = System.currentTimeMillis();
        activeDeviceId = deviceId;
        cachedTotalCoins = currentTotalCoins;
        saveCachedState();
        
        notifyCurrentState();
        pushMiningStateToServer();

        Log.d(TAG, "Mining started locally, syncing to server...");
    }

    private void pushMiningStateToServer() {
        Map<String, Object> miningState = new HashMap<>();
        miningState.put("isMiningActive", isMiningActive);
        miningState.put("startTime", miningStartTime);
        miningState.put("deviceId", deviceId);
        miningState.put("lastUpdate", ServerValue.TIMESTAMP);

        miningRef.updateChildren(miningState)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mining state synced to server");
                    lastSyncTime = System.currentTimeMillis();
                    saveCachedState();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync mining state", e));
    }

    private void completeMiningWithSync(long actualStartTime) {
        if (actualStartTime <= 0) {
            actualStartTime = miningStartTime;
        }

        long elapsed = Math.min(System.currentTimeMillis() - actualStartTime, MINING_DURATION_MS);
        double minedTokens = calculateMinedTokens(elapsed);

        Log.d(TAG, "Completing mining - Elapsed: " + elapsed + "ms, Tokens: " + minedTokens);

        isMiningActive = false;
        miningStartTime = 0;
        cachedTotalCoins += minedTokens;
        saveCachedState();

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("mining/isMiningActive", false);
        updates.put("mining/startTime", 0);
        updates.put("mining/completedAt", ServerValue.TIMESTAMP);
        updates.put("totalcoins", cachedTotalCoins);

        userRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Mining completed and synced. Total: " + cachedTotalCoins);
                    if (listener != null) {
                        listener.onTotalCoinsUpdated(cachedTotalCoins);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync completed mining", e));

        notifyCurrentState();
    }

    private void completeMiningLocally() {
        if (!isMiningActive || miningStartTime <= 0) return;

        long elapsed = Math.min(System.currentTimeMillis() - miningStartTime, MINING_DURATION_MS);
        double minedTokens = calculateMinedTokens(elapsed);

        isMiningActive = false;
        cachedTotalCoins += minedTokens;
        miningStartTime = 0;
        saveCachedState();

        Log.d(TAG, "Mining completed locally. Pending sync: " + minedTokens + " tokens");
        notifyCurrentState();
    }

    private double calculateMinedTokens(long elapsedMs) {
        try {
            BoostManager boostManager = BoostManager.getInstance();
            return boostManager.calculateMiningAmount(elapsedMs);
        } catch (Exception e) {
            float baseRatePerSecond = 0.00125f;
            return (elapsedMs / 1000.0) * baseRatePerSecond;
        }
    }

    private void startPeriodicSync() {
        stopPeriodicSync();

        periodicSyncRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMiningActive) {
                    performLightSync();
                }
                syncHandler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        };

        syncHandler.postDelayed(periodicSyncRunnable, SYNC_INTERVAL_MS);
        Log.d(TAG, "Periodic sync started (every " + (SYNC_INTERVAL_MS / 60000) + " minutes)");
    }

    public void stopPeriodicSync() {
        if (periodicSyncRunnable != null) {
            syncHandler.removeCallbacks(periodicSyncRunnable);
            periodicSyncRunnable = null;
        }
    }

    private void performLightSync() {
        long timeSinceLastSync = System.currentTimeMillis() - lastSyncTime;
        if (timeSinceLastSync < MIN_SYNC_INTERVAL_MS) {
            return;
        }

        Log.d(TAG, "Performing light sync...");

        miningRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    Boolean serverActive = snapshot.child("isMiningActive").getValue(Boolean.class);
                    Long serverStartTime = snapshot.child("startTime").getValue(Long.class);
                    String serverDeviceId = snapshot.child("deviceId").getValue(String.class);

                    if (serverActive != null && serverActive && serverStartTime != null) {
                        if (serverDeviceId != null && !serverDeviceId.equals(deviceId)
                                && serverStartTime != miningStartTime) {
                            miningStartTime = serverStartTime;
                            activeDeviceId = serverDeviceId;
                            saveCachedState();
                            notifyCurrentState();
                        }
                    } else if (serverActive != null && !serverActive && isMiningActive) {
                        isMiningActive = false;
                        miningStartTime = 0;
                        saveCachedState();
                        notifyCurrentState();
                        fetchTotalCoins();
                    }

                    lastSyncTime = System.currentTimeMillis();
                    saveCachedState();

                } catch (Exception e) {
                    Log.e(TAG, "Error in light sync", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Light sync cancelled", error.toException());
            }
        });
    }

    private void fetchTotalCoins() {
        DatabaseReference coinsRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("totalcoins");

        coinsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double coins = snapshot.getValue(Double.class);
                if (coins != null) {
                    cachedTotalCoins = coins;
                    saveCachedState();
                    if (listener != null) {
                        listener.onTotalCoinsUpdated(cachedTotalCoins);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch total coins", error.toException());
            }
        });
    }

    private void notifyCurrentState() {
        if (listener != null) {
            long remainingTime = 0;
            if (isMiningActive && miningStartTime > 0) {
                long elapsed = System.currentTimeMillis() - miningStartTime;
                remainingTime = Math.max(0, MINING_DURATION_MS - elapsed);
            }
            listener.onMiningStateChanged(isMiningActive, miningStartTime, remainingTime);
        }
    }

    // Getters
    public boolean isMiningActive() {
        if (isMiningActive && miningStartTime > 0) {
            long elapsed = System.currentTimeMillis() - miningStartTime;
            if (elapsed >= MINING_DURATION_MS) {
                completeMiningLocally();
                return false;
            }
        }
        return isMiningActive;
    }

    public long getMiningStartTime() {
        return miningStartTime;
    }

    public long getRemainingTime() {
        if (!isMiningActive || miningStartTime <= 0) return 0;
        long elapsed = System.currentTimeMillis() - miningStartTime;
        return Math.max(0, MINING_DURATION_MS - elapsed);
    }

    public double getCachedTotalCoins() {
        return cachedTotalCoins;
    }

    public String getActiveDeviceId() {
        return activeDeviceId;
    }

    public boolean isActiveOnThisDevice() {
        return isMiningActive && deviceId.equals(activeDeviceId);
    }

    public void forceSync() {
        syncOnAppOpen();
    }

    public void onAppBackground() {
        stopPeriodicSync();
        saveCachedState();
        if (isMiningActive) {
            pushMiningStateToServer();
        }
    }

    public void onAppForeground() {
        long timeSinceLastSync = System.currentTimeMillis() - lastSyncTime;
        if (timeSinceLastSync > MIN_SYNC_INTERVAL_MS) {
            syncOnAppOpen();
        } else {
            startPeriodicSync();
            notifyCurrentState();
        }
    }

    /**
     * Reset the singleton instance - call this on logout
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.stopPeriodicSync();
            instance.listener = null;
            instance = null;
            Log.d(TAG, "MiningSyncManager instance reset");
        }
    }
}

