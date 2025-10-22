package network.lynx.app;

import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class ReferralCommissionManager {

    private static final double COMMISSION_RATE = 0.10; // 10% commission
    private static final String TAG = "ReferralCommission";

    public static void distributeMiningCommission(String minerId, double minedAmount) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(minerId);

        userRef.child("referredBy").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String referrerId = snapshot.getValue(String.class);
                    if (referrerId != null && !referrerId.isEmpty()) {
                        double commissionAmount = minedAmount * COMMISSION_RATE;

                        // Add commission to referrer
                        DatabaseReference referrerRef = FirebaseDatabase.getInstance()
                                .getReference("users").child(referrerId);

                        // Update referrer's total coins
                        referrerRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot referrerSnapshot) {
                                Double currentCoins = referrerSnapshot.getValue(Double.class);
                                if (currentCoins == null) currentCoins = 0.0;

                                double newTotal = currentCoins + commissionAmount;
                                referrerRef.child("totalcoins").setValue(newTotal);

                                // Record commission transaction
                                String commissionId = referrerRef.child("commissions").push().getKey();
                                if (commissionId != null) {
                                    Map<String, Object> commissionData = new HashMap<>();
                                    commissionData.put("amount", commissionAmount);
                                    commissionData.put("fromUser", minerId);
                                    commissionData.put("timestamp", System.currentTimeMillis());
                                    commissionData.put("type", "mining_commission");

                                    referrerRef.child("commissions").child(commissionId).setValue(commissionData);
                                }

                                Log.d(TAG, "Commission distributed: " + commissionAmount + " to " + referrerId);
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "Failed to update referrer coins", error.toException());
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to get referrer info", error.toException());
            }
        });
    }

    public static void processReferralSignup(String newUserId, String referralCode) {
        if (referralCode == null || referralCode.isEmpty()) return;

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Find referrer by referral code
        usersRef.orderByChild("referralCode").equalTo(referralCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String referrerId = userSnapshot.getKey();
                            if (referrerId != null && !referrerId.equals(newUserId)) {

                                // Set referrer for new user
                                usersRef.child(newUserId).child("referredBy").setValue(referrerId);

                                // Add to referrer's referral list
                                Map<String, Object> referralData = new HashMap<>();
                                referralData.put("userId", newUserId);
                                referralData.put("timestamp", System.currentTimeMillis());
                                referralData.put("status", "active");

                                usersRef.child(referrerId).child("referrals").child(newUserId).setValue(referralData);

                                // Give signup bonus to both users
                                giveSignupBonus(referrerId, newUserId);

                                Log.d(TAG, "Referral processed: " + newUserId + " referred by " + referrerId);
                                break;
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "Failed to process referral", error.toException());
                    }
                });
    }

    private static void giveSignupBonus(String referrerId, String newUserId) {
        double signupBonus = 0.1; // Bonus amount

        // Give bonus to referrer
        DatabaseReference referrerRef = FirebaseDatabase.getInstance().getReference("users").child(referrerId);
        referrerRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double currentCoins = snapshot.getValue(Double.class);
                if (currentCoins == null) currentCoins = 0.0;

                referrerRef.child("totalcoins").setValue(currentCoins + signupBonus);

                // Record bonus transaction
                String bonusId = referrerRef.child("commissions").push().getKey();
                if (bonusId != null) {
                    Map<String, Object> bonusData = new HashMap<>();
                    bonusData.put("amount", signupBonus);
                    bonusData.put("fromUser", newUserId);
                    bonusData.put("timestamp", System.currentTimeMillis());
                    bonusData.put("type", "referral_signup_bonus");

                    referrerRef.child("commissions").child(bonusId).setValue(bonusData);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to give referrer bonus", error.toException());
            }
        });

        // Give bonus to new user
        DatabaseReference newUserRef = FirebaseDatabase.getInstance().getReference("users").child(newUserId);
        newUserRef.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double currentCoins = snapshot.getValue(Double.class);
                if (currentCoins == null) currentCoins = 0.0;

                newUserRef.child("totalcoins").setValue(currentCoins + signupBonus);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Failed to give new user bonus", error.toException());
            }
        });
    }
}