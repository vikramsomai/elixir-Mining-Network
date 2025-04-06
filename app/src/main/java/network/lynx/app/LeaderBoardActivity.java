package network.lynx.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private DatabaseReference databaseReference;
    private RecyclerView recyclerView1;
    private LeaderBoardAdapter adapter;
    private List<LeaderBoardModel> items;
    private DecimalFormat df;
    private ImageView back;
    private ProgressBar progressBar;
    private TextView rankText, usernameText, coinsText;

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
//        usernameText = findViewById(R.id.username_text);
        coinsText = findViewById(R.id.coins_text);

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

        // Load leaderboard data
        loadLeaderBoard();
    }

    private void loadLeaderBoard() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView1.setVisibility(View.GONE);

        databaseReference.orderByChild("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
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
                    String formattedCoins = df.format(coins);
                    double finalCoins = Double.parseDouble(formattedCoins);
                    items.add(new LeaderBoardModel(uid, username, profilePic!=null?profilePic:null, finalCoins));
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
                    rankText.setText("Your Rank: " + (currentUserPosition + 1));
//                    usernameText.setText("Username: " + currentUser.getUsername());
                    coinsText.setText("Coins: " + LeaderBoardAdapter.formatNumber(currentUser.getCoins()));
                } else {
                    findViewById(R.id.current_user_rank).setVisibility(View.GONE);
                }

                progressBar.setVisibility(View.GONE);
                recyclerView1.setVisibility(View.VISIBLE);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("LeaderBoard", "Error loading data", databaseError.toException());
                progressBar.setVisibility(View.GONE);
            }
        });
    }
}