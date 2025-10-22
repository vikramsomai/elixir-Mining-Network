package network.lynx.app;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Random;

public class SmartNotificationService extends IntentService {
    private static final String TAG = "SmartNotificationService";
    private static final String CHANNEL_ID = "lynx_smart_notifications";

    // Dynamic notification messages for better engagement
    private static final String[][] NOTIFICATION_MESSAGES = {
            // Mining reminders
            {
                    "⛏️ Your mining rig is ready!",
                    "Start earning LYX tokens now - your daily session awaits!",
                    "⛏️ Time to mine some LYX!",
                    "Your mining session is ready. Start earning now!",
                    "⛏️ Daily mining opportunity!",
                    "Don't miss out - start your mining session today!"
            },
            // Streak bonuses
            {
                    "🔥 Keep the streak alive!",
                    "Your daily streak bonus is waiting. Log in now!",
                    "🔥 Streak power activated!",
                    "Maintain your winning streak - bonus rewards inside!",
                    "🔥 Daily streak challenge!",
                    "Your consistency pays off - claim your streak bonus!"
            },
            // Referral rewards
            {
                    "💰 Referral rewards pending!",
                    "You've earned rewards from referrals. Claim them now!",
                    "💰 Your network is growing!",
                    "New referral rewards are ready for collection!",
                    "💰 Passive income unlocked!",
                    "Your referrals are earning you LYX tokens!"
            },
            // Boost available
            {
                    "🚀 Boost your mining power!",
                    "Increase your earning rate by 20% - boost available now!",
                    "🚀 Supercharge your mining!",
                    "Limited time boost available - maximize your earnings!",
                    "🚀 Power up your mining!",
                    "Boost your mining speed and earn more LYX tokens!"
            },
            // Weekly challenge
            {
                    "🎯 New weekly challenge!",
                    "Complete this week's challenge for bonus rewards!",
                    "🎯 Challenge accepted?",
                    "New weekly mission available - extra rewards await!",
                    "🎯 Weekly goal unlocked!",
                    "Take on this week's challenge and earn big!"
            },
            // Comeback reminder
            {
                    "👋 We miss you!",
                    "Your LYX tokens are waiting - come back and claim them!",
                    "🎁 Special comeback bonus!",
                    "We've prepared something special for your return!",
                    "⭐ Your account needs attention!",
                    "Don't let your mining streak break - come back now!"
            },
            // Achievement unlock
            {
                    "🏆 Achievement unlocked!",
                    "You've reached a new milestone - claim your reward!",
                    "🌟 New badge earned!",
                    "Your progress has unlocked a special achievement!",
                    "🎖️ Milestone reached!",
                    "Congratulations! You've earned a new achievement!"
            }
    };

    public SmartNotificationService() {
        super("SmartNotificationService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            int notificationType = intent.getIntExtra("notification_type", -1);
            createNotificationChannel();
            handleSmartNotification(notificationType);
        }
    }

    private void handleSmartNotification(int notificationType) {
        // Check if user should receive this notification
        if (!shouldSendNotification(notificationType)) {
            Log.d(TAG, "Notification skipped based on smart logic: " + notificationType);
            return;
        }

        switch (notificationType) {
            case SmartNotificationScheduler.MINING_REMINDER:
                checkAndSendMiningReminder();
                break;
            case SmartNotificationScheduler.STREAK_BONUS:
                checkAndSendStreakReminder();
                break;
            case SmartNotificationScheduler.REFERRAL_REWARD:
                checkAndSendReferralReminder();
                break;
            case SmartNotificationScheduler.BOOST_AVAILABLE:
                checkAndSendBoostReminder();
                break;
            case SmartNotificationScheduler.WEEKLY_CHALLENGE:
                sendWeeklyChallengeNotification();
                break;
            case SmartNotificationScheduler.COMEBACK_REMINDER:
                sendComebackNotification();
                break;
            case SmartNotificationScheduler.ACHIEVEMENT_UNLOCK:
                sendAchievementNotification();
                break;
        }
    }

    private boolean shouldSendNotification(int notificationType) {
        // Smart logic to prevent notification fatigue
        SharedPreferences prefs = getSharedPreferences("smart_notification_prefs", MODE_PRIVATE);

        // Check daily notification limit
        String today = String.valueOf(System.currentTimeMillis() / (24 * 60 * 60 * 1000));
        String lastNotificationDate = prefs.getString("last_notification_date", "");
        int dailyNotificationCount = prefs.getInt("daily_notification_count", 0);

        if (!today.equals(lastNotificationDate)) {
            // Reset daily count
            prefs.edit()
                    .putString("last_notification_date", today)
                    .putInt("daily_notification_count", 0)
                    .apply();
            dailyNotificationCount = 0;
        }

        // Limit to 3 notifications per day
        if (dailyNotificationCount >= 3) {
            return false;
        }

        // Check if user recently engaged with the app
        long lastAppOpen = prefs.getLong("last_app_open", 0);
        long hoursSinceLastOpen = (System.currentTimeMillis() - lastAppOpen) / (60 * 60 * 1000);

        // Don't send notifications if user was active in the last 2 hours
        if (hoursSinceLastOpen < 2) {
            return false;
        }

        // Increment daily count
        prefs.edit().putInt("daily_notification_count", dailyNotificationCount + 1).apply();

        return true;
    }

    private void checkAndSendMiningReminder() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference miningRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("mining");

        miningRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isMiningActive = snapshot.child("isMiningActive").getValue(Boolean.class);

                if (isMiningActive == null || !isMiningActive) {
                    String[] messages = NOTIFICATION_MESSAGES[0];
                    String title = messages[new Random().nextInt(messages.length / 2) * 2];
                    String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

                    sendSmartNotification(title, message, SmartNotificationScheduler.MINING_REMINDER);
                    trackNotificationSent("mining_reminder");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to check mining status", error.toException());
            }
        });
    }

    private void checkAndSendStreakReminder() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String lastLoginDate = snapshot.child("lastLoginDate").getValue(String.class);
                String today = String.valueOf(System.currentTimeMillis() / (24 * 60 * 60 * 1000));

                if (lastLoginDate == null || !lastLoginDate.equals(today)) {
                    String[] messages = NOTIFICATION_MESSAGES[1];
                    String title = messages[new Random().nextInt(messages.length / 2) * 2];
                    String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

                    sendSmartNotification(title, message, SmartNotificationScheduler.STREAK_BONUS);
                    trackNotificationSent("streak_reminder");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to check streak status", error.toException());
            }
        });
    }

    private void checkAndSendReferralReminder() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId);

        userRef.child("pendingReferralRewards").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    String[] messages = NOTIFICATION_MESSAGES[2];
                    String title = messages[new Random().nextInt(messages.length / 2) * 2];
                    String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

                    sendSmartNotification(title, message, SmartNotificationScheduler.REFERRAL_REWARD);
                    trackNotificationSent("referral_reminder");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to check referral rewards", error.toException());
            }
        });
    }

    private void checkAndSendBoostReminder() {
        SharedPreferences prefs = getSharedPreferences("TokenPrefs_" +
                (FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : ""), MODE_PRIVATE);

        boolean isMiningActive = prefs.getBoolean("isMiningActive", false);
        boolean isBoostActive = prefs.getBoolean("isBoostActive", false);

        if (isMiningActive && !isBoostActive) {
            String[] messages = NOTIFICATION_MESSAGES[3];
            String title = messages[new Random().nextInt(messages.length / 2) * 2];
            String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

            sendSmartNotification(title, message, SmartNotificationScheduler.BOOST_AVAILABLE);
            trackNotificationSent("boost_reminder");
        }
    }

    private void sendWeeklyChallengeNotification() {
        String[] messages = NOTIFICATION_MESSAGES[4];
        String title = messages[new Random().nextInt(messages.length / 2) * 2];
        String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

        sendSmartNotification(title, message, SmartNotificationScheduler.WEEKLY_CHALLENGE);
        trackNotificationSent("weekly_challenge");
    }

    private void sendComebackNotification() {
        String[] messages = NOTIFICATION_MESSAGES[5];
        String title = messages[new Random().nextInt(messages.length / 2) * 2];
        String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

        sendSmartNotification(title, message, SmartNotificationScheduler.COMEBACK_REMINDER);
        trackNotificationSent("comeback_reminder");
    }

    private void sendAchievementNotification() {
        String[] messages = NOTIFICATION_MESSAGES[6];
        String title = messages[new Random().nextInt(messages.length / 2) * 2];
        String message = messages[new Random().nextInt(messages.length / 2) * 2 + 1];

        sendSmartNotification(title, message, SmartNotificationScheduler.ACHIEVEMENT_UNLOCK);
        trackNotificationSent("achievement_unlock");
    }

    private void sendSmartNotification(String title, String message, int notificationId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Reduced priority for less intrusion
                .setAutoCancel(true)
                .setColor(Color.parseColor("#6366F1"))
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(new long[]{0, 200, 100, 200}); // Gentler vibration

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "Smart notification sent: " + title);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Smart Engagement",
                    NotificationManager.IMPORTANCE_DEFAULT // Reduced importance
            );
            channel.setDescription("Smart notifications based on your activity");
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 200, 100, 200});

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void trackNotificationSent(String notificationType) {
        SharedPreferences prefs = getSharedPreferences("notification_analytics", MODE_PRIVATE);
        int count = prefs.getInt(notificationType + "_sent", 0);
        prefs.edit().putInt(notificationType + "_sent", count + 1).apply();

        // Track in Firebase for analytics
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference analyticsRef = FirebaseDatabase.getInstance()
                    .getReference("analytics")
                    .child("smart_notifications")
                    .child(userId);

            String timestamp = String.valueOf(System.currentTimeMillis());
            analyticsRef.child(timestamp).setValue(notificationType);
        }
    }

    public static void recordAppOpen(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("smart_notification_prefs", Context.MODE_PRIVATE);
        prefs.edit().putLong("last_app_open", System.currentTimeMillis()).apply();
    }
}
