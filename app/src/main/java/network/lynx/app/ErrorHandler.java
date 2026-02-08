package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized Error Handler & Crash Reporter
 *
 * SENIOR DEVELOPER BEST PRACTICES:
 * - Catches uncaught exceptions
 * - Logs errors to Firebase for debugging
 * - Provides user-friendly error messages
 * - Prevents app crashes where possible
 * - Tracks error patterns for optimization
 */
public class ErrorHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "ErrorHandler";
    private static final String PREFS_NAME = "error_handler";
    private static final int MAX_LOCAL_ERRORS = 50;

    private static ErrorHandler instance;
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final SharedPreferences prefs;
    private final Handler mainHandler;

    // Error categories
    public enum ErrorCategory {
        NETWORK,
        FIREBASE,
        MINING,
        ADS,
        UI,
        AUTHENTICATION,
        UNKNOWN
    }

    private ErrorHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Set as default handler
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public static synchronized void initialize(Context context) {
        if (instance == null) {
            instance = new ErrorHandler(context);
            Log.d(TAG, "ErrorHandler initialized");
        }
    }

    public static ErrorHandler getInstance() {
        return instance;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // Log the crash
            logError(ErrorCategory.UNKNOWN, "CRASH", throwable);

            // Try to save crash info locally
            saveCrashLocally(thread, throwable);

            // Report to Firebase if possible
            reportToFirebase(thread, throwable);

        } catch (Exception e) {
            Log.e(TAG, "Error in error handler", e);
        } finally {
            // Call default handler to show crash dialog
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }

    /**
     * Log a non-fatal error
     */
    public static void logError(ErrorCategory category, String message, Throwable throwable) {
        String errorLog = String.format(Locale.US, "[%s] %s: %s - %s",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()),
                category.name(),
                message,
                throwable != null ? throwable.getMessage() : "No exception");

        Log.e(TAG, errorLog, throwable);

        if (instance != null) {
            instance.saveErrorLocally(category, message, throwable);
        }
    }

    /**
     * Log a non-fatal error without exception
     */
    public static void logError(ErrorCategory category, String message) {
        logError(category, message, null);
    }

    /**
     * Safe execution wrapper - prevents crashes
     */
    public static void safeExecute(Runnable action) {
        safeExecute(action, null);
    }

    /**
     * Safe execution wrapper with error callback
     */
    public static void safeExecute(Runnable action, ErrorCallback errorCallback) {
        try {
            action.run();
        } catch (Exception e) {
            logError(ErrorCategory.UNKNOWN, "Safe execution failed", e);
            if (errorCallback != null) {
                errorCallback.onError(e);
            }
        }
    }

    private void saveCrashLocally(Thread thread, Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);

            String crashInfo = String.format(Locale.US,
                    "Thread: %s\nTime: %s\nStack:\n%s",
                    thread.getName(),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()),
                    sw.toString());

            prefs.edit()
                    .putString("last_crash", crashInfo)
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "Failed to save crash locally", e);
        }
    }

    private void saveErrorLocally(ErrorCategory category, String message, Throwable throwable) {
        try {
            int errorCount = prefs.getInt("error_count", 0);

            // Rotate errors if too many
            if (errorCount >= MAX_LOCAL_ERRORS) {
                prefs.edit().putInt("error_count", 0).apply();
                errorCount = 0;
            }

            String key = "error_" + errorCount;
            String errorInfo = String.format(Locale.US, "%s|%s|%s|%s",
                    System.currentTimeMillis(),
                    category.name(),
                    message,
                    throwable != null ? throwable.getMessage() : "none");

            prefs.edit()
                    .putString(key, errorInfo)
                    .putInt("error_count", errorCount + 1)
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "Failed to save error locally", e);
        }
    }

    private void reportToFirebase(Thread thread, Throwable throwable) {
        try {
            String userId = null;
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            if (userId == null) return;

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);

            Map<String, Object> crashData = new HashMap<>();
            crashData.put("thread", thread.getName());
            crashData.put("message", throwable.getMessage());
            crashData.put("stackTrace", sw.toString().substring(0, Math.min(sw.toString().length(), 5000)));
            crashData.put("timestamp", System.currentTimeMillis());
            crashData.put("appVersion", "4.0");
            crashData.put("appVersionCode", 22);

            DatabaseReference crashRef = FirebaseDatabase.getInstance()
                    .getReference("crashes")
                    .child(userId)
                    .push();

            crashRef.setValue(crashData);

        } catch (Exception e) {
            Log.e(TAG, "Failed to report crash to Firebase", e);
        }
    }

    public interface ErrorCallback {
        void onError(Exception e);
    }
}

