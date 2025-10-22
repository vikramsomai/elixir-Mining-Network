package network.lynx.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SmartNotificationScheduler {
    private static final String TAG = "SmartNotificationScheduler";
    private static final String PREFS_NAME = "smart_notification_prefs";

    // Default optimal times based on user behavior research
    private static final int[] OPTIMAL_HOURS = {9, 12, 15, 18, 20}; // 9AM, 12PM, 3PM, 6PM, 8PM

    // Notification types with their priorities and optimal timing
    public static final int MINING_REMINDER = 1001;
    public static final int STREAK_BONUS = 1002;
    public static final int REFERRAL_REWARD = 1003;
    public static final int BOOST_AVAILABLE = 1004;
    public static final int WEEKLY_CHALLENGE = 1005;
    public static final int COMEBACK_REMINDER = 1006;
    public static final int ACHIEVEMENT_UNLOCK = 1007;

    public static void scheduleSmartNotifications(Context context) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        analyzeUserBehaviorAndSchedule(context, userId);
    }

    private static void analyzeUserBehaviorAndSchedule(Context context, String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                UserBehaviorData behaviorData = analyzeUserBehavior(context, snapshot);
                scheduleOptimalNotifications(context, behaviorData);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to analyze user behavior", error.toException());
                // Fallback to default scheduling
                scheduleDefaultNotifications(context);
            }
        });
    }

    private static UserBehaviorData analyzeUserBehavior(Context context, DataSnapshot userSnapshot) {
        UserBehaviorData data = new UserBehaviorData();

        // Analyze login patterns
        DataSnapshot loginHistory = userSnapshot.child("loginHistory");
        List<Integer> loginHours = new ArrayList<>();

        for (DataSnapshot login : loginHistory.getChildren()) {
            Long timestamp = login.getValue(Long.class);
            if (timestamp != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(timestamp);
                loginHours.add(cal.get(Calendar.HOUR_OF_DAY));
            }
        }

        // Find most active hours
        data.mostActiveHours = findMostFrequentHours(loginHours);

        // Analyze mining patterns
        DataSnapshot miningHistory = userSnapshot.child("miningHistory");
        data.prefersMorningMining = analyzeTimePreference(miningHistory, 6, 12);
        data.prefersEveningMining = analyzeTimePreference(miningHistory, 18, 23);

        // Check engagement with previous notifications
        data.engagementRate = getNotificationEngagementRate(context);

        // Analyze user timezone (approximate from login patterns)
        data.estimatedTimezone = estimateTimezone(loginHours);

        // Check user activity level
        Integer loginStreak = userSnapshot.child("loginStreak").getValue(Integer.class);
        data.isActiveUser = loginStreak != null && loginStreak > 3;

        // Check if user is at risk of churning
        String lastLoginDate = userSnapshot.child("lastLoginDate").getValue(String.class);
        data.isAtRiskOfChurn = isUserAtRiskOfChurn(lastLoginDate);

        Log.d(TAG, "User behavior analyzed: " + data.toString());
        return data;
    }

    private static List<Integer> findMostFrequentHours(List<Integer> hours) {
        Map<Integer, Integer> hourCount = new HashMap<>();

        for (Integer hour : hours) {
            hourCount.put(hour, hourCount.getOrDefault(hour, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> sortedHours = new ArrayList<>(hourCount.entrySet());
        sortedHours.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<Integer> topHours = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sortedHours.size()); i++) {
            topHours.add(sortedHours.get(i).getKey());
        }

        return topHours.isEmpty() ? getDefaultOptimalHours() : topHours;
    }

    private static List<Integer> getDefaultOptimalHours() {
        List<Integer> defaultHours = new ArrayList<>();
        for (int hour : OPTIMAL_HOURS) {
            defaultHours.add(hour);
        }
        return defaultHours;
    }

    private static boolean analyzeTimePreference(DataSnapshot history, int startHour, int endHour) {
        int count = 0;
        int total = 0;

        for (DataSnapshot entry : history.getChildren()) {
            Long timestamp = entry.child("timestamp").getValue(Long.class);
            if (timestamp != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(timestamp);
                int hour = cal.get(Calendar.HOUR_OF_DAY);

                total++;
                if (hour >= startHour && hour <= endHour) {
                    count++;
                }
            }
        }

        return total > 0 && (count / (double) total) > 0.6; // 60% threshold
    }

    private static double getNotificationEngagementRate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("notification_analytics", Context.MODE_PRIVATE);
        int sent = prefs.getInt("total_sent", 0);
        int engaged = prefs.getInt("total_engaged", 0);

        return sent > 0 ? (engaged / (double) sent) : 0.5; // Default 50%
    }

    private static int estimateTimezone(List<Integer> loginHours) {
        if (loginHours.isEmpty()) return 0;

        // Simple heuristic: if most logins are in typical working hours (9-17), assume local time
        int workingHourLogins = 0;
        for (Integer hour : loginHours) {
            if (hour >= 9 && hour <= 17) {
                workingHourLogins++;
            }
        }

        return workingHourLogins > loginHours.size() / 2 ? 0 : new Random().nextInt(24) - 12;
    }

    private static boolean isUserAtRiskOfChurn(String lastLoginDate) {
        if (lastLoginDate == null) return true;

        try {
            // Simple check: if last login was more than 2 days ago
            long lastLogin = Long.parseLong(lastLoginDate);
            long daysSinceLogin = (System.currentTimeMillis() - lastLogin) / (24 * 60 * 60 * 1000);
            return daysSinceLogin > 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static void scheduleOptimalNotifications(Context context, UserBehaviorData behaviorData) {
        // Clear existing notifications
        cancelAllNotifications(context);

        // Schedule based on user behavior
        if (behaviorData.isAtRiskOfChurn) {
            scheduleChurnPreventionNotifications(context, behaviorData);
        } else if (behaviorData.isActiveUser) {
            scheduleActiveUserNotifications(context, behaviorData);
        } else {
            scheduleNewUserNotifications(context, behaviorData);
        }

        Log.d(TAG, "Smart notifications scheduled based on user behavior");
    }

    private static void scheduleChurnPreventionNotifications(Context context, UserBehaviorData data) {
        // More frequent, engaging notifications for users at risk of churning
        List<Integer> optimalHours = data.mostActiveHours;

        // Comeback reminder - schedule for user's most active hour
        if (!optimalHours.isEmpty()) {
            scheduleNotificationAtHour(context, COMEBACK_REMINDER, optimalHours.get(0), 0);
        }

        // Achievement unlock notification - create urgency
        scheduleNotificationAtHour(context, ACHIEVEMENT_UNLOCK,
                optimalHours.isEmpty() ? 19 : optimalHours.get(0) + 2, 0);

        // Special mining bonus - high value proposition
        scheduleNotificationAtHour(context, MINING_REMINDER,
                data.prefersMorningMining ? 9 : 20, 0);
    }

    private static void scheduleActiveUserNotifications(Context context, UserBehaviorData data) {
        // Regular engagement notifications for active users
        List<Integer> optimalHours = data.mostActiveHours;

        // Mining reminder at optimal time
        int miningHour = data.prefersMorningMining ? 9 :
                data.prefersEveningMining ? 19 :
                        optimalHours.isEmpty() ? 12 : optimalHours.get(0);
        scheduleNotificationAtHour(context, MINING_REMINDER, miningHour, 0);

        // Streak bonus - different time to avoid notification fatigue
        int streakHour = getNextOptimalHour(optimalHours, miningHour);
        scheduleNotificationAtHour(context, STREAK_BONUS, streakHour, 0);

        // Boost available - evening time for engagement
        scheduleNotificationAtHour(context, BOOST_AVAILABLE, 18, 0);

        // Weekly challenge - Monday morning
        scheduleWeeklyNotification(context, WEEKLY_CHALLENGE, Calendar.MONDAY, 10, 0);
    }

    private static void scheduleNewUserNotifications(Context context, UserBehaviorData data) {
        // Gentle onboarding notifications for new users

        // Morning mining reminder
        scheduleNotificationAtHour(context, MINING_REMINDER, 10, 0);

        // Afternoon engagement
        scheduleNotificationAtHour(context, STREAK_BONUS, 15, 0);

        // Evening referral opportunity
        scheduleNotificationAtHour(context, REFERRAL_REWARD, 19, 0);
    }

    private static void scheduleDefaultNotifications(Context context) {
        // Fallback scheduling with proven optimal times
        scheduleNotificationAtHour(context, MINING_REMINDER, 9, 0);
        scheduleNotificationAtHour(context, STREAK_BONUS, 12, 0);
        scheduleNotificationAtHour(context, BOOST_AVAILABLE, 18, 0);
        scheduleWeeklyNotification(context, WEEKLY_CHALLENGE, Calendar.MONDAY, 10, 0);

        Log.d(TAG, "Default notifications scheduled");
    }

    private static int getNextOptimalHour(List<Integer> optimalHours, int excludeHour) {
        for (Integer hour : optimalHours) {
            if (Math.abs(hour - excludeHour) > 2) { // At least 2 hours apart
                return hour;
            }
        }
        return excludeHour + 4; // Fallback: 4 hours later
    }

    private static void scheduleNotificationAtHour(Context context, int notificationType, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Add some randomization (±30 minutes) to avoid predictability
        Random random = new Random();
        int randomMinutes = random.nextInt(61) - 30; // -30 to +30 minutes
        calendar.add(Calendar.MINUTE, randomMinutes);

        scheduleNotificationAtTime(context, notificationType, calendar.getTimeInMillis());
    }

    private static void scheduleWeeklyNotification(Context context, int notificationType, int dayOfWeek, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // If it's already past this week's target, schedule for next week
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }

        scheduleNotificationAtTime(context, notificationType, calendar.getTimeInMillis());
    }

    private static void scheduleNotificationAtTime(Context context, int notificationType, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SmartNotificationReceiver.class);
        intent.putExtra("notification_type", notificationType);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationType,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timeInMillis);
            Log.d(TAG, "Smart notification scheduled for: " + cal.getTime() + " (Type: " + notificationType + ")");
        }
    }

    public static void cancelAllNotifications(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        int[] notificationTypes = {
                MINING_REMINDER, STREAK_BONUS, REFERRAL_REWARD,
                BOOST_AVAILABLE, WEEKLY_CHALLENGE, COMEBACK_REMINDER, ACHIEVEMENT_UNLOCK
        };

        for (int type : notificationTypes) {
            Intent intent = new Intent(context, SmartNotificationReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, type, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    // Helper class to store user behavior data
    private static class UserBehaviorData {
        List<Integer> mostActiveHours = new ArrayList<>();
        boolean prefersMorningMining = false;
        boolean prefersEveningMining = false;
        double engagementRate = 0.5;
        int estimatedTimezone = 0;
        boolean isActiveUser = false;
        boolean isAtRiskOfChurn = false;

        @Override
        public String toString() {
            return "UserBehaviorData{" +
                    "mostActiveHours=" + mostActiveHours +
                    ", prefersMorningMining=" + prefersMorningMining +
                    ", prefersEveningMining=" + prefersEveningMining +
                    ", engagementRate=" + engagementRate +
                    ", isActiveUser=" + isActiveUser +
                    ", isAtRiskOfChurn=" + isAtRiskOfChurn +
                    '}';
        }
    }
}
