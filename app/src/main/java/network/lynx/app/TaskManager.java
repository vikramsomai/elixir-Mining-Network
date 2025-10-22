package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.concurrent.TimeUnit;

public class TaskManager {
    private static final String TAG = "TaskManager";
    private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

    private Context context;
    private String userId;
    private SharedPreferences prefs;
    private DatabaseReference userRef;
    private BoostManager boostManager;

    public TaskManager(Context context, String userId) {
        Log.d(TAG, "=== TaskManager initialization START ===");

        try {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            if (userId == null || userId.isEmpty()) {
                throw new IllegalArgumentException("User ID cannot be null or empty");
            }

            Log.d(TAG, "Initializing TaskManager for user: " + userId);

            this.context = context;
            this.userId = userId;

            Log.d(TAG, "Creating SharedPreferences...");
            this.prefs = context.getSharedPreferences("TaskManager_" + userId, Context.MODE_PRIVATE);
            Log.d(TAG, "✅ SharedPreferences created");

            Log.d(TAG, "Creating Firebase reference...");
            this.userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            Log.d(TAG, "✅ Firebase reference created");

            Log.d(TAG, "Linking BoostManager...");
            try {
                this.boostManager = BoostManager.getInstance(context);
                Log.d(TAG, "✅ BoostManager linked successfully");
            } catch (Exception e) {
                Log.w(TAG, "⚠️ BoostManager not available, some features may not work", e);
                // Don't throw here, allow TaskManager to work without BoostManager
            }

            Log.d(TAG, "=== TaskManager initialization COMPLETED ===");

        } catch (Exception e) {
            Log.e(TAG, "❌ FATAL ERROR initializing TaskManager", e);
            throw e;
        }
    }

    // Method to track mining ad completion
    public void completeMiningAdTask() {
        Log.d(TAG, "Mining ad task completed");
    }

    // Method to track temporary boost completion
    public void completeTemporaryBoostTask() {
        Log.d(TAG, "Temporary boost task completed");
    }

    // Enhanced isTaskCompleted method to handle all task types
    public boolean isTaskCompleted(BoostActivity.TaskItem task, DataSnapshot snapshot) {
        try {
            if (task == null) {
                Log.w(TAG, "Task is null in isTaskCompleted");
                return false;
            }

            Log.d(TAG, "Checking completion for task: " + task.getTitle());

            switch (task.getTaskType()) {
                case INVITE_FRIENDS:
                    return isInviteFriendsCompleted(snapshot);
                case FOLLOW_TWITTER:
                    return isTwitterFollowActive();
                case DAILY_CHECKIN:
                    return isDailyCheckinActive() || hasCheckedInToday(snapshot);
                case WATCH_AD:
                    return boostManager != null && boostManager.isAdWatched();
                case TEMPORARY_BOOST:
                    return boostManager != null && boostManager.isTemporaryBoostActive();

                // NEW: Additional engagement tasks
                case JOIN_TELEGRAM:
                    return isTelegramJoinActive();
                case SUBSCRIBE_YOUTUBE:
                    return isYouTubeSubscribeActive();
                case LIKE_FACEBOOK:
                    return isFacebookLikeActive();
                case FOLLOW_INSTAGRAM:
                    return isInstagramFollowActive();
                case JOIN_DISCORD:
                    return isDiscordJoinActive();
                case PLAY_MINI_GAME:
                    return hasPLayedMiniGameToday();
                case RATE_APP:
                    return hasRatedApp(snapshot);
                case SHARE_APP:
                    return hasSharedAppToday();

                default:
                    Log.w(TAG, "Unknown task type: " + task.getTaskType());
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking task completion for " + (task != null ? task.getTitle() : "null"), e);
            return false;
        }
    }

    // Enhanced getTaskStatus method for all task types
    public String getTaskStatus(BoostActivity.TaskItem task, DataSnapshot snapshot) {
        try {
            if (task == null) {
                Log.w(TAG, "Task is null in getTaskStatus");
                return "Unknown task";
            }

            Log.d(TAG, "Getting status for task: " + task.getTitle());

            switch (task.getTaskType()) {
                case INVITE_FRIENDS:
                    long inviteCount = snapshot.child("referrals").exists() ?
                            snapshot.child("referrals").getChildrenCount() : 0;
                    return inviteCount >= 3 ? "✅ Lifetime boost active" : "Invite more friends";

                case FOLLOW_TWITTER:
                    return isTwitterFollowActive() ?
                            String.format("✅ Active (%d hours left)", getTwitterFollowTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to follow";

                case DAILY_CHECKIN:
                    if (isDailyCheckinActive()) {
                        return String.format("✅ Active (%d hours left)", getDailyCheckinTimeRemaining() / (60 * 60 * 1000));
                    } else if (hasCheckedInToday(snapshot)) {
                        return "✅ Completed today";
                    } else {
                        return "Tap to check in";
                    }

                case WATCH_AD:
                    return boostManager != null && boostManager.isAdWatched() ?
                            "✅ Active this session" : "Go to Mining tab";

                case TEMPORARY_BOOST:
                    if (boostManager != null && boostManager.isTemporaryBoostActive()) {
                        long timeRemaining = boostManager.getTemporaryBoostTimeRemaining();
                        return String.format("✅ Active (%d min left)", timeRemaining / 60000);
                    }
                    return "Watch boost ad in Mining";

                // NEW: Additional engagement task statuses
                case JOIN_TELEGRAM:
                    return isTelegramJoinActive() ?
                            String.format("✅ Active (%d hours left)", getTelegramJoinTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to join";

                case SUBSCRIBE_YOUTUBE:
                    return isYouTubeSubscribeActive() ?
                            String.format("✅ Active (%d hours left)", getYouTubeSubscribeTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to subscribe";

                case LIKE_FACEBOOK:
                    return isFacebookLikeActive() ?
                            String.format("✅ Active (%d hours left)", getFacebookLikeTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to like";

                case FOLLOW_INSTAGRAM:
                    return isInstagramFollowActive() ?
                            String.format("✅ Active (%d hours left)", getInstagramFollowTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to follow";

                case JOIN_DISCORD:
                    return isDiscordJoinActive() ?
                            String.format("✅ Active (%d hours left)", getDiscordJoinTimeRemaining() / (60 * 60 * 1000)) :
                            "Tap to join";

                case PLAY_MINI_GAME:
                    return hasPLayedMiniGameToday() ? "✅ Completed today" : "Tap to play";

                case RATE_APP:
                    return hasRatedApp(snapshot) ? "✅ Thank you!" : "Tap to rate";

                case SHARE_APP:
                    return hasSharedAppToday() ? "✅ Shared today" : "Tap to share";

                default:
                    Log.w(TAG, "Unknown task type: " + task.getTaskType());
                    return "Unknown task";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting task status for " + (task != null ? task.getTitle() : "null"), e);
            return "Error loading status";
        }
    }

    public String getTaskProgress(BoostActivity.TaskItem task, DataSnapshot snapshot) {
        try {
            if (task == null) {
                return "";
            }

            switch (task.getTaskType()) {
                case INVITE_FRIENDS:
                    long inviteCount = snapshot.child("referrals").exists() ?
                            snapshot.child("referrals").getChildrenCount() : 0;
                    return String.format("%d/3 friends invited", inviteCount);
                default:
                    return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting task progress", e);
            return "";
        }
    }

    // Existing methods
    private boolean isInviteFriendsCompleted(DataSnapshot snapshot) {
        try {
            long inviteCount = snapshot.child("referrals").exists() ?
                    snapshot.child("referrals").getChildrenCount() : 0;
            return inviteCount >= 3;
        } catch (Exception e) {
            Log.e(TAG, "Error checking invite friends completion", e);
            return false;
        }
    }

    public boolean isTwitterFollowActive() {
        try {
            long completionTime = prefs.getLong("twitter_follow_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Twitter follow status", e);
            return false;
        }
    }

    public boolean isDailyCheckinActive() {
        try {
            long completionTime = prefs.getLong("daily_checkin_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking daily checkin status", e);
            return false;
        }
    }

    // NEW: Additional engagement task methods
    public boolean isTelegramJoinActive() {
        try {
            long completionTime = prefs.getLong("telegram_join_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Telegram join status", e);
            return false;
        }
    }

    public boolean isYouTubeSubscribeActive() {
        try {
            long completionTime = prefs.getLong("youtube_subscribe_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking YouTube subscribe status", e);
            return false;
        }
    }

    public boolean isFacebookLikeActive() {
        try {
            long completionTime = prefs.getLong("facebook_like_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Facebook like status", e);
            return false;
        }
    }

    public boolean isInstagramFollowActive() {
        try {
            long completionTime = prefs.getLong("instagram_follow_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Instagram follow status", e);
            return false;
        }
    }

    public boolean isDiscordJoinActive() {
        try {
            long completionTime = prefs.getLong("discord_join_completion", 0);
            if (completionTime == 0) return false;

            long elapsed = System.currentTimeMillis() - completionTime;
            return elapsed < DAY_IN_MILLIS;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Discord join status", e);
            return false;
        }
    }

    public boolean hasPLayedMiniGameToday() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String lastPlayDate = prefs.getString("mini_game_last_play", "");
            return today.equals(lastPlayDate);
        } catch (Exception e) {
            Log.e(TAG, "Error checking mini game status", e);
            return false;
        }
    }

    public boolean hasRatedApp(DataSnapshot snapshot) {
        try {
            Boolean rated = snapshot.child("hasRatedApp").getValue(Boolean.class);
            return rated != null && rated;
        } catch (Exception e) {
            Log.e(TAG, "Error checking app rating status", e);
            return false;
        }
    }

    public boolean hasSharedAppToday() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String lastShareDate = prefs.getString("app_share_last_date", "");
            return today.equals(lastShareDate);
        } catch (Exception e) {
            Log.e(TAG, "Error checking app share status", e);
            return false;
        }
    }

    // Check if user has checked in today via HomeFragment
    private boolean hasCheckedInToday(DataSnapshot snapshot) {
        try {
            String lastDate = snapshot.child("lastDate").getValue(String.class);
            if (lastDate == null) return false;

            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            return today.equals(lastDate);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if checked in today", e);
            return false;
        }
    }

    // Time remaining methods
    public long getTwitterFollowTimeRemaining() {
        try {
            long completionTime = prefs.getLong("twitter_follow_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Twitter follow time remaining", e);
            return 0;
        }
    }

    public long getDailyCheckinTimeRemaining() {
        try {
            long completionTime = prefs.getLong("daily_checkin_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting daily checkin time remaining", e);
            return 0;
        }
    }

    // NEW: Time remaining methods for additional tasks
    public long getTelegramJoinTimeRemaining() {
        try {
            long completionTime = prefs.getLong("telegram_join_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Telegram join time remaining", e);
            return 0;
        }
    }

    public long getYouTubeSubscribeTimeRemaining() {
        try {
            long completionTime = prefs.getLong("youtube_subscribe_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting YouTube subscribe time remaining", e);
            return 0;
        }
    }

    public long getFacebookLikeTimeRemaining() {
        try {
            long completionTime = prefs.getLong("facebook_like_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Facebook like time remaining", e);
            return 0;
        }
    }

    public long getInstagramFollowTimeRemaining() {
        try {
            long completionTime = prefs.getLong("instagram_follow_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Instagram follow time remaining", e);
            return 0;
        }
    }

    public long getDiscordJoinTimeRemaining() {
        try {
            long completionTime = prefs.getLong("discord_join_completion", 0);
            if (completionTime == 0) return 0;

            long elapsed = System.currentTimeMillis() - completionTime;
            return Math.max(0, DAY_IN_MILLIS - elapsed);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Discord join time remaining", e);
            return 0;
        }
    }

    // Completion methods
    public void completeTwitterFollow() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("twitter_follow_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("twitterFollow")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("twitterFollow")
                    .child("multiplier").setValue(1.2);
            userRef.child("activeBoosts").child("twitterFollow")
                    .child("type").setValue("twitter_follow");

            // Update BoostManager
            if (boostManager != null) {
                boostManager.activateTwitterBoost(currentTime + DAY_IN_MILLIS);
            }

            Log.d(TAG, "Twitter follow task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing Twitter follow", e);
        }
    }

    public void completeDailyCheckin() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("daily_checkin_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("dailyCheckin")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("dailyCheckin")
                    .child("multiplier").setValue(1.1);
            userRef.child("activeBoosts").child("dailyCheckin")
                    .child("type").setValue("daily_checkin");

            // Update BoostManager
            if (boostManager != null) {
                boostManager.activateDailyCheckinBoost(currentTime + DAY_IN_MILLIS);
            }

            Log.d(TAG, "Daily checkin task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing daily checkin", e);
        }
    }

    // NEW: Completion methods for additional engagement tasks
    public void completeTelegramJoin() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("telegram_join_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("telegramJoin")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("telegramJoin")
                    .child("multiplier").setValue(1.15);
            userRef.child("activeBoosts").child("telegramJoin")
                    .child("type").setValue("telegram_join");

            Log.d(TAG, "Telegram join task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing Telegram join", e);
        }
    }

    public void completeYouTubeSubscribe() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("youtube_subscribe_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("youtubeSubscribe")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("youtubeSubscribe")
                    .child("multiplier").setValue(1.15);
            userRef.child("activeBoosts").child("youtubeSubscribe")
                    .child("type").setValue("youtube_subscribe");

            Log.d(TAG, "YouTube subscribe task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing YouTube subscribe", e);
        }
    }

    public void completeFacebookLike() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("facebook_like_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("facebookLike")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("facebookLike")
                    .child("multiplier").setValue(1.1);
            userRef.child("activeBoosts").child("facebookLike")
                    .child("type").setValue("facebook_like");

            Log.d(TAG, "Facebook like task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing Facebook like", e);
        }
    }

    public void completeInstagramFollow() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("instagram_follow_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("instagramFollow")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("instagramFollow")
                    .child("multiplier").setValue(1.1);
            userRef.child("activeBoosts").child("instagramFollow")
                    .child("type").setValue("instagram_follow");

            Log.d(TAG, "Instagram follow task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing Instagram follow", e);
        }
    }

    public void completeDiscordJoin() {
        try {
            long currentTime = System.currentTimeMillis();
            prefs.edit().putLong("discord_join_completion", currentTime).apply();

            // Apply boost to Firebase
            userRef.child("activeBoosts").child("discordJoin")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("discordJoin")
                    .child("multiplier").setValue(1.15);
            userRef.child("activeBoosts").child("discordJoin")
                    .child("type").setValue("discord_join");

            Log.d(TAG, "Discord join task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing Discord join", e);
        }
    }

    public void completeMiniGame() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            prefs.edit().putString("mini_game_last_play", today).apply();

            // Apply daily boost
            long currentTime = System.currentTimeMillis();
            userRef.child("activeBoosts").child("miniGame")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("miniGame")
                    .child("multiplier").setValue(1.05);
            userRef.child("activeBoosts").child("miniGame")
                    .child("type").setValue("mini_game");

            Log.d(TAG, "Mini game task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing mini game", e);
        }
    }

    public void completeAppRating() {
        try {
            // Mark as permanently completed
            userRef.child("hasRatedApp").setValue(true);

            // Apply permanent boost
            userRef.child("permanentBoosts").child("appRating").setValue(true);
            userRef.child("permanentBoosts").child("appRatingMultiplier").setValue(1.2);
            userRef.child("permanentBoosts").child("appRatingAppliedAt").setValue(System.currentTimeMillis());

            Log.d(TAG, "App rating task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing app rating", e);
        }
    }

    public void completeAppShare() {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            prefs.edit().putString("app_share_last_date", today).apply();

            // Apply daily boost
            long currentTime = System.currentTimeMillis();
            userRef.child("activeBoosts").child("appShare")
                    .child("expirationTime").setValue(currentTime + DAY_IN_MILLIS);
            userRef.child("activeBoosts").child("appShare")
                    .child("multiplier").setValue(1.1);
            userRef.child("activeBoosts").child("appShare")
                    .child("type").setValue("app_share");

            Log.d(TAG, "App share task completed");
        } catch (Exception e) {
            Log.e(TAG, "Error completing app share", e);
        }
    }

    public void completePermanentInviteBoost() {
        try {
            // Apply permanent boost
            userRef.child("permanentBoosts").child("invite3Friends").setValue(true);
            userRef.child("permanentBoosts").child("invite3FriendsMultiplier").setValue(1.5);
            userRef.child("permanentBoosts").child("appliedAt").setValue(System.currentTimeMillis());

            Log.d(TAG, "Permanent invite boost applied");
        } catch (Exception e) {
            Log.e(TAG, "Error completing permanent invite boost", e);
        }
    }

    // Method to sync with HomeFragment check-in
    public void syncWithHomeFragmentCheckin() {
        if (!isDailyCheckinActive()) {
            completeDailyCheckin();
        }
    }

    // Method to handle spin completion
    public void completeSpinTask() {
        // You can add spin-related boost logic here if needed
        Log.d(TAG, "Spin task completed");
    }
}
