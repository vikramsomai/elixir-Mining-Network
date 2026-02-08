package network.lynx.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SECURITY CIRCLE MANAGER - Inspired by Pi Network
 *
 * Security Circle is one of the most engaging features of Pi Network.
 * Users add trusted members to their circle, increasing mining rate.
 *
 * HOW IT WORKS:
 * - Users can add up to 5 trusted members to their circle
 * - Each active member in circle = +10% mining boost
 * - Full circle (5 members) = +50% permanent boost
 * - Members must be active (mined in last 24h) to count
 * - Creates social dependency = daily engagement
 *
 * WHY IT WORKS:
 * - Social obligation keeps users coming back
 * - Users invite friends to fill their circle
 * - Daily check to see if circle members are active
 * - FOMO if circle members become inactive
 */
public class SecurityCircleManager {
    private static final String TAG = "SecurityCircleManager";
    private static final String PREFS_NAME = "security_circle";

    // Configuration
    public static final int MAX_CIRCLE_SIZE = 5;
    public static final float BOOST_PER_ACTIVE_MEMBER = 0.10f; // 10% per member
    public static final long ACTIVE_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24 hours

    private static SecurityCircleManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseReference dbRef;
    private String currentUserId;

    // Circle data
    private List<CircleMember> circleMembers = new ArrayList<>();
    private int activeCount = 0;
    private CircleUpdateListener listener;

    public interface CircleUpdateListener {
        void onCircleUpdated(int totalMembers, int activeMembers, float boostMultiplier);
    }

    public static class CircleMember {
        public String odamUserId;
        public String username;
        public long lastActiveTime;
        public boolean isActive;
        public long addedTime;

        public CircleMember() {}

        public CircleMember(String odamUserId, String username) {
            this.odamUserId = odamUserId;
            this.username = username;
            this.addedTime = System.currentTimeMillis();
        }
    }

    private SecurityCircleManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbRef = FirebaseDatabase.getInstance().getReference();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            loadCircle();
        }
    }

    public static synchronized SecurityCircleManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityCircleManager(context);
        }
        return instance;
    }

    public void setListener(CircleUpdateListener listener) {
        this.listener = listener;
    }

    /**
     * Load circle members from Firebase
     */
    public void loadCircle() {
        if (currentUserId == null) return;

        dbRef.child("securityCircle").child(currentUserId).child("members")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        circleMembers.clear();

                        for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                            CircleMember member = memberSnapshot.getValue(CircleMember.class);
                            if (member != null) {
                                circleMembers.add(member);
                            }
                        }

                        // Check which members are active
                        checkActiveMembersStatus();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Error loading circle", error.toException());
                    }
                });
    }

    /**
     * Check activity status of all circle members
     */
    private void checkActiveMembersStatus() {
        activeCount = 0;
        final int[] checkedCount = {0};

        if (circleMembers.isEmpty()) {
            notifyListener();
            return;
        }

        for (CircleMember member : circleMembers) {
            dbRef.child("users").child(member.odamUserId).child("lastMiningTime")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            long lastMining = 0;
                            if (snapshot.exists() && snapshot.getValue() != null) {
                                lastMining = ((Number) snapshot.getValue()).longValue();
                            }

                            member.lastActiveTime = lastMining;
                            member.isActive = (System.currentTimeMillis() - lastMining) < ACTIVE_THRESHOLD_MS;

                            if (member.isActive) {
                                activeCount++;
                            }

                            checkedCount[0]++;
                            if (checkedCount[0] == circleMembers.size()) {
                                notifyListener();
                                saveActiveCount();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            checkedCount[0]++;
                            if (checkedCount[0] == circleMembers.size()) {
                                notifyListener();
                            }
                        }
                    });
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onCircleUpdated(circleMembers.size(), activeCount, getBoostMultiplier());
        }
    }

    private void saveActiveCount() {
        prefs.edit()
                .putInt("activeCount", activeCount)
                .putLong("lastCheck", System.currentTimeMillis())
                .apply();
    }

    /**
     * Add a member to security circle
     */
    public void addMember(String memberUserId, String username, AddMemberCallback callback) {
        if (currentUserId == null) {
            callback.onError("Please login first");
            return;
        }

        if (circleMembers.size() >= MAX_CIRCLE_SIZE) {
            callback.onError("Circle is full (max 5 members)");
            return;
        }

        if (memberUserId.equals(currentUserId)) {
            callback.onError("Cannot add yourself");
            return;
        }

        // Check if already in circle
        for (CircleMember member : circleMembers) {
            if (member.odamUserId.equals(memberUserId)) {
                callback.onError("Already in your circle");
                return;
            }
        }

        // Verify user exists
        dbRef.child("users").child(memberUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onError("User not found");
                    return;
                }

                // Add to circle
                CircleMember newMember = new CircleMember(memberUserId, username);

                dbRef.child("securityCircle").child(currentUserId)
                        .child("members").child(memberUserId)
                        .setValue(newMember)
                        .addOnSuccessListener(aVoid -> {
                            // Also add reverse connection (you're in their "trusted by" list)
                            Map<String, Object> trustedBy = new HashMap<>();
                            trustedBy.put("userId", currentUserId);
                            trustedBy.put("addedTime", System.currentTimeMillis());

                            dbRef.child("securityCircle").child(memberUserId)
                                    .child("trustedBy").child(currentUserId)
                                    .setValue(trustedBy);

                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> callback.onError(e.getMessage()));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Remove member from circle
     */
    public void removeMember(String memberUserId, RemoveMemberCallback callback) {
        if (currentUserId == null) {
            callback.onError("Please login first");
            return;
        }

        dbRef.child("securityCircle").child(currentUserId)
                .child("members").child(memberUserId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Also remove from their "trusted by" list
                    dbRef.child("securityCircle").child(memberUserId)
                            .child("trustedBy").child(currentUserId)
                            .removeValue();
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Get current boost multiplier from active circle members
     */
    public float getBoostMultiplier() {
        return 1.0f + (activeCount * BOOST_PER_ACTIVE_MEMBER);
    }

    /**
     * Get cached boost (for when offline)
     */
    public float getCachedBoost() {
        int cached = prefs.getInt("activeCount", 0);
        return 1.0f + (cached * BOOST_PER_ACTIVE_MEMBER);
    }

    public List<CircleMember> getCircleMembers() {
        return circleMembers;
    }

    public int getCircleSize() {
        return circleMembers.size();
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getRemainingSlots() {
        return MAX_CIRCLE_SIZE - circleMembers.size();
    }

    public interface AddMemberCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface RemoveMemberCallback {
        void onSuccess();
        void onError(String message);
    }
}

