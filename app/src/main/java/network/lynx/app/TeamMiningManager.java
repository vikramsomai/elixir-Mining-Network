package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TEAM MINING SYSTEM - Inspired by Bee Network
 *
 * Team mining creates collaborative engagement.
 * Users join or create teams, team activity boosts all members.
 *
 * HOW IT WORKS:
 * - Users can join/create a team (max 50 members)
 * - Team earns collective points based on member activity
 * - Weekly team leaderboard with rewards
 * - Team boosts mining for all members
 */
public class TeamMiningManager {
    private static final String TAG = "TeamMiningManager";
    private static final String PREFS_NAME = "team_mining";

    // Configuration
    public static final int MAX_TEAM_SIZE = 50;
    public static final float BASE_TEAM_BOOST = 0.05f; // 5% base
    public static final float BOOST_PER_ACTIVE_MEMBER = 0.005f; // 0.5% per active member
    public static final float MAX_MEMBER_BOOST = 0.25f; // 25% max from members

    private static TeamMiningManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private String currentUserId;

    private Team currentTeam;
    private TeamUpdateListener listener;

    public static class Team {
        public String teamId;
        public String name;
        public String description;
        public String captainId;
        public String captainName;
        public long createdTime;
        public int memberCount;
        public int activeMembers;
        public double totalMined;
        public double weeklyMined;
        public int weeklyRank;
        public boolean isOpen;

        public Team() {}

        public float getBoostMultiplier() {
            float memberBoost = Math.min(activeMembers * BOOST_PER_ACTIVE_MEMBER, MAX_MEMBER_BOOST);
            float rankBonus = getRankBonus();
            return 1.0f + BASE_TEAM_BOOST + memberBoost + rankBonus;
        }

        private float getRankBonus() {
            if (weeklyRank == 1) return 0.50f;
            if (weeklyRank == 2) return 0.40f;
            if (weeklyRank == 3) return 0.30f;
            if (weeklyRank <= 5) return 0.20f;
            if (weeklyRank <= 10) return 0.10f;
            return 0f;
        }
    }

    public static class TeamMember {
        public String odamUserId;
        public String username;
        public double contributedAmount;
        public long joinedTime;
        public long lastActiveTime;
        public boolean isActive;
        public String role;

        public TeamMember() {}
    }

    public interface TeamUpdateListener {
        void onTeamUpdated(Team team);
        void onTeamLeft();
        void onError(String message);
    }

    private TeamMiningManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadCurrentTeam();
        }
    }

    public static synchronized TeamMiningManager getInstance(Context context) {
        if (instance == null) {
            instance = new TeamMiningManager(context);
        }
        return instance;
    }

    public void setListener(TeamUpdateListener listener) {
        this.listener = listener;
    }

    public void loadCurrentTeam() {
        if (currentUserId == null) return;

        dbRef.child("users").child(currentUserId).child("teamId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            String teamId = snapshot.getValue(String.class);
                            if (teamId != null) {
                                loadTeamDetails(teamId);
                            }
                        } else {
                            currentTeam = null;
                            if (listener != null) {
                                listener.onTeamLeft();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error loading team ID", error.toException());
                    }
                });
    }

    private void loadTeamDetails(String teamId) {
        dbRef.child("teams").child(teamId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    currentTeam = snapshot.getValue(Team.class);
                    if (currentTeam != null) {
                        currentTeam.teamId = teamId;
                        prefs.edit().putString("teamId", teamId).apply();

                        if (listener != null) {
                            listener.onTeamUpdated(currentTeam);
                        }
                    }
                } else {
                    currentTeam = null;
                    prefs.edit().remove("teamId").apply();
                    if (listener != null) {
                        listener.onTeamLeft();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading team", error.toException());
            }
        });
    }

    public void createTeam(String name, String description, boolean isOpen, CreateTeamCallback callback) {
        if (currentUserId == null) {
            callback.onError("Please login first");
            return;
        }

        if (currentTeam != null) {
            callback.onError("Leave current team first");
            return;
        }

        dbRef.child("users").child(currentUserId).child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String username = "User";
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            username = snapshot.getValue(String.class);
                        }

                        String teamId = dbRef.child("teams").push().getKey();
                        if (teamId == null) {
                            callback.onError("Failed to create team");
                            return;
                        }

                        Team newTeam = new Team();
                        newTeam.teamId = teamId;
                        newTeam.name = name;
                        newTeam.description = description;
                        newTeam.captainId = currentUserId;
                        newTeam.captainName = username;
                        newTeam.createdTime = System.currentTimeMillis();
                        newTeam.memberCount = 1;
                        newTeam.activeMembers = 1;
                        newTeam.isOpen = isOpen;

                        String finalUsername = username;
                        dbRef.child("teams").child(teamId).setValue(newTeam)
                                .addOnSuccessListener(aVoid -> {
                                    TeamMember member = new TeamMember();
                                    member.odamUserId = currentUserId;
                                    member.username = finalUsername;
                                    member.joinedTime = System.currentTimeMillis();
                                    member.role = "captain";

                                    dbRef.child("teams").child(teamId).child("members")
                                            .child(currentUserId).setValue(member);

                                    dbRef.child("users").child(currentUserId)
                                            .child("teamId").setValue(teamId);

                                    currentTeam = newTeam;
                                    callback.onSuccess(teamId);
                                })
                                .addOnFailureListener(e -> callback.onError(e.getMessage()));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void joinTeam(String teamId, JoinTeamCallback callback) {
        if (currentUserId == null) {
            callback.onError("Please login first");
            return;
        }

        if (currentTeam != null) {
            callback.onError("Leave current team first");
            return;
        }

        dbRef.child("teams").child(teamId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onError("Team not found");
                    return;
                }

                Team team = snapshot.getValue(Team.class);
                if (team == null) {
                    callback.onError("Team data error");
                    return;
                }

                if (!team.isOpen) {
                    callback.onError("Team is invite-only");
                    return;
                }

                if (team.memberCount >= MAX_TEAM_SIZE) {
                    callback.onError("Team is full");
                    return;
                }

                addUserToTeam(teamId, team.memberCount, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    private void addUserToTeam(String teamId, int currentMemberCount, JoinTeamCallback callback) {
        dbRef.child("users").child(currentUserId).child("username")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnap) {
                        String username = "User";
                        if (userSnap.exists() && userSnap.getValue() != null) {
                            username = userSnap.getValue(String.class);
                        }

                        TeamMember member = new TeamMember();
                        member.odamUserId = currentUserId;
                        member.username = username;
                        member.joinedTime = System.currentTimeMillis();
                        member.role = "member";

                        dbRef.child("teams").child(teamId).child("members")
                                .child(currentUserId).setValue(member);

                        dbRef.child("teams").child(teamId).child("memberCount")
                                .setValue(currentMemberCount + 1);

                        dbRef.child("users").child(currentUserId)
                                .child("teamId").setValue(teamId);

                        callback.onSuccess();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void leaveTeam(LeaveTeamCallback callback) {
        if (currentUserId == null || currentTeam == null) {
            callback.onError("Not in a team");
            return;
        }

        String teamId = currentTeam.teamId;
        boolean isCaptain = currentUserId.equals(currentTeam.captainId);

        dbRef.child("teams").child(teamId).child("members")
                .child(currentUserId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    dbRef.child("teams").child(teamId).child("memberCount")
                            .setValue(Math.max(0, currentTeam.memberCount - 1));

                    dbRef.child("users").child(currentUserId)
                            .child("teamId").removeValue();

                    if (isCaptain && currentTeam.memberCount <= 1) {
                        dbRef.child("teams").child(teamId).removeValue();
                    }

                    currentTeam = null;
                    prefs.edit().remove("teamId").apply();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getLeaderboard(int limit, LeaderboardCallback callback) {
        dbRef.child("teams").orderByChild("weeklyMined").limitToLast(limit)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Team> teams = new ArrayList<>();
                        for (DataSnapshot teamSnap : snapshot.getChildren()) {
                            Team team = teamSnap.getValue(Team.class);
                            if (team != null) {
                                team.teamId = teamSnap.getKey();
                                teams.add(team);
                            }
                        }

                        Collections.sort(teams, (a, b) -> Double.compare(b.weeklyMined, a.weeklyMined));

                        for (int i = 0; i < teams.size(); i++) {
                            teams.get(i).weeklyRank = i + 1;
                        }

                        callback.onSuccess(teams);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void contributeToTeam(double amount) {
        if (currentUserId == null || currentTeam == null) return;

        String teamId = currentTeam.teamId;

        // Simple setValue instead of transaction for reliability
        dbRef.child("teams").child(teamId).child("members")
                .child(currentUserId).child("contributedAmount")
                .get().addOnSuccessListener(snapshot -> {
                    double current = 0;
                    if (snapshot.exists() && snapshot.getValue() != null) {
                        current = ((Number) snapshot.getValue()).doubleValue();
                    }
                    dbRef.child("teams").child(teamId).child("members")
                            .child(currentUserId).child("contributedAmount")
                            .setValue(current + amount);
                });

        // Update team weekly total
        dbRef.child("teams").child(teamId).child("weeklyMined")
                .get().addOnSuccessListener(snapshot -> {
                    double current = 0;
                    if (snapshot.exists() && snapshot.getValue() != null) {
                        current = ((Number) snapshot.getValue()).doubleValue();
                    }
                    dbRef.child("teams").child(teamId).child("weeklyMined")
                            .setValue(current + amount);
                });
    }

    public Team getCurrentTeam() {
        return currentTeam;
    }

    public boolean hasTeam() {
        return currentTeam != null;
    }

    public float getTeamBoost() {
        return currentTeam != null ? currentTeam.getBoostMultiplier() : 1.0f;
    }

    public interface CreateTeamCallback {
        void onSuccess(String teamId);
        void onError(String message);
    }

    public interface JoinTeamCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface LeaveTeamCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface LeaderboardCallback {
        void onSuccess(List<Team> teams);
        void onError(String message);
    }
}

