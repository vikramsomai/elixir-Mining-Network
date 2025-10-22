package network.lynx.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

import java.util.Date;

public class AppOpenAdManager implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "AppOpenAdManager";
    private static final String AD_UNIT_ID = "ca-app-pub-1396109779371789/4580141705"; // Real ID

    private final LynxApplication app;
    private AppOpenAd appOpenAd = null;
    private long loadTime = 0;
    private boolean isShowingAd = false;
    private Activity currentActivity;

    public AppOpenAdManager(LynxApplication app) {
        this.app = app;
        app.registerActivityLifecycleCallbacks(this);
        loadAd();
    }

    public void loadAd() {
        if (isAdAvailable()) {
            Log.d(TAG, "Ad already available, skipping load.");
            return;
        }

        AdRequest request = new AdRequest.Builder().build();

        // Optional: if using target API < 34, add orientation param
        AppOpenAd.load(
                app, AD_UNIT_ID, request,
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        appOpenAd = ad;
                        loadTime = new Date().getTime();
                        Log.d(TAG, "App Open Ad loaded successfully.");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        appOpenAd = null;
                        Log.e(TAG, "Failed to load App Open Ad: " + error.getMessage());
                    }
                });
    }

    public boolean isAdAvailable() {
        return appOpenAd != null && (new Date().getTime() - loadTime) < 4 * 60 * 60 * 1000;
    }

    public void showAdIfAvailable(Activity activity, Runnable onAdComplete) {
        if (isShowingAd || !isAdAvailable()) {
            Log.d(TAG, "No ad available or ad already showing.");
            onAdComplete.run();
            return;
        }

        isShowingAd = true;

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingAd = false;
                appOpenAd = null;
                loadAd();
                Log.d(TAG, "Ad dismissed.");
                onAdComplete.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                isShowingAd = false;
                Log.e(TAG, "Ad failed to show: " + adError.getMessage());
                onAdComplete.run();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad is showing.");
            }
        });

        appOpenAd.show(activity);
    }

    // Lifecycle tracking
    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;

        // Only show ad if it's not splash or login
        if (!(activity instanceof splash) && !(activity instanceof LoginActivity)) {
            activity.getWindow().getDecorView().post(() -> {
                showAdIfAvailable(activity, () ->
                        Log.d(TAG, "Ad show complete or skipped."));
            });
        }
    }


    @Override public void onActivityStarted(Activity activity) { currentActivity = activity; }
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
