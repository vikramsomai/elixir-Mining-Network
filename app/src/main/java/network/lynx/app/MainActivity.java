package network.lynx.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    FrameLayout frameLayout;
    BottomNavigationView bottomNavigationView;
    private AdManager adManager;

    // FIX: Track current user to detect user changes
    private String currentUserId = null;
    private FirebaseAuth.AuthStateListener authStateListener;

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
        } catch (JSONException e) {
            Log.e(TAG, "Error creating consent object", e);
        }
        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG);
        RequestConfiguration configuration =
                new RequestConfiguration.Builder()
                        .setTestDeviceIds(Collections.singletonList("YOUR_DEVICE_ID"))
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
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            checkForNotifications();
        }

        // FIX: Setup auth state listener to handle user changes
        setupAuthStateListener();

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

    /**
     * FIX: Setup auth state listener to detect user changes and refresh data
     */
    private void setupAuthStateListener() {
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() != null) {
                String newUserId = firebaseAuth.getCurrentUser().getUid();

                // If user changed, reload the current fragment to refresh data
                if (currentUserId != null && !currentUserId.equals(newUserId)) {
                    Log.d(TAG, "User changed from " + currentUserId + " to " + newUserId);
                    currentUserId = newUserId;

                    // Reload the home fragment to refresh with new user data
                    loadFragment(new HomeFragment());
                    bottomNavigationView.setSelectedItemId(R.id.navHome);
                } else if (currentUserId == null) {
                    currentUserId = newUserId;
                }
            } else {
                // User signed out - go to login
                currentUserId = null;
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }

    private void setupViews() {
        frameLayout = findViewById(R.id.framelayout);
        bottomNavigationView = findViewById(R.id.bottomview);
    }

    private void setupNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
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

        // Setup back press handling using modern API
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomNavigationView != null && bottomNavigationView.getSelectedItemId() != R.id.navHome) {
                    // If not on home, go to home first
                    bottomNavigationView.setSelectedItemId(R.id.navHome);
                } else {
                    // Exit the app
                    finish();
                }
            }
        });
    }

    public void loadFragment(Fragment fragment) {
        try {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Activity is finishing, cannot load fragment");
                return;
            }

            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager.isStateSaved()) {
                Log.w(TAG, "State already saved, using commitAllowingStateLoss");
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.framelayout, fragment);
                fragmentTransaction.commitAllowingStateLoss();
            } else {
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.framelayout, fragment);
                fragmentTransaction.commit();
            }
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


    private void checkForNotifications() {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.w(TAG, "No user logged in, skipping notification check");
                return;
            }

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
                                        if (!isFinishing() && !isDestroyed()) {
                                            ToastUtils.showInfo(MainActivity.this, message);
                                        }
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to check notifications", error.toException());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error checking notifications", e);
        }
    }
}
