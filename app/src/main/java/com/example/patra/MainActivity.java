package com.example.patra;

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

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
FrameLayout frameLayout;
BottomNavigationView bottomNavigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        frameLayout=findViewById(R.id.framelayout);
        bottomNavigationView=findViewById(R.id.bottomview);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemid=item.getItemId();
                if(itemid==R.id.navHome){
                    loadFragement(new HomeFragment());
                }
                else if(itemid==R.id.navMine){
                    loadFragement(new MiningFragment());
                }
                else if(itemid==R.id.referral){
                    loadFragement(new ReferralFragment());
                }
                else if(itemid==R.id.navProfile){
                    loadFragement(new ProfileFragment());
                }
                return true;
            }
        });
        loadFragement(new HomeFragment());
        Intent serviceIntent = new Intent(this, CounterService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    public  void loadFragement(Fragment fragment){
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction=fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.framelayout,fragment);
        fragmentTransaction.commit();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Optionally stop the service if you want it to end when the activity is destroyed
        // stopService(new Intent(this, CounterService.class));
    }
}