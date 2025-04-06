package network.lynx.app;



import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.FirebaseApp;


public class MainActivity extends AppCompatActivity {
FrameLayout frameLayout;
BottomNavigationView bottomNavigationView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        MobileAds.initialize(this, initializationStatus -> {});

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();
            View decor = window.getDecorView();
            // Clear LIGHT_STATUS_BAR to make icons white
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            decor.setSystemUiVisibility(0);
        }

        FirebaseApp.initializeApp(this);

        // Optional: Set a dark status bar background for contrast


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
//                     else if (itemid == R.id.navwallet) {
//                       loadFragement(new WalletFragment());
//                      }
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
    fragmentTransaction.replace(R.id.framelayout, fragment);
    fragmentTransaction.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right, R.anim.enter_from_right, R.anim.exit_to_left);
    fragmentTransaction.addToBackStack(null);
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