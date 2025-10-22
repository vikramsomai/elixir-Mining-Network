package network.lynx.app;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;



//import com.google.ads.mediation.inmobi.InMobiConsent;
import com.google.ads.mediation.inmobi.InMobiConsent;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.inmobi.sdk.InMobiSdk;
//import com.inmobi.sdk.InMobiSdk;
//import com.inmobi.sdk.SdkInitializationListener;
//import com.inmobi.unification.sdk.InitializationStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    FrameLayout frameLayout;
    BottomNavigationView bottomNavigationView;
    private AdManager adManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Record app open for smart notifications
        SmartNotificationService.recordAppOpen(this);

        // Schedule smart notifications instead of asking user for preferences
        SmartNotificationScheduler.scheduleSmartNotifications(this);
        // Initialize Facebook Audience Network and AdMob


        MobileAds.initialize(this, initializationStatus -> {
            Map<String, AdapterStatus> statusMap = initializationStatus.getAdapterStatusMap();
            for (Map.Entry<String, AdapterStatus> entry : statusMap.entrySet()) {
                Log.d("Adapter", entry.getKey() + ": " + entry.getValue().getDescription());
            }
        });

        // ✅ Initialize AdManager for rewarded ad preload
        initializeAdManager();
        JSONObject consentObject = new JSONObject();
        try {
            consentObject.put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true);
            consentObject.put("gdpr", "1");
        } catch (JSONException exception) {
            exception.printStackTrace();
        }
        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG);
        RequestConfiguration configuration =
                new RequestConfiguration.Builder()
                        .setTestDeviceIds(Arrays.asList("YOUR_DEVICE_ID"))
                        .build();
        MobileAds.setRequestConfiguration(configuration);


        InMobiConsent.updateGDPRConsent(consentObject);
//        JSONObject consent = new JSONObject();
//        try {
//            consent.put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true);
//            consent.put("gdpr", "1");
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//
//        InMobiSdk.init(this, "a27d4991145846658b2990c33aabf996", consent, new SdkInitializationListener() {
//            @Override
//            public void onInitializationComplete(@Nullable Error error) {
//                Log.d("InMobi", "SDK Init Complete: " + error.toString());
//            }
//
//            public void onInitializationComplete(@NonNull InitializationStatus status) {
//                Log.d("InMobi", "SDK Init Complete: " + status.toString());
//            }
//        });


        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            checkForNotifications();
        }

        setupViews();
        setupNavigation();

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    private void initializeAdManager() {
        adManager = AdManager.getInstance();

        // Smart preload only the most likely used ad
        adManager.smartPreloadAd(this, AdManager.AD_UNIT_CHECK_IN);
    }

    private void setupViews() {
        frameLayout = findViewById(R.id.framelayout);
        bottomNavigationView = findViewById(R.id.bottomview);
    }

    private void setupNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            try {
                if (itemId == R.id.navHome) {
                    loadFragment(new HomeFragment());
                } else if (itemId == R.id.navMine) {
                    loadFragment(new MiningFragment());
                } else if (itemId == R.id.referral) {
                    loadFragment(new ReferralFragment());
                } else if (itemId == R.id.navProfile) {
                    loadFragment(new ProfileFragment());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading fragment: " + e.getMessage());
            }
            return true;
        });
    }

    public void loadFragment(Fragment fragment) {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            fragmentTransaction.replace(R.id.framelayout, fragment);
            fragmentTransaction.commit();
        } catch (Exception e) {
            Log.e(TAG, "Error in loadFragment: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adManager != null) {
            adManager.clearCache();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity(); // optional, ensures all activities closed
        System.exit(0);
    }

    private void checkForNotifications() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userRef.child("notifications").orderByChild("read").equalTo(false)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            int unreadCount = (int) snapshot.getChildrenCount();
                            userRef.child("unreadNotifications").setValue(unreadCount);

                            for (DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                                String type = notificationSnapshot.child("type").getValue(String.class);
                                String message = notificationSnapshot.child("message").getValue(String.class);
                                if ("referral_ping".equals(type) && message != null) {
                                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check notifications", error.toException());
                    }
                });
    }
}
