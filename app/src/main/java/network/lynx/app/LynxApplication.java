package network.lynx.app;

import android.app.Application;
import android.util.Log;

//import com.facebook.ads.AudienceNetworkAds;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Map;

import network.lynx.app.DataMigrationUtil;
import network.lynx.app.UserActivityTracker;
import network.lynx.app.ActivityCheckWorker;

public class LynxApplication extends Application {
    public UserActivityTracker activityTracker;
    private AdManager adManager;
    @Override
    public void onCreate() {
        super.onCreate();
            Log.d("LynxApplication", "App started - LynxApplication onCreate() called");

        // Initialize Firebase
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
//        AudienceNetworkAds.initialize(this);

        // Run data migration
        DataMigrationUtil.migrateReferralData();
//        AudienceNetworkAds.initialize(this);
        MobileAds.initialize(this, initializationStatus -> {
            Map<String, AdapterStatus> statusMap = initializationStatus.getAdapterStatusMap();
            for (Map.Entry<String, AdapterStatus> entry : statusMap.entrySet()) {
                Log.d("Adapter", entry.getKey() + ": " + entry.getValue().getDescription());
            }
        });

        // ✅ Initialize AdManager for rewarded ad preload
        initializeAdManager();


        // Initialize activity tracker
        activityTracker = new UserActivityTracker(this);

        // Schedule daily check of all users' activity status
        ActivityCheckWorker.scheduleDaily(this);
    }
    private void initializeAdManager() {
        adManager = AdManager.getInstance();

        // Smart preload only the most likely used ad
        adManager.smartPreloadAd(this, AdManager.AD_UNIT_CHECK_IN);
    }
}