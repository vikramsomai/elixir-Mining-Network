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

        inviteFriendsButton.setOnClickListener(v -> shareAppInvite());

        return view;
    }

    private void setupRecyclerView() {
        referralAdapter = new ReferralAdapter(referralList);
        referralsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        referralsRecyclerView.setAdapter(referralAdapter);
    }

    private void loadReferralData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Get referral code
        userRef.child("referralCode").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                referralCode = snapshot.getValue(String.class);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });

        // Load referrals data
        userRef.child("referrals").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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

                            // Migrate to new structure
                            migrateReferralData(userId, referralId, referralInfo);
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
        });
    }

    private void migrateReferralData(String userId, String referralId, ReferralInfo referralInfo) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        Map<String, Object> updatedReferral = new HashMap<>();
        updatedReferral.put("userId", referralInfo.getUserId());
        updatedReferral.put("username", referralInfo.getUsername());
        updatedReferral.put("joinDate", referralInfo.getJoinDate());
        updatedReferral.put("isActive", referralInfo.isActive());
        updatedReferral.put("totalCommission", referralInfo.getTotalCommission());

        userRef.child("referrals").child(referralId).setValue(updatedReferral);
    }

    private void shareAppInvite() {
        if (referralCode == null || referralCode.isEmpty()) {
            return;
        }

        String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + referralCode;
        String message = "Join Lynx Network and earn rewards! Use my referral code: "
                + referralCode + "\nDownload here: " + inviteLink;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, message);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share via");
        startActivity(shareIntent);
    }
}