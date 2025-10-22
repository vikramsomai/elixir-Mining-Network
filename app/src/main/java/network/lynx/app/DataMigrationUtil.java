package network.lynx.app;


import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class DataMigrationUtil {
    private static final String TAG = "DataMigrationUtil";

    /**
     * Migrates the old referral structure to the new structure
     */
    public static void migrateReferralData() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (userId == null) continue;

                    // Process referrals
                    processUserReferrals(userId, userSnapshot);

                    // Initialize commissions if needed
                    if (!userSnapshot.child("commissions").exists()) {
                        usersRef.child(userId).child("commissions").setValue(new HashMap<>());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to migrate data", error.toException());
            }
        });
    }

    private static void processUserReferrals(String userId, DataSnapshot userSnapshot) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        DataSnapshot referralsSnapshot = userSnapshot.child("referrals");

        if (referralsSnapshot.exists()) {
            for (DataSnapshot referralSnapshot : referralsSnapshot.getChildren()) {
                String referralId = referralSnapshot.getKey();
                if (referralId == null) continue;

                // Check if this is the old structure (has refer_UserId)
                if (referralSnapshot.child("refer_UserId").exists()) {
                    String referUserId = referralSnapshot.child("refer_UserId").getValue(String.class);
                    String referUsername = referralSnapshot.child("refer_username").getValue(String.class);

                    if (referUserId != null) {
                        // Create updated structure
                        Map<String, Object> updatedReferral = new HashMap<>();
                        updatedReferral.put("userId", referUserId);
                        updatedReferral.put("username", referUsername != null ? referUsername : "Unknown User");
                        updatedReferral.put("joinDate", System.currentTimeMillis());
                        updatedReferral.put("isActive", false);
                        updatedReferral.put("totalCommission", 0.0);

                        // Update the structure
                        userRef.child("referrals").child(referralId).setValue(updatedReferral);

                        // Update the user's active status
                        updateUserActiveStatus(referUserId);
                    }
                }
                // If totalCommission doesn't exist, add it
                else if (!referralSnapshot.child("totalCommission").exists()) {
                    userRef.child("referrals").child(referralId).child("totalCommission").setValue(0.0);
                }
            }
        }
    }

    private static void updateUserActiveStatus(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean isActive = snapshot.child("isActive").getValue(Boolean.class);
                Long lastActive = snapshot.child("lastActive").getValue(Long.class);

                // If isActive doesn't exist but lastActive does, calculate isActive
                if (isActive == null && lastActive != null) {
                    long currentTime = System.currentTimeMillis();
                    boolean active = (currentTime - lastActive) <= (3 * 24 * 60 * 60 * 1000); // 3 days
                    userRef.child("isActive").setValue(active);
                }
                // If neither exists, set defaults
                else if (isActive == null) {
                    userRef.child("isActive").setValue(false);
                    userRef.child("lastActive").setValue(System.currentTimeMillis());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to update user active status", error.toException());
            }
        });
    }
}