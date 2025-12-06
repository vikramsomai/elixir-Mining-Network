package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeaderBoardActivity extends AppCompatActivity {

    private static final String TAG = "LeaderBoardActivity";
    private static final String CACHE_PREFS = "leaderboard_cache";
    private static final String CACHE_KEY = "leaderboard_data";
    private static final String CACHE_TIME_KEY = "cache_time";
    private static final long CACHE_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes cache

    private DatabaseReference databaseReference;
    private RecyclerView recyclerView1;
    private LeaderBoardAdapter adapter;
    private List<LeaderBoardModel> items;
    private ImageView back;
    private ProgressBar progressBar;
    private TextView rankText, coinsText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueEventListener leaderboardListener;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leader_board);

        // Validate user is logged in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "User not logged in");
            finish();
            return;
        }
        currentUid = user.getUid();

        initializeViews();
        setupRecyclerView();
        setupListeners();

        // Load cached data first for instant display
        loadCachedData();

        // Then fetch fresh data from Firebase
        loadLeaderBoard(false);
    }

    private void initializeViews() {
        recyclerView1 = findViewById(R.id.recyclerView1);
        back = findViewById(R.id.backNav);
        progressBar = findViewById(R.id.progressBar);
        rankText = findViewById(R.id.rank_text);
        coinsText = findViewById(R.id.coins_text);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        items = new ArrayList<>();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView1.setLayoutManager(layoutManager);

        // OPTIMIZATION: RecyclerView performance improvements
        recyclerView1.setHasFixedSize(true);
        recyclerView1.setItemViewCacheSize(20);
        recyclerView1.setDrawingCacheEnabled(true);
        recyclerView1.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        adapter = new LeaderBoardAdapter(this, items, currentUid);
        recyclerView1.setAdapter(adapter);
    }

    private void setupListeners() {
        back.setOnClickListener(view -> finish());

        swipeRefreshLayout.setOnRefreshListener(() -> loadLeaderBoard(true));
        swipeRefreshLayout.setColorSchemeResources(R.color.gold, R.color.colorPrimary);
    }

    /**
     * Load cached leaderboard data for instant display
     */
    private void loadCachedData() {
        try {
            SharedPreferences prefs = getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
            String cachedData = prefs.getString(CACHE_KEY, null);
            long cacheTime = prefs.getLong(CACHE_TIME_KEY, 0);

            if (cachedData != null && !cachedData.isEmpty()) {
                JSONArray jsonArray = new JSONArray(cachedData);
                items.clear();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    items.add(new LeaderBoardModel(
                            obj.getString("uid"),
                            obj.getString("username"),
                            obj.optString("profilePic", null),
                            obj.getDouble("coins")
                    ));
                }

                adapter.notifyDataSetChanged();
                updateCurrentUserRank();
                recyclerView1.setVisibility(View.VISIBLE);

                Log.d(TAG, "Loaded " + items.size() + " items from cache");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached data", e);
        }
    }

    /**
     * Save leaderboard data to cache
     */
    private void saveCacheData() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (LeaderBoardModel item : items) {
                JSONObject obj = new JSONObject();
                obj.put("uid", item.getUid());
                obj.put("username", item.getUsername());
                obj.put("profilePic", item.getImage());
                obj.put("coins", item.getCoins());
                jsonArray.put(obj);
            }

            SharedPreferences prefs = getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(CACHE_KEY, jsonArray.toString())
                    .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "Cached " + items.size() + " leaderboard items");
        } catch (Exception e) {
            Log.e(TAG, "Error saving cache", e);
        }
    }

    private void loadLeaderBoard(boolean forceRefresh) {
        // Check if cache is still valid
        if (!forceRefresh && isCacheValid() && !items.isEmpty()) {
            Log.d(TAG, "Using cached data, skipping Firebase fetch");
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (!swipeRefreshLayout.isRefreshing() && items.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // Remove existing listener
        if (leaderboardListener != null && databaseReference != null) {
            databaseReference.removeEventListener(leaderboardListener);
        }

        leaderboardListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (isFinishing() || isDestroyed()) return;

                try {
                    List<LeaderBoardModel> newItems = new ArrayList<>();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String uid = snapshot.getKey();
                        if (uid == null) continue;

                        String username = snapshot.child("username").getValue(String.class);
                        String profilePic = snapshot.child("profilePicUrl").getValue(String.class);
                        Double coins = snapshot.child("totalcoins").getValue(Double.class);

                        if (username == null || username.isEmpty()) username = "Unknown";
                        if (coins == null) coins = 0.0;

                        double finalCoins = Math.round(coins * 100.0) / 100.0;
                        newItems.add(new LeaderBoardModel(uid, username, profilePic, finalCoins));
                    }

                    // Sort by coins descending (since limitToLast gives ascending order)
                    Collections.reverse(newItems);

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        items.clear();
                        items.addAll(newItems);
                        adapter.notifyDataSetChanged();
                        updateCurrentUserRank();

                        // Save to cache
                        saveCacheData();

                        progressBar.setVisibility(View.GONE);
                        recyclerView1.setVisibility(View.VISIBLE);
                        swipeRefreshLayout.setRefreshing(false);

                        Log.d(TAG, "Loaded " + items.size() + " users from Firebase");
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error processing leaderboard data", e);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                        ToastUtils.showError(LeaderBoardActivity.this, "Error loading leaderboard");
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Firebase error: " + databaseError.getMessage(), databaseError.toException());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    ToastUtils.showError(LeaderBoardActivity.this, "Failed to load leaderboard");
                });
            }
        };

        // OPTIMIZATION: Query only top 100 users sorted by totalcoins
        Query query = databaseReference
                .orderByChild("totalcoins")
                .limitToLast(100);

        query.addListenerForSingleValueEvent(leaderboardListener);
    }

    private boolean isCacheValid() {
        SharedPreferences prefs = getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        long cacheTime = prefs.getLong(CACHE_TIME_KEY, 0);
        return (System.currentTimeMillis() - cacheTime) < CACHE_VALIDITY_MS;
    }

    private void updateCurrentUserRank() {
        int currentUserPosition = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getUid().equals(currentUid)) {
                currentUserPosition = i;
                break;
            }
        }

        View rankCard = findViewById(R.id.current_user_rank);
        if (currentUserPosition != -1) {
            LeaderBoardModel currentUser = items.get(currentUserPosition);
            rankText.setText(String.valueOf(currentUserPosition + 1));
            coinsText.setText(LeaderBoardAdapter.formatNumber(currentUser.getCoins()));
            if (rankCard != null) rankCard.setVisibility(View.VISIBLE);
        } else {
            if (rankCard != null) rankCard.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (leaderboardListener != null && databaseReference != null) {
            databaseReference.removeEventListener(leaderboardListener);
            leaderboardListener = null;
        }
    }
}