package com.somai.elixirMiningNetwork;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class splash extends AppCompatActivity {

    private static int SPLASH_TIME_OUT = 1000; // 3 seconds
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnectedToInternet()) {
                    Intent i = new Intent(splash.this, LoginActivity.class);
                    startActivity(i);
                    finish();
                } else {
                    // Show a message and stay on the splash screen
                    Toast.makeText(splash.this, "No internet connection", Toast.LENGTH_SHORT).show();
                }

            }
        }, SPLASH_TIME_OUT);

    }
    private boolean isConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}