package network.lynx.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class BoostActivity extends AppCompatActivity implements BoostManager.BoostChangeListener {
    private static final String TAG = "BoostActivity";

    private TextView farmingSpeed, boostStatus;
    private ImageView backButton;
    private RecyclerView comingSoonTasksRecyclerView, completedTasksRecyclerView;
    private ComingSoonTasksAdapter comingSoonAdapter, completedAdapter;
    private List<TaskItem> comingSoonTasksList, completedTasksList;
    private DatabaseReference userRef;
    private String userId;
    private BoostManager boostManager;
    private TaskManager taskManager;

    // NEW: BroadcastReceiver for task refresh
    private BroadcastReceiver taskRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            if ("REFRESH_BOOST_TASKS".equals(intent.getAction())) {
                Log.d(TAG, "Received task refresh broadcast");
                refreshTaskLists();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== BoostActivity onCreate START ===");

        try {
            // Step 1: Set content view
            Log.d(TAG, "Step 1: Setting content view...");
            setContentView(R.layout.activity_boost);
            Log.d(TAG, "✅ Step 1: Layout set successfully");

            // Step 2: Check authentication
            Log.d(TAG, "Step 2: Checking authentication...");
            if (!checkAuthentication()) {
                Log.e(TAG, "❌ Step 2: Authentication failed");
                return;
            }
            Log.d(TAG, "✅ Step 2: Authentication successful");

            // Step 3: Initialize views
            Log.d(TAG, "Step 3: Initializing views...");
            if (!initializeViews()) {
                Log.e(TAG, "❌ Step 3: View initialization failed");
                return;
            }
            Log.d(TAG, "✅ Step 3: Views initialized successfully");

            // Step 4: Setup click listeners
            Log.d(TAG, "Step 4: Setting up click listeners...");
            setupClickListeners();
            Log.d(TAG, "✅ Step 4: Click listeners set up successfully");

            // Step 5: Initialize managers
            Log.d(TAG, "Step 5: Initializing managers...");
            if (!initializeManagers()) {
                Log.e(TAG, "❌ Step 5: Manager initialization failed");
                return;
            }
            Log.d(TAG, "✅ Step 5: Managers initialized successfully");

            // Step 6: Setup RecyclerViews
            Log.d(TAG, "Step 6: Setting up RecyclerViews...");
            if (!setupRecyclerViews()) {
                Log.e(TAG, "❌ Step 6: RecyclerView setup failed");
                return;
            }
            Log.d(TAG, "✅ Step 6: RecyclerViews set up successfully");

            // Step 7: Load boost data
            Log.d(TAG, "Step 7: Loading boost data...");
            loadBoostData();
            Log.d(TAG, "✅ Step 7: Boost data loading started");

            // Step 8: Register broadcast receiver
            Log.d(TAG, "Step 8: Registering broadcast receiver...");
            registerBroadcastReceiver();
            Log.d(TAG, "✅ Step 8: Broadcast receiver registered successfully");

            Log.d(TAG, "=== BoostActivity onCreate COMPLETED SUCCESSFULLY ===");

        } catch (Exception e) {
            Log.e(TAG, "❌ FATAL ERROR in onCreate", e);
            showErrorAndFinish("Failed to initialize boost activity: " + e.getMessage());
        }
    }

    private boolean checkAuthentication() {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.e(TAG, "User not authenticated");
                showErrorAndFinish("Please log in to access boost features");
                return false;
            }

            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (userId == null || userId.isEmpty()) {
                Log.e(TAG, "User ID is null or empty");
                showErrorAndFinish("Invalid user session");
                return false;
            }

            Log.d(TAG, "User authenticated with ID: " + userId);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception during authentication check", e);
            showErrorAndFinish("Authentication error: " + e.getMessage());
            return false;
        }
    }

    private boolean initializeViews() {
        try {
            Log.d(TAG, "Looking for farmingSpeed view...");
            farmingSpeed = findViewById(R.id.farmingSpeed);
            if (farmingSpeed == null) {
                Log.e(TAG, "farmingSpeed view not found in layout");
                showErrorAndFinish("Layout error: farmingSpeed view missing");
                return false;
            }
            Log.d(TAG, "✅ farmingSpeed view found");

            Log.d(TAG, "Looking for boostStatus view...");
            boostStatus = findViewById(R.id.boostStatus);
            if (boostStatus == null) {
                Log.e(TAG, "boostStatus view not found in layout");
                showErrorAndFinish("Layout error: boostStatus view missing");
                return false;
            }
            Log.d(TAG, "✅ boostStatus view found");

            Log.d(TAG, "Looking for backButton view...");
            backButton = findViewById(R.id.backButton);
            if (backButton == null) {
                Log.e(TAG, "backButton view not found in layout");
                showErrorAndFinish("Layout error: backButton view missing");
                return false;
            }
            Log.d(TAG, "✅ backButton view found");

            Log.d(TAG, "Looking for comingSoonTasksRecyclerView...");
            comingSoonTasksRecyclerView = findViewById(R.id.boostTasksRecyclerView);
            if (comingSoonTasksRecyclerView == null) {
                Log.e(TAG, "boostTasksRecyclerView not found in layout");
                showErrorAndFinish("Layout error: boostTasksRecyclerView view missing");
                return false;
            }
            Log.d(TAG, "✅ comingSoonTasksRecyclerView found");

            Log.d(TAG, "Looking for completedTasksRecyclerView...");
            completedTasksRecyclerView = findViewById(R.id.completedTasksRecyclerView);
            if (completedTasksRecyclerView == null) {
                Log.e(TAG, "completedTasksRecyclerView not found in layout");
                showErrorAndFinish("Layout error: completedTasksRecyclerView view missing");
                return false;
            }
            Log.d(TAG, "✅ completedTasksRecyclerView found");

            // Initialize Firebase reference
            Log.d(TAG, "Initializing Firebase reference...");
            userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            Log.d(TAG, "✅ Firebase reference initialized");

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception during view initialization", e);
            showErrorAndFinish("View initialization error: " + e.getMessage());
            return false;
        }
    }

    private void setupClickListeners() {
        try {
            backButton.setOnClickListener(v -> {
                Log.d(TAG, "Back button clicked");
                finish();
            });
            Log.d(TAG, "Click listeners set up successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up click listeners", e);
            throw e;
        }
    }

    private boolean initializeManagers() {
        try {
            // Initialize BoostManager
            Log.d(TAG, "Initializing BoostManager...");
            boostManager = BoostManager.getInstance(this);
            if (boostManager == null) {
                Log.e(TAG, "BoostManager initialization returned null");
                showErrorAndFinish("Failed to initialize boost system");
                return false;
            }
            boostManager.addBoostChangeListener(this);
            Log.d(TAG, "✅ BoostManager initialized successfully");

            // Initialize TaskManager
            Log.d(TAG, "Initializing TaskManager...");
            taskManager = new TaskManager(this, userId);
            if (taskManager == null) {
                Log.e(TAG, "TaskManager initialization returned null");
                showErrorAndFinish("Failed to initialize task system");
                return false;
            }
            Log.d(TAG, "✅ TaskManager initialized successfully");

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception during manager initialization", e);
            showErrorAndFinish("Manager initialization error: " + e.getMessage());
            return false;
        }
    }

    private boolean setupRecyclerViews() {
        try {
            Log.d(TAG, "Creating task lists...");
            comingSoonTasksList = new ArrayList<>();
            completedTasksList = new ArrayList<>();
            Log.d(TAG, "✅ Task lists created");

            Log.d(TAG, "Creating adapters...");
            comingSoonAdapter = new ComingSoonTasksAdapter(comingSoonTasksList, false, taskManager);
            completedAdapter = new ComingSoonTasksAdapter(completedTasksList, true, taskManager);
            Log.d(TAG, "✅ Adapters created");

            Log.d(TAG, "Setting up coming soon RecyclerView...");
            comingSoonTasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            comingSoonTasksRecyclerView.setAdapter(comingSoonAdapter);
            Log.d(TAG, "✅ Coming soon RecyclerView set up");

            Log.d(TAG, "Setting up completed RecyclerView...");
            completedTasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            completedTasksRecyclerView.setAdapter(completedAdapter);
            Log.d(TAG, "✅ Completed RecyclerView set up");

            Log.d(TAG, "Loading initial tasks...");
            loadTasks();
            Log.d(TAG, "✅ Initial tasks loading started");

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception during RecyclerView setup", e);
            showErrorAndFinish("RecyclerView setup error: " + e.getMessage());
            return false;
        }
    }

    private void registerBroadcastReceiver() {
        try {
            IntentFilter filter = new IntentFilter("REFRESH_BOOST_TASKS");
            registerReceiver(taskRefreshReceiver, filter);
            Log.d(TAG, "Broadcast receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error registering broadcast receiver", e);
            // Don't fail the activity for this
        }
    }

    private void loadTasks() {
        try {
            Log.d(TAG, "Creating task items...");
            List<TaskItem> allTasks = new ArrayList<>();

            // Core engagement tasks
            allTasks.add(new TaskItem("Invite 3 Friends", "x1.5", "Lifetime", TaskItem.TaskType.INVITE_FRIENDS));
            allTasks.add(new TaskItem( "Follow X", "x1.2", "24 hours", TaskItem.TaskType.FOLLOW_TWITTER));
            allTasks.add(new TaskItem( "Daily Check-in", "x1.1", "24 hours", TaskItem.TaskType.DAILY_CHECKIN));
            allTasks.add(new TaskItem( "Watch Ad for Mining", "x2.0", "Per session", TaskItem.TaskType.WATCH_AD));
//            allTasks.add(new TaskItem("⚡", "Temporary Boost", "x1.5", "1 hour", TaskItem.TaskType.TEMPORARY_BOOST));

            // NEW: Additional engagement tasks
            allTasks.add(new TaskItem( "Join Telegram", "x1.15", "24 hours", TaskItem.TaskType.JOIN_TELEGRAM));
//            allTasks.add(new TaskItem( "Subscribe YouTube", "x1.15", "24 hours", TaskItem.TaskType.SUBSCRIBE_YOUTUBE));
//            allTasks.add(new TaskItem("Like Facebook Page", "x1.1", "24 hours", TaskItem.TaskType.LIKE_FACEBOOK));
//            allTasks.add(new TaskItem( "Follow Instagram", "x1.1", "24 hours", TaskItem.TaskType.FOLLOW_INSTAGRAM));
            allTasks.add(new TaskItem( "Join Discord", "x1.15", "24 hours", TaskItem.TaskType.JOIN_DISCORD));
//            allTasks.add(new TaskItem( "Play Mini Game", "x1.05", "Daily", TaskItem.TaskType.PLAY_MINI_GAME));
            allTasks.add(new TaskItem( "Rate App", "x1.2", "Lifetime", TaskItem.TaskType.RATE_APP));
            allTasks.add(new TaskItem( "Share App", "x1.1", "Daily", TaskItem.TaskType.SHARE_APP));

            Log.d(TAG, "Created " + allTasks.size() + " task items");

            checkTaskCompletion(allTasks);

        } catch (Exception e) {
            Log.e(TAG, "Error loading tasks", e);
            // Don't crash the activity, just show error
            if (farmingSpeed != null) {
                farmingSpeed.setText("Error loading tasks");
            }
            if (boostStatus != null) {
                boostStatus.setText("Task loading failed");
            }
        }
    }

    private void checkTaskCompletion(List<TaskItem> allTasks) {
        if (userRef == null) {
            Log.e(TAG, "userRef is null, cannot check task completion");
            return;
        }

        Log.d(TAG, "Checking task completion from Firebase...");
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    Log.d(TAG, "Firebase data received, processing tasks...");

                    comingSoonTasksList.clear();
                    completedTasksList.clear();

                    for (TaskItem task : allTasks) {
                        boolean isCompleted = taskManager.isTaskCompleted(task, snapshot);
                        String status = taskManager.getTaskStatus(task, snapshot);
                        String progress = taskManager.getTaskProgress(task, snapshot);

                        task.setCompleted(isCompleted);
                        task.setStatus(status);
                        task.setProgress(progress);

                        if (isCompleted) {
                            completedTasksList.add(task);
                        } else {
                            comingSoonTasksList.add(task);
                        }
                    }

                    Log.d(TAG, "Tasks processed - Coming soon: " + comingSoonTasksList.size() + ", Completed: " + completedTasksList.size());

                    if (comingSoonAdapter != null) {
                        comingSoonAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Coming soon adapter notified");
                    }
                    if (completedAdapter != null) {
                        completedAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Completed adapter notified");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error processing task completion data", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase error checking task completion", error.toException());
            }
        });
    }

    private void loadBoostData() {
        try {
            Log.d(TAG, "Loading boost data...");
            updateBoostDisplay();

            if (userRef != null) {
                userRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Log.d(TAG, "Boost data updated from Firebase");
                            updateBoostDisplay();
                            loadTasks();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Error loading boost data", error.toException());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in loadBoostData", e);
        }
    }

    private void updateBoostDisplay() {
        try {
            if (boostManager != null) {
                float currentRatePerHour = boostManager.getCurrentMiningRatePerHour();
                String indicators = boostManager.getBoostIndicators();

                if (farmingSpeed != null) {
                    farmingSpeed.setText(String.format("%.4f / hour %s", currentRatePerHour, indicators));
                }
                updateBoostStatus();

                Log.d(TAG, "Boost display updated successfully");
            } else {
                Log.w(TAG, "BoostManager is null, using fallback display");
                if (farmingSpeed != null) {
                    farmingSpeed.setText("4.5000 / hour");
                }
                if (boostStatus != null) {
                    boostStatus.setText("Loading...");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating boost display", e);
            if (farmingSpeed != null) {
                farmingSpeed.setText("Error");
            }
            if (boostStatus != null) {
                boostStatus.setText("Display error");
            }
        }
    }

    private void updateBoostStatus() {
        try {
            if (boostManager == null || boostStatus == null) {
                Log.w(TAG, "BoostManager or boostStatus is null");
                return;
            }

            List<String> activeBoosts = new ArrayList<>();
            float totalMultiplier = 1.0f;

            // Check permanent boost
            if (boostManager.hasPermanentBoost()) {
                float permMultiplier = boostManager.getPermanentBoostMultiplier();
                activeBoosts.add(String.format("Permanent +%.0f%%", (permMultiplier - 1) * 100));
                totalMultiplier *= permMultiplier;
            }

            // Check ad boost
            if (boostManager.isAdWatched()) {
                activeBoosts.add("Ad +100%");
                totalMultiplier *= 2.0f;
            }

            // Check temporary boost
            if (boostManager.isTemporaryBoostActive()) {
                long timeRemaining = boostManager.getTemporaryBoostTimeRemaining();
                activeBoosts.add(String.format("⚡ Temp +50%% (%dm)", timeRemaining / 60000));
                totalMultiplier *= 1.5f;
            }

            // Check daily boosts
            if (taskManager != null) {
                if (taskManager.isDailyCheckinActive()) {
                    activeBoosts.add("Daily +10%");
                    totalMultiplier *= 1.1f;
                }
                if (taskManager.isTwitterFollowActive()) {
                    activeBoosts.add("Twitter +20%");
                    totalMultiplier *= 1.2f;
                }
                // NEW: Check additional social boosts
                if (taskManager.isTelegramJoinActive()) {
                    activeBoosts.add("Telegram +15%");
                    totalMultiplier *= 1.15f;
                }
                if (taskManager.isYouTubeSubscribeActive()) {
                    activeBoosts.add("YouTube +15%");
                    totalMultiplier *= 1.15f;
                }
            }

            if (activeBoosts.isEmpty()) {
                boostStatus.setText("No boosts active");
                boostStatus.setTextColor(getResources().getColor(android.R.color.secondary_text_light));
            } else {
                String statusText;
                if (activeBoosts.size() == 1) {
                    statusText = activeBoosts.get(0);
                } else {
                    statusText = String.format("x%.1f Total Boost Active", totalMultiplier);
                }
                boostStatus.setText(statusText);
                boostStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            Log.d(TAG, "Boost status updated: " + boostStatus.getText());

        } catch (Exception e) {
            Log.e(TAG, "Error updating boost status", e);
        }
    }

    private void showErrorAndFinish(String message) {
        Log.e(TAG, "Showing error and finishing: " + message);
        ToastUtils.showError(this, message);
        finish();
    }

    // BoostManager.BoostChangeListener implementation
    @Override
    public void onBoostStateChanged(float currentMiningRate, String boostInfo) {
        runOnUiThread(() -> {
            Log.d(TAG, "Boost state changed: " + boostInfo);
            updateBoostDisplay();
            loadTasks();
        });
    }

    @Override
    public void onPermanentBoostChanged(boolean hasPermanentBoost, float multiplier) {
        runOnUiThread(() -> {
            Log.d(TAG, "Permanent boost changed: " + hasPermanentBoost);
            updateBoostDisplay();
            if (hasPermanentBoost) {
                ToastUtils.showInfo(this, "Permanent boost is active! +" +
                        String.format("%.0f", (multiplier - 1) * 100) + "%");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "BoostActivity onResume");
        updateBoostDisplay();
        loadTasks();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "BoostActivity onDestroy");
        try {
            if (boostManager != null) {
                boostManager.removeBoostChangeListener(this);
            }

            // Unregister broadcast receiver
            try {
                unregisterReceiver(taskRefreshReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver was not registered", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        } finally {
            super.onDestroy();
        }
    }

    // Method to refresh task lists (called by adapter)
    public void refreshTaskLists() {
        Log.d(TAG, "Refreshing task lists");
        loadTasks();
    }

    // Enhanced TaskItem class with new task types
    public static class TaskItem {
        public enum TaskType {
            INVITE_FRIENDS,
            FOLLOW_TWITTER,
            DAILY_CHECKIN,
            WATCH_AD,
            TEMPORARY_BOOST,
            // NEW: Additional engagement tasks
            JOIN_TELEGRAM,
            SUBSCRIBE_YOUTUBE,
            LIKE_FACEBOOK,
            FOLLOW_INSTAGRAM,
            JOIN_DISCORD,
            PLAY_MINI_GAME,
            RATE_APP,
            SHARE_APP
        }

//        private String icon;
        private String title;
        private String multiplier;
        private String duration;
        private boolean completed;
        private String progress;
        private String status;
        private TaskType taskType;

        public TaskItem(String title, String multiplier, String duration, TaskType taskType) {
//            this.icon = icon;
            this.title = title;
            this.multiplier = multiplier;
            this.duration = duration;
            this.taskType = taskType;
            this.completed = false;
            this.progress = "";
            this.status = "";
        }

        // Getters and setters
//        public String getIcon() { return icon; }
        public String getTitle() { return title; }
        public String getMultiplier() { return multiplier; }
        public String getDuration() { return duration; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public String getProgress() { return progress; }
        public void setProgress(String progress) { this.progress = progress; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public TaskType getTaskType() { return taskType; }
    }
}
