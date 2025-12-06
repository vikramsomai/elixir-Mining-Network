package network.lynx.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.appopen.AppOpenAd;

import java.util.Date;

public class AppOpenAdManager implements LifecycleObserver, Application.ActivityLifecycleCallbacks {
    private static final String TAG = "AppOpenAdManager";
    private static final String AD_UNIT_ID = "ca-app-pub-1396109779371789/4580141705";

    private static AppOpenAdManager instance;
    private final LynxApplication app;
    private AppOpenAd appOpenAd = null;
    private long loadTime = 0;
    private boolean isShowingAd = false;
    private Activity currentActivity;

    // Flag to temporarily suppress app open ads (e.g., during rewarded ad sessions)
    private boolean suppressAppOpenAds = false;

    private AppOpenAdManager(LynxApplication app) {
        this.app = app;
        this.app.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    public static synchronized AppOpenAdManager getInstance(LynxApplication app) {
        if (instance == null) {
            instance = new AppOpenAdManager(app);
        }
        return instance;
    }

    @OnLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
    public void onMoveToForeground() {
        // Don't show app open ads if suppressed (e.g., during rewarded ad sessions)
        if (suppressAppOpenAds) {
            return;
        }

        // Show the ad (if available) when the app moves to the foreground.
        if (currentActivity != null && !shouldExcludeFromAds(currentActivity)) {
            showAdIfAvailable(currentActivity);
        }
    }

    /**
     * Temporarily suppress app open ads (call before showing rewarded ads)
     */
    public void setSuppressAppOpenAds(boolean suppress) {
        this.suppressAppOpenAds = suppress;
    }

    /**
     * Check if the activity should be excluded from showing app open ads.
     * Excludes splash, login, signup, ad-watching, and information/policy screens.
     */
    private boolean shouldExcludeFromAds(Activity activity) {
        return activity instanceof splash
                || activity instanceof LoginActivity
                || activity instanceof SignupActivity
                || activity instanceof PrivacyPolicyActivity
                || activity instanceof TermsConditionActivity
                || activity instanceof FaqsActivity
                || activity instanceof KycActivity
                || activity instanceof spinActivity;  // Users watching ads for spins
    }

    public void loadAd() {
        if (isAdAvailable()) {
            return; // An ad is already available.
        }

        AppOpenAd.load(
                app, AD_UNIT_ID, new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        appOpenAd = ad;
                        loadTime = new Date().getTime();
                        Log.d(TAG, "App Open Ad loaded.");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        appOpenAd = null;
                        Log.e(TAG, "Failed to load App Open Ad: " + error.getMessage());
                    }
                });
    }

    private boolean isAdAvailable() {
        // An ad is considered available if it's not null and has been loaded within the last 4 hours.
        return appOpenAd != null && (new Date().getTime() - loadTime) < (4 * 60 * 60 * 1000);
    }

    public void showAdIfAvailable(@NonNull Activity activity) {
        if (isShowingAd || !isAdAvailable()) {
            loadAd();
            return;
        }

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenAd = null;
                isShowingAd = false;
                loadAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                appOpenAd = null;
                isShowingAd = false;
                loadAd();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                isShowingAd = true;
            }
        });

        appOpenAd.show(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
}
