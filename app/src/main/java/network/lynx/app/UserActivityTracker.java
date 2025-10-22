package network.lynx.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class UserActivityTracker {
    private static final String TAG = "UserActivityTracker";
    private final Context context;

    public UserActivityTracker(Context context) {
        this.context = context;

        // Update user's active status when app starts
        updateUserActiveStatus();
    }

    /**
     * Updates the user's active status and lastActive timestamp
     */
    public void updateUserActiveStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userId = user.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Update lastActive timestamp
        userRef.child("lastActive").setValue(System.currentTimeMillis());

        // Set isActive to true
        userRef.child("isActive").setValue(true);

        Log.d(TAG, "Updated user active status for: " + userId);

        // Also update referrals' active status
//        ReferralCommissionManager.updateReferralsActiveStatus(userId);
    }

    /**
     * Static method to check and update activity status for all users
     * This is called by the ActivityCheckWorker
     */
    public static void checkAllUsersActivity() {
        Log.d(TAG, "Checking activity for all users");

        // Get current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String currentUserId = currentUser.getUid();

        // Update current user's active status
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId);
        userRef.child("lastActive").setValue(System.currentTimeMillis());
        userRef.child("isActive").setValue(true);

        // Check all users for inactivity
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long currentTime = System.currentTimeMillis();
                long inactiveThreshold = 3 * 24 * 60 * 60 * 1000; // 3 days

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String checkUserId = userSnapshot.getKey();
                    if (checkUserId == null) continue;

                    // Skip current user, already updated
                    if (checkUserId.equals(currentUserId)) continue;

                    // Check lastActive timestamp
                    if (userSnapshot.child("lastActive").exists()) {
                        Long lastActive = userSnapshot.child("lastActive").getValue(Long.class);
                        if (lastActive != null) {
                            boolean isActive = (currentTime - lastActive) <= inactiveThreshold;
                            usersRef.child(checkUserId).child("isActive").setValue(isActive);
                        }
                    }
                }

                // Update referrals' active status for current user
//                ReferralCommissionManager.updateReferralsActiveStatus(currentUserId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check user activity", error.toException());
            }
        });
    }
}