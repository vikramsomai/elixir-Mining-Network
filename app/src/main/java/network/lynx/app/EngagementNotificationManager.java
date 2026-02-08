package network.lynx.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * ENGAGEMENT NOTIFICATION MANAGER
 *
 * Smart notifications that bring users back at optimal times.
 */
public class EngagementNotificationManager {
    private static final String TAG = "EngagementNotif";
    private static final String PREFS_NAME = "engagement_notif";

    // Notification channels
    private static final String CHANNEL_MINING = "mining_alerts";
    private static final String CHANNEL_EVENTS = "event_alerts";
    private static final String CHANNEL_SOCIAL = "social_alerts";
    private static final String CHANNEL_EARNINGS = "earnings_updates";

    // Notification IDs
    private static final int NOTIF_MINING_STOPPED = 1001;
    private static final int NOTIF_STREAK_WARNING = 1002;
    private static final int NOTIF_CIRCLE_ALERT = 1003;
    private static final int NOTIF_EVENT_ALERT = 1004;
    private static final int NOTIF_DAILY_EARNINGS = 1005;

    private static EngagementNotificationManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final NotificationManagerCompat notificationManager;

    // Messages for variety
    private static final String[] MINING_STOPPED_MESSAGES = {
            "Your mining has stopped. Resume now to keep earning!",
            "Don't miss out! Your mining session ended.",
            "Your LYX tokens are waiting. Start mining again!",
            "Mining paused. Your earnings are on hold.",
            "Resume mining to maintain your streak bonus!"
    };

    private static final String[] STREAK_WARNING_MESSAGES = {
            "Your streak is at risk! Check in now.",
            "Don't lose your %d day streak!",
            "Quick! Your streak ends in 2 hours.",
            "One tap to save your streak bonus!",
            "Your mining streak needs you!"
    };

    private static final String[] EARNINGS_MESSAGES = {
            "Today's earnings: %s LYX! Keep it up!",
            "You mined %s LYX today! Great progress!",
            "Daily report: +%s LYX added to your wallet!",
            "Nice! You earned %s LYX in the last 24 hours."
    };

    private EngagementNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannels();
    }

    public static synchronized EngagementNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new EngagementNotificationManager(context);
        }
        return instance;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            NotificationChannel miningChannel = new NotificationChannel(
                    CHANNEL_MINING, "Mining Alerts", NotificationManager.IMPORTANCE_HIGH);
            miningChannel.setDescription("Alerts about your mining status");
            manager.createNotificationChannel(miningChannel);

            NotificationChannel eventsChannel = new NotificationChannel(
                    CHANNEL_EVENTS, "Event Alerts", NotificationManager.IMPORTANCE_HIGH);
            eventsChannel.setDescription("Limited time events and bonuses");
            manager.createNotificationChannel(eventsChannel);

            NotificationChannel socialChannel = new NotificationChannel(
                    CHANNEL_SOCIAL, "Social Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            socialChannel.setDescription("Updates about friends and teams");
            manager.createNotificationChannel(socialChannel);

            NotificationChannel earningsChannel = new NotificationChannel(
                    CHANNEL_EARNINGS, "Earnings Updates", NotificationManager.IMPORTANCE_DEFAULT);
            earningsChannel.setDescription("Daily earnings summaries");
            manager.createNotificationChannel(earningsChannel);
        }
    }

    public void showMiningStoppedNotification() {
        if (!canShowNotification()) return;

        String message = MINING_STOPPED_MESSAGES[new Random().nextInt(MINING_STOPPED_MESSAGES.length)];

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openFragment", "mining");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MINING)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Mining Stopped")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            notificationManager.notify(NOTIF_MINING_STOPPED, builder.build());
            recordNotificationSent("mining_stopped");
        } catch (SecurityException e) {
            Log.e(TAG, "No notification permission", e);
        }
    }

    public void showStreakWarningNotification(int currentStreak) {
        if (!canShowNotification()) return;

        String message = String.format(
                STREAK_WARNING_MESSAGES[new Random().nextInt(STREAK_WARNING_MESSAGES.length)],
                currentStreak);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openFragment", "home");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MINING)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Streak Alert!")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            notificationManager.notify(NOTIF_STREAK_WARNING, builder.build());
            recordNotificationSent("streak_warning");
        } catch (SecurityException e) {
            Log.e(TAG, "No notification permission", e);
        }
    }

    public void showEventNotification(String eventName, String description) {
        if (!canShowNotification()) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openFragment", "mining");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(eventName + " is LIVE!")
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            notificationManager.notify(NOTIF_EVENT_ALERT, builder.build());
            recordNotificationSent("event_alert");
        } catch (SecurityException e) {
            Log.e(TAG, "No notification permission", e);
        }
    }

    public void showDailyEarningsNotification(double earnings) {
        if (!canShowNotification()) return;

        String formattedEarnings = String.format(java.util.Locale.US, "%.2f", earnings);
        String message = String.format(
                EARNINGS_MESSAGES[new Random().nextInt(EARNINGS_MESSAGES.length)],
                formattedEarnings);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_EARNINGS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Daily Earnings")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            notificationManager.notify(NOTIF_DAILY_EARNINGS, builder.build());
            recordNotificationSent("daily_earnings");
        } catch (SecurityException e) {
            Log.e(TAG, "No notification permission", e);
        }
    }

    public void showCircleAlertNotification(String memberName) {
        if (!canShowNotification()) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("openFragment", "referral");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Circle Member Inactive")
                .setContentText(memberName + " hasn't mined today. Remind them!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            notificationManager.notify(NOTIF_CIRCLE_ALERT, builder.build());
            recordNotificationSent("circle_alert");
        } catch (SecurityException e) {
            Log.e(TAG, "No notification permission", e);
        }
    }

    private boolean canShowNotification() {
        if (!prefs.getBoolean("notifications_enabled", true)) {
            return false;
        }

        if (prefs.getBoolean("quiet_hours_enabled", false)) {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int quietStart = prefs.getInt("quiet_start", 22);
            int quietEnd = prefs.getInt("quiet_end", 8);

            if (quietStart > quietEnd) {
                if (hour >= quietStart || hour < quietEnd) return false;
            } else {
                if (hour >= quietStart && hour < quietEnd) return false;
            }
        }

        return true;
    }

    private void recordNotificationSent(String type) {
        int count = prefs.getInt("notif_count_" + type, 0);
        prefs.edit()
                .putInt("notif_count_" + type, count + 1)
                .putLong("last_notif_" + type, System.currentTimeMillis())
                .apply();
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply();
    }

    public void setQuietHours(boolean enabled, int startHour, int endHour) {
        prefs.edit()
                .putBoolean("quiet_hours_enabled", enabled)
                .putInt("quiet_start", startHour)
                .putInt("quiet_end", endHour)
                .apply();
    }

    public void scheduleEngagementChecks() {
        PeriodicWorkRequest checkRequest = new PeriodicWorkRequest.Builder(
                EngagementCheckWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "engagement_check",
                ExistingPeriodicWorkPolicy.KEEP,
                checkRequest);
    }

    public static class EngagementCheckWorker extends Worker {

        public EngagementCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                EngagementNotificationManager manager = getInstance(getApplicationContext());

                Calendar cal = Calendar.getInstance();
                if (cal.get(Calendar.HOUR_OF_DAY) >= 22) {
                    SharedPreferences prefs = getApplicationContext()
                            .getSharedPreferences("engagement_notif", Context.MODE_PRIVATE);

                    String today = String.valueOf(cal.get(Calendar.DAY_OF_YEAR));
                    if (!prefs.getBoolean("streak_warned_" + today, false)) {
                        prefs.edit().putBoolean("streak_warned_" + today, true).apply();
                    }
                }

                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Engagement check failed", e);
                return Result.retry();
            }
        }
    }
}

