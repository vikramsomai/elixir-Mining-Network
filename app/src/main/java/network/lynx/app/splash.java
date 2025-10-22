package network.lynx.app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class splash extends AppCompatActivity {

    private static final int SPLASH_TIME_OUT = 1000; // 2 seconds
    private TextView noInternetText;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        noInternetText = findViewById(R.id.noInternetText);
        retryButton = findViewById(R.id.retryButton);

        checkInternetAndProceed();

        retryButton.setOnClickListener(v -> checkInternetAndProceed());
    }

    private void checkInternetAndProceed() {
        new Handler().postDelayed(() -> {
            if (isConnectedToInternet()) {
                startActivity(new Intent(splash.this, LoginActivity.class));
                finish();
            } else {
                noInternetText.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.VISIBLE);
                ToastUtils.showError(splash.this, "No internet connection");
            }
        }, SPLASH_TIME_OUT);
    }

    private boolean isConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // For older devices
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }
}
