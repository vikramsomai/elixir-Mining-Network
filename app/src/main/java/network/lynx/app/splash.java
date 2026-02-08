package network.lynx.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Splash Screen with improved UX
 *
 * SENIOR DEVELOPER BEST PRACTICES:
 * - Uses SessionManager to check login state
 * - Uses NetworkUtils for connectivity
 * - Proper loading states
 * - Smooth transitions
 */
public class splash extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int SPLASH_TIMEOUT = 1500; // 1.5 seconds for branding

    private TextView noInternetText;
    private Button retryButton;
    private ProgressBar loadingProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        noInternetText = findViewById(R.id.noInternetText);
        retryButton = findViewById(R.id.retryButton);
        loadingProgress = findViewById(R.id.loadingProgress);

        retryButton.setOnClickListener(v -> {
            hideErrorUI();
            checkAndProceed();
        });

        // Start with a delay for branding
        new Handler().postDelayed(this::checkAndProceed, SPLASH_TIMEOUT);
    }

    private void checkAndProceed() {
        // Hide error UI while checking
        hideErrorUI();

        try {
            // Use NetworkUtils for proper connectivity check
            NetworkUtils networkUtils = NetworkUtils.getInstance(this);

            if (networkUtils.isConnected()) {
                proceedToNextScreen();
            } else {
                // Check if we have cached data and can proceed offline
                SessionManager sessionManager = SessionManager.getInstance(this);
                if (sessionManager.isLoggedIn() && sessionManager.getTotalCoins() > 0) {
                    // User has cached data, allow offline access
                    proceedToNextScreen();
                } else {
                    // No internet and no cached data
                    showNoInternetUI();
                }
            }
        } catch (Exception e) {
            // Fallback - try to proceed anyway
            proceedToNextScreen();
        }
    }

    private void proceedToNextScreen() {
        try {
            SessionManager sessionManager = SessionManager.getInstance(this);

            Intent intent;
            if (sessionManager.isLoggedIn()) {
                // User is logged in, go to main
                sessionManager.updateActivity();
                intent = new Intent(this, MainActivity.class);
            } else {
                // Not logged in, go to login
                intent = new Intent(this, LoginActivity.class);
            }

            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        } catch (Exception e) {
            // Fallback to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void hideErrorUI() {
        if (loadingProgress != null) loadingProgress.setVisibility(View.VISIBLE);
        if (noInternetText != null) noInternetText.setVisibility(View.GONE);
        if (retryButton != null) retryButton.setVisibility(View.GONE);
    }

    private void showNoInternetUI() {
        if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
        if (noInternetText != null) noInternetText.setVisibility(View.VISIBLE);
        if (retryButton != null) retryButton.setVisibility(View.VISIBLE);
        ToastUtils.showError(this, "No internet connection");
    }
}
