package network.lynx.app;


import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import network.lynx.app.R;
import network.lynx.app.ReferralAdapter;
import network.lynx.app.ReferralInfo;

public class ReferralsTabFragment extends Fragment {

    private TextView totalReferralsCount, activeUsersCount, totalEarningsCount, emptyReferralsText;
    private Button inviteFriendsButton;
    private RecyclerView referralsRecyclerView;
    private ReferralAdapter referralAdapter;
    private List<ReferralInfo> referralList = new ArrayList<>();
    private String referralCode;

    // Track listeners for cleanup
    private ValueEventListener referralsListener;
    private DatabaseReference userRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_referrals_tab, container, false);

        totalReferralsCount = view.findViewById(R.id.totalReferralsCount);
        activeUsersCount = view.findViewById(R.id.activeUsersCount);
        totalEarningsCount = view.findViewById(R.id.totalEarningsCount);
        emptyReferralsText = view.findViewById(R.id.emptyReferralsText);
        inviteFriendsButton = view.findViewById(R.id.inviteFriendsButton);
        referralsRecyclerView = view.findViewById(R.id.referralsRecyclerView);

        setupRecyclerView();
        loadReferralData();

        inviteFriendsButton.setOnClickListener(v -> ReferralUtils.shareReferral(getContext()));

        return view;
    }

    private void setupRecyclerView() {
        referralAdapter = new ReferralAdapter(referralList);
        referralsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        referralsRecyclerView.setAdapter(referralAdapter);
    }

    private void loadReferralData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // OPTIMIZATION: Check cached referral code first to avoid Firebase read
        String cachedCode = ReferralUtils.getCachedReferralCode(getContext(), userId);
        if (cachedCode != null && !cachedCode.isEmpty() && !cachedCode.equals("XXXXXX")) {
            referralCode = cachedCode;
        } else {
            // Get referral code from Firebase if not cached - use single value event
            userRef.child("referralCode").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    referralCode = snapshot.getValue(String.class);

                    // If referral code is null or placeholder, generate a new one
                    if (referralCode == null || referralCode.isEmpty() || referralCode.equals("XXXXXX")) {
                        referralCode = generateReferralCode(userId);
                        // Save to Firebase
                        userRef.child("referralCode").setValue(referralCode);
                    }

                    // Save to centralized SharedPreferences cache for app-wide reuse
                    ReferralUtils.saveProfileToPrefs(getContext(), userId, null, null, referralCode);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error - try to generate code if possible
                    if (referralCode == null || referralCode.isEmpty()) {
                        referralCode = generateReferralCode(userId);
                        ReferralUtils.saveProfileToPrefs(getContext(), userId, null, null, referralCode);
                    }
                }
            });
        }

        // Remove existing listener to prevent duplicates
        if (referralsListener != null) {
            userRef.child("referrals").removeEventListener(referralsListener);
        }

        // OPTIMIZATION: Use single value event listener instead of continuous listener
        referralsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                referralList.clear();
                int totalReferrals = 0;
                int activeUsers = 0;
                double totalEarnings = 0.0;

                for (DataSnapshot referralSnapshot : snapshot.getChildren()) {
                    String referralId = referralSnapshot.getKey();

                    // Check if using old structure (refer_UserId)
                    if (referralSnapshot.child("refer_UserId").exists()) {
                        String referUserId = referralSnapshot.child("refer_UserId").getValue(String.class);
                        String referUsername = referralSnapshot.child("refer_username").getValue(String.class);

                        if (referUserId != null) {
                            // Create ReferralInfo object
                            ReferralInfo referralInfo = new ReferralInfo();
                            referralInfo.setUserId(referUserId);
                            referralInfo.setUsername(referUsername != null ? referUsername : "Unknown User");
                            referralInfo.setJoinDate(System.currentTimeMillis());
                            referralInfo.setActive(false);
                            referralInfo.setTotalCommission(0.0);

                            // Add to list
                            referralList.add(referralInfo);
                            totalReferrals++;

                            // OPTIMIZATION: Only migrate if not already in new structure
                            // This reduces unnecessary Firebase writes
                            migrateReferralDataOnce(userId, referralId, referralInfo);
                        }
                    }
                    // Using new structure
                    else {
                        ReferralInfo referralInfo = new ReferralInfo();

                        // Get userId
                        String referUserId = referralSnapshot.child("userId").getValue(String.class);
                        if (referUserId == null) continue;
                        referralInfo.setUserId(referUserId);

                        // Get username
                        String username = referralSnapshot.child("username").getValue(String.class);
                        referralInfo.setUsername(username != null ? username : "Unknown User");

                        // Get join date
                        Long joinDate = referralSnapshot.child("joinDate").getValue(Long.class);
                        referralInfo.setJoinDate(joinDate != null ? joinDate : System.currentTimeMillis());

                        // Get active status
                        Boolean isActive = referralSnapshot.child("isActive").getValue(Boolean.class);
                        referralInfo.setActive(isActive != null && isActive);

                        // Get commission
                        Double commission = referralSnapshot.child("totalCommission").getValue(Double.class);
                        referralInfo.setTotalCommission(commission != null ? commission : 0.0);

                        // Add to list
                        referralList.add(referralInfo);
                        totalReferrals++;

                        if (referralInfo.isActive()) {
                            activeUsers++;
                        }

                        totalEarnings += referralInfo.getTotalCommission();
                    }
                }

                // Update UI
                totalReferralsCount.setText(String.valueOf(totalReferrals));
                activeUsersCount.setText(String.valueOf(activeUsers));
                totalEarningsCount.setText(String.format("%.2f", totalEarnings));

                referralAdapter.notifyDataSetChanged();

                // Show empty state if no referrals
                if (referralList.isEmpty()) {
                    emptyReferralsText.setVisibility(View.VISIBLE);
                    referralsRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyReferralsText.setVisibility(View.GONE);
                    referralsRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        };

        userRef.child("referrals").addListenerForSingleValueEvent(referralsListener);
    }

    // OPTIMIZATION: Only migrate once, not on every load
    private void migrateReferralDataOnce(String userId, String referralId, ReferralInfo referralInfo) {
        DatabaseReference refRef = FirebaseDatabase.getInstance().getReference("users").child(userId).child("referrals").child(referralId);

        Map<String, Object> updatedReferral = new HashMap<>();
        updatedReferral.put("userId", referralInfo.getUserId());
        updatedReferral.put("username", referralInfo.getUsername());
        updatedReferral.put("joinDate", referralInfo.getJoinDate());
        updatedReferral.put("isActive", referralInfo.isActive());
        updatedReferral.put("totalCommission", referralInfo.getTotalCommission());
        // Remove old fields
        updatedReferral.put("refer_UserId", null);
        updatedReferral.put("refer_username", null);

        refRef.updateChildren(updatedReferral);
    }

    private void shareAppInvite() {
        // Keep for backwards compatibility but delegate to ReferralUtils
        ReferralUtils.shareReferral(getContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // OPTIMIZATION: Clean up Firebase listeners
        if (referralsListener != null && userRef != null) {
            userRef.child("referrals").removeEventListener(referralsListener);
            referralsListener = null;
        }
    }

    /**
     * Generate a unique referral code from user ID
     */
    private String generateReferralCode(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "LYNX" + System.currentTimeMillis() % 10000;
        }
        try {
            String encoded = android.util.Base64.encodeToString(uid.getBytes(), android.util.Base64.NO_WRAP);
            String code = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (code.length() >= 6) {
                return code.substring(0, 6);
            } else {
                return code + uid.hashCode() % 1000;
            }
        } catch (Exception e) {
            return "LYX" + Math.abs(uid.hashCode() % 100000);
        }
    }
}