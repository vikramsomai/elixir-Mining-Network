package network.lynx.app;

import android.app.Application;
import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Map;

public class LynxApplication extends Application {
    private static final String TAG = "LynxApplication";

    public UserActivityTracker activityTracker;
    private AdManager adManager;
    private NetworkUtils networkUtils;
    private SessionManager sessionManager;
    private AnalyticsManager analyticsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== App Started - LynxApplication onCreate() ===");

        // STEP 1: Initialize Error Handler FIRST (catches all crashes)
        ErrorHandler.initialize(this);
        Log.d(TAG, "✅ ErrorHandler initialized");

        // STEP 2: Initialize Firebase with offline persistence
        initializeFirebase();

        // STEP 3: Initialize Network Utils for connectivity monitoring
        initializeNetworkUtils();

        // STEP 4: Initialize Session Manager
        initializeSessionManager();

        // STEP 5: Run data migration (only if needed)
        ErrorHandler.safeExecute(() -> {
            DataMigrationUtil.migrateReferralDataIfNeeded(this);
            Log.d(TAG, "✅ Data migration checked");
        });

        // STEP 6: Initialize Mobile Ads
        initializeMobileAds();

        // STEP 7: Initialize AdManager with eCPM optimization
        initializeAdManager();

        // STEP 8: Initialize activity tracker
        initializeActivityTracker();

        // STEP 9: Initialize Analytics Manager
        initializeAnalytics();

        // STEP 10: Schedule background workers
        scheduleBackgroundWorkers();

        Log.d(TAG, "=== LynxApplication initialization complete ===");
    }

    private void initializeFirebase() {
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            Log.d(TAG, "✅ Firebase persistence enabled");
        } catch (Exception e) {
            // Already enabled
            Log.d(TAG, "Firebase persistence already enabled");
        }
    }

    private void initializeNetworkUtils() {
        networkUtils = NetworkUtils.getInstance(this);
        networkUtils.startMonitoring();
        Log.d(TAG, "✅ NetworkUtils initialized and monitoring");
    }

    private void initializeSessionManager() {
        sessionManager = SessionManager.getInstance(this);

        // Sync session from Firebase if user is logged in
        if (sessionManager.isLoggedIn()) {
            sessionManager.syncFromFirebase();
            sessionManager.updateActivity();
            Log.d(TAG, "✅ Session restored for user: " + sessionManager.getUserId());
        } else {
            Log.d(TAG, "✅ SessionManager initialized (no active session)");
        }
    }

    private void initializeMobileAds() {
        MobileAds.initialize(this, initializationStatus -> {
            Map<String, AdapterStatus> statusMap = initializationStatus.getAdapterStatusMap();
            for (Map.Entry<String, AdapterStatus> entry : statusMap.entrySet()) {
                Log.d("AdAdapter", entry.getKey() + ": " + entry.getValue().getDescription());
            }
            Log.d(TAG, "✅ MobileAds initialized");
        });
    }

    private void initializeAdManager() {
        adManager = AdManager.getInstance();

        // eCPM OPTIMIZATION: Start session timer
        adManager.startSession();

        // Smart preload high-priority ads
        adManager.smartPreloadAd(this, AdManager.AD_UNIT_CHECK_IN);
        adManager.smartPreloadAd(this, AdManager.AD_UNIT_MINING);

        Log.d(TAG, "✅ AdManager initialized with eCPM optimization");
    }

    private void initializeActivityTracker() {
        activityTracker = new UserActivityTracker(this);
        Log.d(TAG, "✅ UserActivityTracker initialized");
    }

    private void initializeAnalytics() {
        analyticsManager = AnalyticsManager.getInstance(this);
        analyticsManager.startSession();
        Log.d(TAG, "✅ AnalyticsManager initialized");
    }

    private void scheduleBackgroundWorkers() {
        // Schedule daily activity check
        ActivityCheckWorker.scheduleDaily(this);

        // Schedule smart notifications
        SmartNotificationScheduler.scheduleSmartNotifications(this);

        Log.d(TAG, "✅ Background workers scheduled");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        // Cleanup
        if (networkUtils != null) {
            networkUtils.stopMonitoring();
        }

        if (adManager != null) {
            adManager.clearCache();
        }

        // Sync analytics before termination
        if (analyticsManager != null) {
            analyticsManager.endSession();
            analyticsManager.syncToFirebase();
        }

        Log.d(TAG, "App terminated - cleanup complete");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning");

        // Clear ad cache to free memory
        if (adManager != null) {
            adManager.cleanupExpiredAds();
        }
    }

    // ============================================
    // PUBLIC ACCESSORS
    // ============================================

    public AdManager getAdManager() {
        return adManager;
    }

    public NetworkUtils getNetworkUtils() {
        return networkUtils;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }
}