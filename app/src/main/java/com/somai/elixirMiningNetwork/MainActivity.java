package com.somai.elixirMiningNetwork;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
FrameLayout frameLayout;
BottomNavigationView bottomNavigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        MobileAds.initialize(this, initializationStatus -> {});
//
        frameLayout=findViewById(R.id.framelayout);
        bottomNavigationView=findViewById(R.id.bottomview);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemid=item.getItemId();
                try {
                    if (itemid == R.id.navHome) {
                        loadFragement(new HomeFragment());
                    } else if (itemid == R.id.navMine) {
                        try {
                            loadFragement(new MiningFragment());
                        } catch (Exception e) {
                        }
                    }
                     else if (itemid == R.id.navwallet) {
                       loadFragement(new WalletFragment());
                      }
                    else if (itemid == R.id.referral) {
                        loadFragement(new ReferralFragment());
                    } else if (itemid == R.id.navProfile) {
                        loadFragement(new ProfileFragment());
                    }
                }catch (Exception e){}
                return true;
            }
        });
        // Load default fragment
        if (savedInstanceState == null) {
            loadFragement(new HomeFragment());
        }

    }
    public  void loadFragement(Fragment fragment){
try {
    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
//    fragmentTransaction.setCustomAnimations();
    fragmentTransaction.replace(R.id.framelayout, fragment);
    fragmentTransaction.commit();
}catch (Exception e){

}
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity(); // Closes all activities in the current task
        System.exit(0); // Optionally call this to ensure the app exits
    }

}