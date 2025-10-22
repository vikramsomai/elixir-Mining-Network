package network.lynx.app;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ReferralPingManager {
    private static final String TAG = "ReferralPingManager";

    public interface PingCallback {
        void onComplete(boolean success);
    }

    /**
     * Sends a ping notification to an inactive referral
     * @param referralUserId The user ID of the referral to ping
     * @param callback Callback to notify when ping is sent
     */
    public static void pingReferral(String referralUserId, PingCallback callback) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Get current user's info
        usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = "Your referrer";
                if (snapshot.child("username").exists()) {
                    username = snapshot.child("username").getValue(String.class);
                } else if (snapshot.child("name").exists()) {
                    username = snapshot.child("name").getValue(String.class);
                }

                final String senderName = username;

                // Create ping notification
                DatabaseReference notificationsRef = usersRef.child(referralUserId).child("notifications");
                String notificationId = notificationsRef.push().getKey();

                if (notificationId != null) {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("id", notificationId);
                    notification.put("type", "referral_ping");
                    notification.put("title", "Mining Reminder");
                    notification.put("message", senderName + " is reminding you to mine your daily LYX tokens!");
                    notification.put("senderId", currentUserId);
                    notification.put("senderName", senderName);
                    notification.put("timestamp", System.currentTimeMillis());
                    notification.put("read", false);

                    notificationsRef.child(notificationId).setValue(notification)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Ping sent successfully to: " + referralUserId);

                                // Also increment unread count if it exists
                                usersRef.child(referralUserId).child("unreadNotifications")
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                Integer count = snapshot.getValue(Integer.class);
                                                if (count == null) count = 0;
                                                usersRef.child(referralUserId).child("unreadNotifications")
                                                        .setValue(count + 1);
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError error) {
                                                Log.e(TAG, "Failed to update unread count", error.toException());
                                            }
                                        });

                                callback.onComplete(true);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to send ping", e);
                                callback.onComplete(false);
                            });
                } else {
                    callback.onComplete(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to get user info", error.toException());
                callback.onComplete(false);
            }
        });
    }
}