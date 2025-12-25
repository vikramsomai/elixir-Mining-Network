package network.lynx.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Locale;

public class ReferralFragment extends Fragment {
    private static final String TAG = "ReferralFragment";

    private TextView totalEarned, friendsAdded, referralCode;
    private ImageView backButton, copyButton;
    private MaterialButton inviteButton;
    private DatabaseReference databaseReference;
    private DatabaseReference userRef;
    private String userId;
    private String currentReferralCode;
    private double totalCommissionEarned = 0.0;
    private double referralMiningIncome = 0.0;
    private boolean isBoostActive = false;
    private SharedPreferences sharedPreferences;

    private ValueEventListener referralListener;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_referral, container, false);

        try {
            initializeViews(view);
            setupClickListeners();
            checkAndCreateUserIfNeeded(); // Check user exists before loading data
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreateView", e);
            // Try to still load data even if initialization had issues
            if (userId != null && userRef == null) {
                userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            }
            if (userRef != null) {
                checkAndCreateUserIfNeeded();
            }
            // Only show error if we truly can't proceed
            if (userId == null && getContext() != null) {
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
        // The layout uses a MaterialButton for invite; use MaterialButton to avoid ClassCastException
        inviteButton = view.findViewById(R.id.inviteButton);
        // boostButton = view.findViewById(R.id.BoostButton);
        sharedPreferences = requireContext().getSharedPreferences("userData", Context.MODE_PRIVATE);

        // Initialize UI with default values
        if (totalEarned != null) {
            totalEarned.setText("0.0000 LYX");
        }
        if (friendsAdded != null) {
            friendsAdded.setText("0");
        }
        try {
            // Initialize exactly like ProfileEditActivity does
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = auth.getCurrentUser();

            // Get user ID from shared preferences (like ProfileEditActivity)
            userId = sharedPreferences.getString("userid", null);
            // Fallback to FirebaseAuth if missing
            if ((userId == null || userId.isEmpty()) && currentUser != null) {
                userId = currentUser.getUid();
                sharedPreferences.edit().putString("userid", userId).apply();
            }

            // CRITICAL: Load cached referral code IMMEDIATELY to prevent "unknown" display
            if (userId != null && !userId.isEmpty()) {
                String cachedCode = ReferralUtils.getCachedReferralCode(getContext(), userId);
                if (cachedCode != null && !cachedCode.isEmpty() && !cachedCode.equals("XXXXXX")) {
                    currentReferralCode = cachedCode;
                    if (referralCode != null) {
                        referralCode.setText(cachedCode);
                        Log.d(TAG, "Referral code loaded from cache immediately: " + cachedCode);
                    }
                } else {
                    // Generate and cache immediately if not available
                    String generatedCode = generateReferralCode(userId);
                    currentReferralCode = generatedCode;
                    if (referralCode != null) {
                        referralCode.setText(generatedCode);
                    }
                    ReferralUtils.saveProfileToPrefs(getContext(), userId,
                            sharedPreferences.getString("userName", null),
                            sharedPreferences.getString("userEmail", null),
                            generatedCode);
                    Log.d(TAG, "Generated and cached referral code immediately: " + generatedCode);
                }
            } else {
                // Show loading placeholder
                if (referralCode != null) {
                    referralCode.setText("Loading...");
                }
            }

            // Save basic profile info locally to avoid repeated reads
            if (currentUser != null && userId != null) {
                String displayName = currentUser.getDisplayName();
                String email = currentUser.getEmail();
                // Save whatever info is available now using ReferralUtils
                ReferralUtils.saveProfileToPrefs(getContext(), userId, displayName, email, currentReferralCode);
            }

            // Initialize databaseReference if we have userId (even if currentUser is null,
            // userId in SharedPreferences means user was previously authenticated)
            if (userId != null && !userId.isEmpty()) {
                // Initialize databaseReference exactly like ProfileEditActivity
                databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
                userRef = databaseReference; // Also set userRef for compatibility
                Log.d(TAG, "Firebase initialized successfully with userId: " + userId);
            } else {
                Log.w(TAG, "userId not found in SharedPreferences");
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Please log in to view referral data");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
            // Try to get userId from SharedPreferences as fallback
            if (userId == null) {
                userId = sharedPreferences.getString("userid", null);
                if (userId != null && !userId.isEmpty()) {
                    databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
                    userRef = databaseReference;

                    // Still try to load cached code
                    String cachedCode = ReferralUtils.getCachedReferralCode(getContext(), userId);
                    if (cachedCode != null && !cachedCode.isEmpty()) {
                        currentReferralCode = cachedCode;
                        if (referralCode != null) {
                            referralCode.setText(cachedCode);
                        }
                    }
                }
            }
        }
    }

    private void checkAndCreateUserIfNeeded() {
        // Ensure sharedPreferences is initialized
        if (sharedPreferences == null) {
            if (getContext() != null) {
                sharedPreferences = requireContext().getSharedPreferences("userData", Context.MODE_PRIVATE);
            } else {
                Log.e(TAG, "Cannot check user - context is null");
                return;
            }
        }

        // Ensure databaseReference is initialized before loading
        if (databaseReference == null) {
            // Try to initialize databaseReference if we have userId
            String userId = sharedPreferences.getString("userid", null);
            if (userId == null || userId.isEmpty()) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                    sharedPreferences.edit().putString("userid", userId).apply();
                }
            }

            if (userId != null && !userId.isEmpty()) {
                databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
                userRef = databaseReference;
                Log.d(TAG, "Initialized databaseReference in checkAndCreateUserIfNeeded: " + userId);
            } else {
                Log.w(TAG, "Cannot initialize databaseReference - no userId available");
                return;
            }
        }

        // Use the same simple approach as ProfileEditActivity - just load the profile
        // This will handle generating the referral code if needed
        loadUserProfile();

        // Also load additional referral statistics using the direct method
        if (userRef != null) {
            loadReferralDataDirect();
        }
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        if (copyButton != null) {
            copyButton.setOnClickListener(v -> {
                Log.d(TAG, "Copy button clicked");
                copyReferralCode();
            });
        }

        if (inviteButton != null) {
            Log.d(TAG, "Setting up invite button click listener");
            inviteButton.setOnClickListener(v -> {
                Log.d(TAG, "Invite button clicked!");
                Context context = getContext();
                if (context != null) {
                    // Ensure we have a referral code before sharing
                    if (currentReferralCode == null || currentReferralCode.isEmpty() || currentReferralCode.equals("Loading...")) {
                        if (userId != null) {
                            currentReferralCode = generateReferralCode(userId);
                            ReferralUtils.saveProfileToPrefs(context, userId, null, null, currentReferralCode);
                        }
                    }
                    ReferralUtils.shareReferral(context);
                } else {
                    Log.e(TAG, "Context is null, cannot share");
                }
            });
            // Also set clickable and focusable for CardView
            inviteButton.setClickable(true);
            inviteButton.setFocusable(true);
        } else {
            Log.w(TAG, "inviteButton is null - cannot set click listener");
        }

        // if (boostButton != null) {
        // boostButton.setOnClickListener(v -> openBoostActivity());
        // }
    }


    private void loadReferralData() {
        loadReferralDataDirect();
        loadUserProfile();

    }

    private void loadUserProfile() {
        Log.d(TAG, "loadUserProfile called, databaseReference: " + (databaseReference != null ? "not null" : "null"));

        // Ensure sharedPreferences is initialized
        if (sharedPreferences == null) {
            if (getContext() != null) {
                sharedPreferences = requireContext().getSharedPreferences("userData", Context.MODE_PRIVATE);
            } else {
                Log.e(TAG, "Cannot load user profile - context is null");
                return;
            }
        }

        // First, try to get userId if not set
        String userId = sharedPreferences.getString("userid", null);
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
                sharedPreferences.edit().putString("userid", userId).apply();
                Log.d(TAG, "Got userId from FirebaseAuth: " + userId);
            }
        }

        // Persist into the fragment field so inner classes can safely reference it
        this.userId = userId;

        if (databaseReference != null) {
            Log.d(TAG, "Loading referral code from Firebase...");
            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Log.d(TAG, "Firebase onDataChange called, snapshot exists: " + snapshot.exists());
                    if (snapshot.exists()) {
                        String fetchedReferralCode = snapshot.child("referralCode").getValue(String.class);
                        Log.d(TAG, "Fetched referral code from Firebase: " + fetchedReferralCode);

                        // If referral code is null or placeholder, generate a new one (exactly like
                        // ProfileEditActivity)
                        if (fetchedReferralCode == null || fetchedReferralCode.isEmpty()
                                || fetchedReferralCode.equals("XXXXXX")) {
                            String userId = sharedPreferences.getString("userid", null);
                            if (userId != null) {
                                fetchedReferralCode = generateReferralCode(userId);
                                // Save to SharedPreferences using ReferralUtils
                                ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                        sharedPreferences.getString("userEmail", null), fetchedReferralCode);
                                // Save to Firebase
                                databaseReference.child("referralCode").setValue(fetchedReferralCode);
                                Log.d(TAG, "Generated and saved referral code to both SharedPreferences and Firebase: "
                                        + fetchedReferralCode);
                            }
                        }

                        // Update UI with referral code and set currentReferralCode
                        if (fetchedReferralCode != null && !fetchedReferralCode.isEmpty() && referralCode != null) {
                            currentReferralCode = fetchedReferralCode;
                            referralCode.setText(fetchedReferralCode);
                            // Save to SharedPreferences for next time using ReferralUtils
                            ReferralUtils.saveProfileToPrefs(getContext(), ReferralFragment.this.userId, sharedPreferences.getString("userName", null),
                                    sharedPreferences.getString("userEmail", null), fetchedReferralCode);
                            Log.d(TAG, "Referral code loaded from Firebase and saved to SharedPreferences: "
                                    + fetchedReferralCode);
                        } else {
                            // Fallback: generate code if still null
                            String userId = sharedPreferences.getString("userid", null);
                            if (userId != null) {
                                String generatedCode = generateReferralCode(userId);
                                currentReferralCode = generatedCode;
                                if (referralCode != null) {
                                    referralCode.setText(generatedCode);
                                }
                                // Save to SharedPreferences
                                ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                        sharedPreferences.getString("userEmail", null), generatedCode);
                                Log.d(TAG, "Generated referral code and saved to SharedPreferences: " + generatedCode);
                            }
                        }
                    } else {
                        // User doesn't exist in Firebase, generate code anyway
                        String userId = sharedPreferences.getString("userid", null);
                        if (userId != null) {
                            String generatedCode = generateReferralCode(userId);
                            currentReferralCode = generatedCode;
                            if (referralCode != null) {
                                referralCode.setText(generatedCode);
                            }
                            // Save to SharedPreferences
                            ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                    sharedPreferences.getString("userEmail", null), generatedCode);
                            // Try to save to Firebase
                            if (databaseReference != null) {
                                databaseReference.child("referralCode").setValue(generatedCode);
                            }
                            Log.d(TAG, "Generated referral code for new user and saved to SharedPreferences: "
                                    + generatedCode);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error loading user profile", error.toException());
                    // Generate and show referral code even on error (like ProfileEditActivity)
                    String userId = sharedPreferences.getString("userid", null);
                    if (userId != null) {
                        String generatedCode = generateReferralCode(userId);
                        currentReferralCode = generatedCode;
                        if (referralCode != null) {
                            referralCode.setText(generatedCode);
                        }
                        // Save to SharedPreferences
                        ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                sharedPreferences.getString("userEmail", null), generatedCode);
                        Log.d(TAG, "Generated referral code on error and saved to SharedPreferences: " + generatedCode);
                    } else {
                        ToastUtils.showInfo(getContext(), "Failed to load profile");
                    }
                }
            });
        } else {
            // databaseReference is null, try to generate code from userId
            Log.w(TAG, "databaseReference is null, generating code from userId");
            userId = sharedPreferences.getString("userid", null);
            if (userId == null || userId.isEmpty()) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                    sharedPreferences.edit().putString("userid", userId).apply();
                }
            }

            if (userId != null) {
                String generatedCode = generateReferralCode(userId);
                currentReferralCode = generatedCode;
                if (referralCode != null) {
                    referralCode.setText(generatedCode);
                }
                // Save to SharedPreferences
                ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                        sharedPreferences.getString("userEmail", null), generatedCode);
                Log.d(TAG, "Generated referral code (no database) and saved to SharedPreferences: " + generatedCode);
            } else {
                Log.e(TAG, "Cannot generate referral code - no userId available");
            }
        }
    }

    /**
     * Direct Firebase loading with proper referral code handling
     */
    private void loadReferralDataDirect() {
        Log.d(TAG, "loadReferralDataDirect: Starting... UserId: " + userId);

        // Check authentication first (required by Firebase rules)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not authenticated - cannot load referral data from Firebase");
            // Still show cached referral code if available
            String cachedCode = ReferralUtils.getCachedReferralCode(getContext());
            if (cachedCode != null && !cachedCode.isEmpty() && !cachedCode.equals("XXXXXX")) {
                currentReferralCode = cachedCode;
                if (referralCode != null) {
                    referralCode.setText(cachedCode);
                }
            }
            // Initialize UI with zeros
            if (totalEarned != null) {
                totalEarned.setText("0.0000 LYX");
            }
            if (friendsAdded != null) {
                friendsAdded.setText("0");
            }
            return;
        }

        if (userRef == null) {
            Log.w(TAG, "userRef is null, trying to re-initialize");
            // Try SharedPreferences first (like ProfileEditActivity)
            if (userId == null) {
                userId = sharedPreferences.getString("userid", null);
            }
            // Fallback to FirebaseAuth
            if (userId == null) {
                userId = currentUser.getUid();
                sharedPreferences.edit().putString("userid", userId).apply();
            }

            if (userId != null && !userId.isEmpty()) {
                userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
                Log.d(TAG, "Re-initialized userRef for user: " + userId);
            } else {
                Log.e(TAG, "Cannot load referral data - userId is null");
                if (getContext() != null) {
                    ToastUtils.showError(getContext(), "Please log in to view referral data");
                }
                return;
            }
        }

        // Remove previous listener if exists
        if (referralListener != null) {
            userRef.removeEventListener(referralListener);
            referralListener = null;
        }

        referralListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || getActivity() == null) {
                    Log.w(TAG, "Fragment not attached, skipping data update");
                    return;
                }

                // Run on UI thread to ensure safe UI updates
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded())
                        return;

                    try {
                        String fetchedReferralCode = null;

                        if (snapshot.exists()) {
                            fetchedReferralCode = snapshot.child("referralCode").getValue(String.class);
                            Log.d(TAG, "Fetched referral code from Firebase: '" + fetchedReferralCode + "'");
                        } else {
                            Log.w(TAG, "User snapshot does not exist in Firebase");
                        }

                        // CRITICAL: Use the EXACT SAME LOGIC as ProfileEditActivity
                        // If referral code is null, empty, or placeholder, generate a new one
                        if (fetchedReferralCode == null || fetchedReferralCode.isEmpty()
                                || "XXXXXX".equals(fetchedReferralCode)) {
                            Log.d(TAG, "Referral code invalid or missing, generating new one for userId: " + userId);
                            currentReferralCode = generateReferralCode(userId);

                            // Save to Firebase (this matches ProfileEditActivity behavior)
                            if (userRef != null && currentReferralCode != null) {
                                // Save to SharedPreferences immediately
                                ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                        sharedPreferences.getString("userEmail", null), currentReferralCode);
                                userRef.child("referralCode").setValue(currentReferralCode)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "Successfully saved new referral code to Firebase: "
                                                    + currentReferralCode);
                                            // Update UI after successful save
                                            if (isAdded() && referralCode != null) {
                                                referralCode.setText(currentReferralCode);
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to save referral code to Firebase", e);
                                            // Still show the generated code locally
                                            if (isAdded() && referralCode != null) {
                                                referralCode.setText(currentReferralCode);
                                            }
                                        });
                            }
                        } else {
                            // Use existing valid code
                            currentReferralCode = fetchedReferralCode;
                            Log.d(TAG, "Using fetched referral code: " + currentReferralCode);

                            // Save to SharedPreferences
                            ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                    sharedPreferences.getString("userEmail", null), currentReferralCode);

                            // Update UI immediately
                             if (referralCode != null) {
                                 referralCode.setText(currentReferralCode);
                             }
                        }


                        // Load commission earnings
                        totalCommissionEarned = 0.0;
                        if (snapshot.exists() && snapshot.child("commissions").exists()) {
                            DataSnapshot commissionsSnapshot = snapshot.child("commissions");
                            if (commissionsSnapshot.hasChildren()) {
                                for (DataSnapshot commission : commissionsSnapshot.getChildren()) {
                                    Double amount = commission.child("amount").getValue(Double.class);
                                    if (amount != null) {
                                        totalCommissionEarned += amount;
                                        Log.d(TAG, "Found commission: " + amount);
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Total commissions: " + totalCommissionEarned);

                        // Load referral mining earnings
                        referralMiningIncome = 0.0;
                        if (snapshot.exists() && snapshot.child("referralEarnings").exists()) {
                            DataSnapshot earningsSnapshot = snapshot.child("referralEarnings");
                            if (earningsSnapshot.hasChildren()) {
                                for (DataSnapshot earning : earningsSnapshot.getChildren()) {
                                    Double amount = earning.child("amount").getValue(Double.class);
                                    if (amount != null) {
                                        referralMiningIncome += amount;
                                        Log.d(TAG, "Found referral earning: " + amount);
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Total referral earnings: " + referralMiningIncome);

                        // Check boost status
                        if (snapshot.exists()) {
                            checkBoostStatus(snapshot);
                        }

                        // Calculate total earned
                        double totalReferralIncome = totalCommissionEarned + referralMiningIncome;
                        double displayEarned = isBoostActive ? totalReferralIncome * 2.0 : totalReferralIncome;

                        // Update total earned display (always update, even if 0)
                        if (totalEarned != null) {
                            totalEarned.setText(String.format(Locale.US, "%.4f LYX", displayEarned));
                            Log.d(TAG, "Updated totalEarned display: " + displayEarned);
                        } else {
                            Log.w(TAG, "totalEarned TextView is null!");
                        }

                        // Count referrals
                        int totalReferrals = 0;
                        if (snapshot.exists() && snapshot.child("referrals").exists()) {
                            DataSnapshot referralsSnapshot = snapshot.child("referrals");
                            if (referralsSnapshot.hasChildren()) {
                                totalReferrals = (int) referralsSnapshot.getChildrenCount();
                            }
                        }

                        // Update friends added display (always update, even if 0)
                        if (friendsAdded != null) {
                            friendsAdded.setText(String.valueOf(totalReferrals));
                            Log.d(TAG, "Updated friendsAdded display: " + totalReferrals);
                        } else {
                            Log.w(TAG, "friendsAdded TextView is null!");
                        }

                        Log.d(TAG, "Referral data loaded - Code: " + currentReferralCode +
                                ", Total Income: " + totalReferralIncome +
                                ", Commissions: " + totalCommissionEarned +
                                ", Referral Earnings: " + referralMiningIncome +
                                ", Referrals: " + totalReferrals +
                                ", Boost Active: " + isBoostActive);

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing referral data", e);

                        // Fallback: Generate and display a referral code
                        if (userId != null) {
                            currentReferralCode = generateReferralCode(userId);
                            if (referralCode != null) {
                                referralCode.setText(currentReferralCode);
                            }
                            // Save to SharedPreferences
                            ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                    sharedPreferences.getString("userEmail", null), currentReferralCode);
                            Log.d(TAG, "Generated referral code on exception and saved to SharedPreferences: "
                                    + currentReferralCode);
                        }

                        if (getContext() != null) {
                            ToastUtils.showError(getContext(), "Error loading referral data");
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error loading referral data", error.toException());
                if (getActivity() != null && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        // Fallback: Generate and display a referral code (like ProfileEditActivity
                        // does)
                        if (userId != null) {
                            currentReferralCode = generateReferralCode(userId);
                            if (referralCode != null) {
                                referralCode.setText(currentReferralCode);
                                Log.d(TAG, "Displayed fallback referral code: " + currentReferralCode);
                            }
                            // Save to SharedPreferences
                            ReferralUtils.saveProfileToPrefs(getContext(), userId, sharedPreferences.getString("userName", null),
                                    sharedPreferences.getString("userEmail", null), currentReferralCode);
                            // Try to save to Firebase in background (non-blocking)
                            if (userRef != null) {
                                userRef.child("referralCode").setValue(currentReferralCode)
                                        .addOnSuccessListener(
                                                aVoid -> Log.d(TAG, "Saved fallback referral code to Firebase"))
                                        .addOnFailureListener(
                                                e -> Log.w(TAG, "Could not save fallback referral code", e));
                            }
                            Log.d(TAG, "Generated referral code on cancellation and saved to SharedPreferences: "
                                    + currentReferralCode);
                        } else {
                            // Only show error if we can't generate a referral code
                            if (getContext() != null) {
                                // Prefer cached code over showing N/A
                                String cached = ReferralUtils.getCachedReferralCode(getContext());
                                if (cached != null && !cached.isEmpty()) {
                                    currentReferralCode = cached;
                                    if (referralCode != null) referralCode.setText(cached);
                                } else {
                                    ToastUtils.showError(getContext(), "Failed to load referral data");
                                }
                            }
                        }
                    });
                }
            }
        };

        // Use addValueEventListener for real-time updates
        userRef.addValueEventListener(referralListener);
    }

    private void checkBoostStatus(DataSnapshot snapshot) {
        try {
            if (snapshot.child("activeBoosts").child("referralBoost").exists()) {
                Long boostEndTime = snapshot.child("activeBoosts").child("referralBoost").child("endTime")
                        .getValue(Long.class);
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
        if (getContext() != null && isAdded()) {
            // Update boost button to show active state
            // if (boostButton != null) {
            // boostButton.setCardBackgroundColor(getResources().getColor(R.color.colorPrimary));
            // }
            ToastUtils.showInfo(getContext(), "Referral boost is active! (2x rewards)");
        }
    }

    private void hideBoostActiveIndicator() {
        // if (boostButton != null && isAdded()) {
        // boostButton.setCardBackgroundColor(getResources().getColor(R.color.cardBackground));
        // }
    }

    private void copyReferralCode() {
        if (currentReferralCode != null && getContext() != null) {
            try {
                ClipboardManager clipboard = (ClipboardManager) getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
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
                                .withEndAction(() -> copyButton.animate()
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
        } else {
            ToastUtils.showError(getContext(), "No referral code available");
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Reloading referral data");

        // Ensure sharedPreferences is initialized
        if (sharedPreferences == null && getContext() != null) {
            sharedPreferences = requireContext().getSharedPreferences("userData", Context.MODE_PRIVATE);
            Log.d(TAG, "Initialized sharedPreferences in onResume");
        }

        // If context is not available, skip loading
        if (getContext() == null || sharedPreferences == null) {
            Log.w(TAG, "Cannot load referral data - context or sharedPreferences is null");
            return;
        }

        // Ensure databaseReference is initialized
        if (databaseReference == null) {
            String userId = sharedPreferences.getString("userid", null);
            if (userId == null || userId.isEmpty()) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                    sharedPreferences.edit().putString("userid", userId).apply();
                }
            }

            if (userId != null && !userId.isEmpty()) {
                databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
                userRef = databaseReference;
                Log.d(TAG, "Initialized databaseReference in onResume: " + userId);
            }
        }

        // Load referral code first (from cache or Firebase)
        if (databaseReference != null) {
            loadUserProfile();
        } else {
            // Fallback: try to show cached code
            // Prefer per-user cached referral code via ReferralUtils
            String cachedCode = ReferralUtils.getCachedReferralCode(getContext());
            if (cachedCode != null && !cachedCode.isEmpty()) {
                currentReferralCode = cachedCode;
                if (referralCode != null) {
                    referralCode.setText(cachedCode);
                }
            }
        }

        // Load additional statistics
        if (userRef != null) {
            loadReferralData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (referralListener != null && userRef != null) {
            userRef.removeEventListener(referralListener);
            referralListener = null;
        }
        Log.d(TAG, "ReferralFragment destroyed - listeners cleaned up");
    }

    /**
     * Generates a consistent referral code based on user ID
     * IMPORTANT: This must match EXACTLY with ProfileEditActivity's
     * generateReferralCode method
     */
    private String generateReferralCode(String uid) {
        if (uid == null || uid.isEmpty()) {
            return "LYNX" + (System.currentTimeMillis() % 10000);
        }

        try {
            // Create a consistent referral code based on user ID
            String encoded = Base64.encodeToString(uid.getBytes(), Base64.NO_WRAP);
            String code = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase();

            // Ensure we have at least 6 characters
            if (code.length() >= 6) {
                return code.substring(0, 6);
            } else {
                // Pad with numbers if too short (use StringBuilder to avoid repeated concatenation)
                StringBuilder padded = new StringBuilder(code);
                while (padded.length() < 6) {
                    padded.append(Math.abs(uid.hashCode() % 10));
                }
                return padded.substring(0, 6);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating referral code", e);
            // Fallback: Use hash-based code
            return "LYNX" + Math.abs(uid.hashCode() % 10000);
        }
    }
}
