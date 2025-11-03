package network.lynx.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReferralFragment extends Fragment {
    private static final String TAG = "ReferralFragment";

    private TextView totalEarned, friendsAdded, referralCode, totalCoinsDisplay;
    private ImageView backButton, copyButton;
    private CardView inviteButton, boostCard, boostButton;
    private DatabaseReference userRef;
    private String userId;
    private String currentReferralCode;
    private double totalCommissionEarned = 0.0;
    private double totalCoins = 0.0;
    private boolean isBoostActive = false;
    private boolean hasUpdatedCoinsThisSession = false; // BUG FIX: Prevent double coin counting
    private ValueEventListener referralDataListener; // BUG FIX: Track listener for cleanup

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_referral, container, false);

        try {
            initializeViews(view);
            setupClickListeners();
            loadReferralData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreateView", e);
            if (getContext() != null) {
                ToastUtils.showError(getContext(), "Failed to load referral data");
            }
        }

        return view;
    }

    private void initializeViews(View view) {
        totalEarned = view.findViewById(R.id.totalEarned);
        friendsAdded = view.findViewById(R.id.friendsAdded);
        referralCode = view.findViewById(R.id.referralCode);
        backButton = view.findViewById(R.id.backButton);
        copyButton = view.findViewById(R.id.copyButton);
        inviteButton = view.findViewById(R.id.inviteButton);
        boostButton = view.findViewById(R.id.BoostButton);

        // NEW: Total coins display (add this to your layout if not present)
//        totalCoinsDisplay = view.findViewById(R.id.totalCoinsDisplay);

        // Initialize Firebase with error handling
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
                Log.d(TAG, "Firebase initialized successfully");
            } else {
                Log.w(TAG, "User not authenticated");
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Please log in to view referral data");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        if (copyButton != null) {
            copyButton.setOnClickListener(v -> copyReferralCode());
        }

        if (inviteButton != null) {
            inviteButton.setOnClickListener(v -> shareInvite());
        }

        if (boostButton != null) {
            boostButton.setOnClickListener(v -> openBoostActivity());
        }
    }

    // NEW: Safer method to open BoostActivity
    private void openBoostActivity() {
        try {
            // Check if user is authenticated
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Please log in to access boost features");
                }
                return;
            }

            // Check if context is available
            if (getContext() == null) {
                Log.e(TAG, "Context is null, cannot start BoostActivity");
                return;
            }

            Log.d(TAG, "Opening BoostActivity...");
            Intent intent = new Intent(getContext(), BoostActivity.class);

            // Add extra data to help with debugging
            intent.putExtra("source", "ReferralFragment");
            intent.putExtra("userId", userId);

            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error opening BoostActivity", e);
            if (getContext() != null) {
                ToastUtils.showError(getContext(), "Failed to open boost activity. Please try again.");
            }
        }
    }

    private void loadReferralData() {
        if (userRef == null) {
            Log.w(TAG, "userRef is null, cannot load referral data");
            return;
        }

        // BUG FIX: Remove old listener to prevent duplicate updates
        if (referralDataListener != null) {
            userRef.removeEventListener(referralDataListener);
        }

        referralDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    Log.w(TAG, "Fragment not attached, skipping data update");
                    return;
                }

                try {
                    if (snapshot.exists()) {
                        // Get referral code
                        currentReferralCode = snapshot.child("referralCode").getValue(String.class);
                        if (currentReferralCode != null && referralCode != null) {
                            referralCode.setText(currentReferralCode);
                        }

                        // NEW: Get total coins from user account
                        Double userTotalCoins = snapshot.child("totalcoins").getValue(Double.class);
                        if (userTotalCoins != null) {
                            totalCoins = userTotalCoins;
                        }

                        // Calculate total earned from commissions (20% of referred users' mining)
                        totalCommissionEarned = 0.0;
                        if (snapshot.child("commissions").exists()) {
                            for (DataSnapshot commission : snapshot.child("commissions").getChildren()) {
                                Double amount = commission.child("amount").getValue(Double.class);
                                if (amount != null) {
                                    totalCommissionEarned += amount;
                                }
                            }
                        }

                        // NEW: Calculate referral income from mining commissions
                        double referralMiningIncome = 0.0;
                        if (snapshot.child("referralEarnings").exists()) {
                            for (DataSnapshot earning : snapshot.child("referralEarnings").getChildren()) {
                                Double amount = earning.child("amount").getValue(Double.class);
                                if (amount != null) {
                                    referralMiningIncome += amount;
                                }
                            }
                        }

                        // Total referral income = commissions + mining income
                        double totalReferralIncome = totalCommissionEarned + referralMiningIncome;

                        // Check if boost is active and apply multiplier
                        checkBoostStatus(snapshot);

                        // Apply boost multiplier if active
                        double displayEarned = isBoostActive ? totalReferralIncome * 2.0 : totalReferralIncome;
                        if (totalEarned != null) {
                            totalEarned.setText(String.format("%.4f LYX", displayEarned));
                        }

                        // NEW: Display total coins including referral income
//                        if (totalCoinsDisplay != null) {
//                            double totalWithReferrals = totalCoins + totalReferralIncome;
//                            totalEarned.setText(String.format("Total: %.4f LYX", totalWithReferrals));
//                        }

                        // Count active referrals
                        int totalReferrals = 0;
                        if (snapshot.child("referrals").exists()) {
                            totalReferrals = (int) snapshot.child("referrals").getChildrenCount();
                        }
                        if (friendsAdded != null) {
                            friendsAdded.setText(String.valueOf(totalReferrals));
                        }

                        // NEW: Update user's total coins to include referral income
                        updateUserTotalCoins(totalReferralIncome);

                        Log.d(TAG, "Referral data loaded successfully - Total referral income: " + totalReferralIncome);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing referral data", e);
                    if (getContext() != null) {
                        ToastUtils.showError(getContext(), "Error loading referral data");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error loading referral data", error.toException());
                if (getContext() != null && isAdded()) {
                    ToastUtils.showError(getContext(), "Failed to load referral data");
                }
            }
        });
    }

    // NEW: Method to update user's total coins with referral income
    private void updateUserTotalCoins(double referralIncome) {
        if (userRef != null && referralIncome > 0) {
            // Only update if there's actual referral income
            double newTotalCoins = totalCoins + referralIncome;

            // Update Firebase with new total (this should be done carefully to avoid double-counting)
            userRef.child("totalcoins").setValue(newTotalCoins)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Total coins updated with referral income: " + newTotalCoins);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update total coins", e);
                    });
        }
    }

    private void checkBoostStatus(DataSnapshot snapshot) {
        try {
            // Check if referral boost is active
            if (snapshot.child("activeBoosts").child("referralBoost").exists()) {
                Long boostEndTime = snapshot.child("activeBoosts").child("referralBoost").child("endTime").getValue(Long.class);
                if (boostEndTime != null && boostEndTime > System.currentTimeMillis()) {
                    isBoostActive = true;
                    showBoostActiveIndicator();
                } else {
                    isBoostActive = false;
                    hideBoostActiveIndicator();
                }
            } else {
                isBoostActive = false;
                hideBoostActiveIndicator();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking boost status", e);
            isBoostActive = false;
        }
    }

    private void showBoostActiveIndicator() {
        // Show boost active indicator (you can add a visual indicator here)
        if (getContext() != null && isAdded()) {
            ToastUtils.showInfo(getContext(), "🚀 Referral boost is active! (2x rewards)");
        }
    }

    private void hideBoostActiveIndicator() {
        // Hide boost indicator
    }

    private void copyReferralCode() {
        if (currentReferralCode != null && getContext() != null) {
            try {
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Referral Code", currentReferralCode);
                    clipboard.setPrimaryClip(clip);
                    ToastUtils.showInfo(getContext(), "Referral code copied!");

                    // Add visual feedback
                    if (copyButton != null) {
                        copyButton.animate()
                                .scaleX(1.2f)
                                .scaleY(1.2f)
                                .setDuration(100)
                                .withEndAction(() ->
                                        copyButton.animate()
                                                .scaleX(1.0f)
                                                .scaleY(1.0f)
                                                .setDuration(100)
                                                .start())
                                .start();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error copying referral code", e);
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Failed to copy referral code");
                }
            }
        }
    }

    private void shareInvite() {
        if (currentReferralCode != null && getContext() != null) {
            try {
                String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + currentReferralCode;
                String boostMessage = isBoostActive ? "\n🚀 I'm currently on a 2x boost - join now for maximum rewards!" : "";

                // NEW: Enhanced sharing message with earnings info
                String earningsInfo = totalCommissionEarned > 0 ?
                        String.format("\n💰 I've already earned %.2f LYX from referrals!", totalCommissionEarned) : "";

                String message = "🚀 Join Lynx Network and start earning!\n\n" +
                        "Use my referral code: " + currentReferralCode + "\n" +
                        "Download here: " + inviteLink + "\n\n" +
                        "We both get mining speed boosts! 💎⚡" +
                        earningsInfo + boostMessage +
                        "\n\n🎯 You get 20% of my mining rewards forever!";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join Lynx Network!");
                Intent chooser = Intent.createChooser(shareIntent, "Share via");
                startActivity(chooser);
            } catch (Exception e) {
                Log.e(TAG, "Error sharing invite", e);
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Failed to share invite");
                }
            }
        }
    }

    // Method to activate boost (can be called from boost button)
    public void activateReferralBoost() {
        if (userRef != null) {
            try {
                long boostEndTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
                userRef.child("activeBoosts").child("referralBoost").child("endTime").setValue(boostEndTime)
                        .addOnSuccessListener(aVoid -> {
                            if (getContext() != null && isAdded()) {
                                Toast.makeText(getContext(), "🚀 Referral boost activated for 24 hours!", Toast.LENGTH_LONG).show();
                            }
                            isBoostActive = true;
                            showBoostActiveIndicator();
                            // Refresh the earnings display
                            loadReferralData();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to activate boost", e);
                            if (getContext() != null && isAdded()) {
                                Toast.makeText(getContext(), "Failed to activate boost", Toast.LENGTH_SHORT).show();
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error activating referral boost", e);
            }
        }
    }

    // Method to check if user can activate boost
    public boolean canActivateBoost() {
        return !isBoostActive;
    }

    // Get current boost status
    public boolean isBoostActive() {
        return isBoostActive;
    }

    // Get total earned amount
    public double getTotalEarned() {
        return totalCommissionEarned;
    }

    // NEW: Get total coins including referral income
    public double getTotalCoinsWithReferrals() {
        return totalCoins + totalCommissionEarned;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when fragment becomes visible
        if (userRef != null) {
            loadReferralData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "ReferralFragment destroyed");
        // BUG FIX: Remove listener to prevent memory leaks
        if (userRef != null && referralDataListener != null) {
            userRef.removeEventListener(referralDataListener);
            Log.d(TAG, "Referral data listener removed");
        }
    }
}
