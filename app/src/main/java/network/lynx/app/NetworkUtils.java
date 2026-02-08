package network.lynx.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralized Network Utilities
 *
 * SENIOR DEVELOPER BEST PRACTICES:
 * - Real-time connectivity monitoring
 * - Internet availability check (not just network connection)
 * - Automatic retry with exponential backoff
 * - Network state callbacks for UI updates
 * - Efficient background checking
 */
public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    private static NetworkUtils instance;
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> networkState = new MutableLiveData<>(true);
    private final Set<NetworkStateListener> listeners = new HashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isMonitoring = false;

    private NetworkUtils(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initial state
        networkState.postValue(isConnected());
    }

    public static synchronized NetworkUtils getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkUtils(context);
        }
        return instance;
    }

    // ============================================
    // CONNECTIVITY CHECKS
    // ============================================

    /**
     * Check if device has network connection (WiFi or Mobile)
     */
    public boolean isConnected() {
        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

    /**
     * Check if device is connected via WiFi
     */
    public boolean isConnectedViaWifi() {
        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        }
    }

    /**
     * Check if actual internet is available (can reach servers)
     * This is more reliable than just checking network connection
     */
    public void checkInternetAvailability(InternetCheckCallback callback) {
        executor.execute(() -> {
            boolean available = isInternetAvailable();
            mainHandler.post(() -> callback.onResult(available));
        });
    }

    /**
     * Synchronous internet check (run on background thread only)
     */
    public boolean isInternetAvailable() {
        if (!isConnected()) return false;

        try {
            // Try to connect to Google DNS
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 3000);
            socket.close();
            return true;
        } catch (IOException e) {
            // Try alternative
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("1.1.1.1", 53), 3000);
                socket.close();
                return true;
            } catch (IOException e2) {
                return false;
            }
        }
    }

    // ============================================
    // NETWORK MONITORING
    // ============================================

    /**
     * Start monitoring network changes
     */
    public void startMonitoring() {
        if (isMonitoring) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.d(TAG, "Network available");
                    networkState.postValue(true);
                    notifyListeners(true);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.d(TAG, "Network lost");
                    networkState.postValue(false);
                    notifyListeners(false);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities capabilities) {
                    boolean hasInternet = capabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    Log.d(TAG, "Network capabilities changed. Internet: " + hasInternet);
                }
            };

            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            isMonitoring = true;
            Log.d(TAG, "Network monitoring started");
        }
    }

    /**
     * Stop monitoring network changes
     */
    public void stopMonitoring() {
        if (!isMonitoring || networkCallback == null) return;

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            isMonitoring = false;
            Log.d(TAG, "Network monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping network monitoring", e);
        }
    }

    /**
     * Get network state as LiveData for UI observation
     */
    public LiveData<Boolean> getNetworkState() {
        return networkState;
    }

    // ============================================
    // LISTENERS
    // ============================================

    public void addNetworkStateListener(NetworkStateListener listener) {
        listeners.add(listener);
    }

    public void removeNetworkStateListener(NetworkStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(boolean isConnected) {
        mainHandler.post(() -> {
            for (NetworkStateListener listener : listeners) {
                try {
                    listener.onNetworkStateChanged(isConnected);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener", e);
                }
            }
        });
    }

    // ============================================
    // RETRY UTILITIES
    // ============================================

    /**
     * Execute action with automatic retry on failure
     */
    public void executeWithRetry(Runnable action, int maxRetries, RetryCallback callback) {
        executeWithRetryInternal(action, maxRetries, 0, callback);
    }

    private void executeWithRetryInternal(Runnable action, int maxRetries, int attempt,
            RetryCallback callback) {
        executor.execute(() -> {
            try {
                action.run();
                mainHandler.post(() -> callback.onSuccess());
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    long delay = AppConfig.RETRY_DELAY_MS * (long) Math.pow(2, attempt);
                    Log.d(TAG, "Retry attempt " + (attempt + 1) + " in " + delay + "ms");

                    mainHandler.postDelayed(() -> {
                        executeWithRetryInternal(action, maxRetries, attempt + 1, callback);
                    }, delay);
                } else {
                    mainHandler.post(() -> callback.onFailure(e));
                }
            }
        });
    }

    /**
     * Wait for network connection before executing
     */
    public void executeWhenConnected(Runnable action, long timeoutMs, TimeoutCallback callback) {
        if (isConnected()) {
            action.run();
            return;
        }

        long startTime = System.currentTimeMillis();

        NetworkStateListener listener = new NetworkStateListener() {
            @Override
            public void onNetworkStateChanged(boolean isConnected) {
                if (isConnected) {
                    removeNetworkStateListener(this);
                    action.run();
                }
            }
        };

        addNetworkStateListener(listener);

        // Timeout handler
        mainHandler.postDelayed(() -> {
            if (!isConnected()) {
                removeNetworkStateListener(listener);
                callback.onTimeout();
            }
        }, timeoutMs);
    }

    // ============================================
    // INTERFACES
    // ============================================

    public interface NetworkStateListener {
        void onNetworkStateChanged(boolean isConnected);
    }

    public interface InternetCheckCallback {
        void onResult(boolean isAvailable);
    }

    public interface RetryCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface TimeoutCallback {
        void onTimeout();
    }
}

