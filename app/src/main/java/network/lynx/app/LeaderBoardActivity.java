package network.lynx.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeaderBoardActivity extends AppCompatActivity {

    private static final String TAG = "LeaderBoardActivity";
    private DatabaseReference databaseReference;
    private RecyclerView recyclerView1;
    private LeaderBoardAdapter adapter;
    private List<LeaderBoardModel> items;
    private DecimalFormat df;
    private ImageView back;
    private ProgressBar progressBar;
    private TextView rankText, coinsText;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leader_board);

        // Initialize UI components
        recyclerView1 = findViewById(R.id.recyclerView1);
        recyclerView1.setLayoutManager(new LinearLayoutManager(this));
        back = findViewById(R.id.backNav);
        progressBar = findViewById(R.id.progressBar);
        rankText = findViewById(R.id.rank_text);
        coinsText = findViewById(R.id.coins_text);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        items = new ArrayList<>();
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        adapter = new LeaderBoardAdapter(this, items, currentUid);
        recyclerView1.setAdapter(adapter);

        // Number formatting
        df = new DecimalFormat("#.00");

        // Back button
        back.setOnClickListener(view -> finish());

        // Firebase Database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        // Set up swipe to refresh
        swipeRefreshLayout.setOnRefreshListener(this::loadLeaderBoard);
        swipeRefreshLayout.setColorSchemeResources(R.color.gold, R.color.colorPrimary);

        // Load leaderboard data
        loadLeaderBoard();
    }

    private void loadLeaderBoard() {
        if (!swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        recyclerView1.setVisibility(View.GONE);

        databaseReference.keepSynced(false);

        databaseReference.orderByChild("totalcoins").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    items.clear();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String uid = snapshot.getKey();
                        String username = snapshot.child("username").getValue(String.class);
                        String profilePic = snapshot.child("profilePicUrl").getValue(String.class);
                        Double coins = snapshot.child("totalcoins").getValue(Double.class);
                        Double totalDailyStreak = snapshot.child("totalStreak").getValue(Double.class);

                        if (totalDailyStreak == null) totalDailyStreak = 0.0;
                        if (username == null) username = "Unknown";
                        if (coins == null) coins = 0.0;

                        // FIXED: Use the original coins value directly, no need to format and parse
                        // Remove these problematic lines:
                        // String formattedCoins = df.format(coins);
                        // double finalCoins = Double.parseDouble(formattedCoins);

                        // Use this instead:
                        // Instead of formatting and parsing:
                        double finalCoins = Math.round(coins * 100.0) / 100.0;

                        items.add(new LeaderBoardModel(uid, username, profilePic != null ? profilePic : null, finalCoins));
                    }

                    Collections.reverse(items);
                    adapter.notifyDataSetChanged();

                    // Find and display current user's rank
                    String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    int currentUserPosition = -1;
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).getUid().equals(currentUid)) {
                            currentUserPosition = i;
                            break;
                        }
                    }

                    if (currentUserPosition != -1) {
                        LeaderBoardModel currentUser = items.get(currentUserPosition);
                        rankText.setText("" + (currentUserPosition + 1));
                        coinsText.setText("" + LeaderBoardAdapter.formatNumber(currentUser.getCoins()));
                    } else {
                        findViewById(R.id.current_user_rank).setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing leaderboard data", e);
                    ToastUtils.showInfo(LeaderBoardActivity.this, "Error loading leaderboard data");
                } finally {
                    progressBar.setVisibility(View.GONE);
                    recyclerView1.setVisibility(View.VISIBLE);
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading data", databaseError.toException());
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                ToastUtils.showInfo(LeaderBoardActivity.this, "Failed to load leaderboard data");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when activity resumes
        loadLeaderBoard();
    }
}