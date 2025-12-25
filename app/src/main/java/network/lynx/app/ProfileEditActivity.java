package network.lynx.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class ProfileEditActivity extends AppCompatActivity {

    private TextView emailView, referralCodeView;
    private ImageView backButton, copyReferralButton;
    private View deleteAccountButton, logoutButton;

    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        sharedPreferences = getSharedPreferences("userData", MODE_PRIVATE);

        // Get user ID from shared preferences
        String userId = sharedPreferences.getString("userid", null);
        if (currentUser != null && userId != null && !userId.isEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
        }

        // Initialize UI components
        emailView = findViewById(R.id.email_view);
        referralCodeView = findViewById(R.id.referral_code_view);
        backButton = findViewById(R.id.backNav);
        copyReferralButton = findViewById(R.id.copy_referral);
        deleteAccountButton = findViewById(R.id.deleteBtn);
        logoutButton = findViewById(R.id.logoutBtn);

        // Load user data
        loadUserProfile();

        // Set up click listeners
        backButton.setOnClickListener(v -> finish());

        copyReferralButton.setOnClickListener(v -> copyReferralCode());

        deleteAccountButton.setOnClickListener(v -> confirmDeleteAccount());

        logoutButton.setOnClickListener(v -> confirmLogout());
    }

    // Fetch user profile data from Firebase
    private void loadUserProfile() {
        String userId = sharedPreferences.getString("userid", null);

        // OPTIMIZATION: Try to load cached referral code first for immediate display
        if (userId != null) {
            String cachedCode = ReferralUtils.getCachedReferralCode(this, userId);
            if (cachedCode != null && !cachedCode.isEmpty() && !cachedCode.equals("XXXXXX")) {
                referralCodeView.setText(cachedCode);
            }

            // Also get cached email
            String cachedEmail = sharedPreferences.getString("email", null);
            if (cachedEmail != null && !cachedEmail.isEmpty()) {
                emailView.setText(cachedEmail);
            }
        }

        if (databaseReference != null) {
            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String email = snapshot.child("email").getValue(String.class);
                        String referralCode = snapshot.child("referralCode").getValue(String.class);

                        if (email != null) {
                            emailView.setText(email);
                        }

                        // If referral code is null or placeholder, generate a new one
                        if (referralCode == null || referralCode.isEmpty() || referralCode.equals("XXXXXX")) {
                            String usrId = sharedPreferences.getString("userid", null);
                            if (usrId != null) {
                                referralCode = generateReferralCode(usrId);
                                // Save to Firebase
                                databaseReference.child("referralCode").setValue(referralCode);
                            }
                        }

                        if (referralCode != null && referralCodeView != null) {
                            referralCodeView.setText(referralCode);
                            // Save to ReferralUtils cache
                            String usrId = sharedPreferences.getString("userid", null);
                            ReferralUtils.saveProfileToPrefs(ProfileEditActivity.this, usrId, null, email, referralCode);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ToastUtils.showInfo(ProfileEditActivity.this, "Failed to load profile");
                }
            });
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

    private void copyReferralCode() {
        String referralCode = referralCodeView.getText().toString();
        if (!referralCode.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Referral Code", referralCode);
            clipboard.setPrimaryClip(clip);
            ToastUtils.showInfo(this, "Referral code copied!");
        }
    }

    // Confirm account deletion
    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> askForPasswordAndDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Confirm logout
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> logoutUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logoutUser() {
        String userId = sharedPreferences.getString("userid", "unknown_user");

        // Reset all singleton manager instances to prevent data leaks
        try {
            BoostManager.resetInstance();
            MiningSyncManager.resetInstance();
            MiningStreakManager.resetInstance();
            AchievementManager.resetInstance();
            HourlyBonusManager.resetInstance();
            FirebaseManager.resetInstance();
            Log.d("ProfileEditActivity", "All singleton managers reset");
        } catch (Exception e) {
            Log.e("ProfileEditActivity", "Error resetting managers", e);
        }

        // Clear ALL user-related SharedPreferences
        try {
            // Clear per-user check-in preferences
            getSharedPreferences("user_checkin_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear mining preferences
            getSharedPreferences("MiningSync_" + userId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("TokenPrefs_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear boost preferences
            getSharedPreferences("BoostManager_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear achievement preferences
            getSharedPreferences("Achievements_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear hourly bonus preferences
            getSharedPreferences("HourlyBonus_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear lucky number preferences
            getSharedPreferences("LuckyNumber_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear scratch card preferences
            getSharedPreferences("ScratchCard_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear mining streak preferences
            getSharedPreferences("MiningStreak_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear task manager preferences
            getSharedPreferences("TaskManager_" + userId, MODE_PRIVATE).edit().clear().apply();

            // Clear spin preferences
            getSharedPreferences("spinPrefs_" + userId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("spinPrefs", MODE_PRIVATE).edit().clear().apply(); // Clear old global prefs

            // Clear mining prefs
            getSharedPreferences("mining_prefs", MODE_PRIVATE).edit().clear().apply();

            // Clear general user data
            sharedPreferences.edit().clear().apply();

            // ALSO: clear per-user ReferralUtils scoped prefs to avoid overlap when another user logs in on this device
            try {
                ReferralUtils.clearUserPrefs(ProfileEditActivity.this, userId);
            } catch (Exception ignored) {
            }

            Log.d("ProfileEditActivity", "All user preferences cleared for logout");
        } catch (Exception e) {
            Log.e("ProfileEditActivity", "Error clearing preferences", e);
        }

        // Sign out from Firebase
        auth.signOut();

        // Redirect to login screen
        Intent intent = new Intent(ProfileEditActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Show an input dialog to get the user's password
    private void askForPasswordAndDelete() {
        if (currentUser == null) {
            ToastUtils.showInfo(this, "Error: No user logged in");
            return;
        }

        // If user signed in with Google or Facebook, no password is required
        String providerId = currentUser.getProviderData().get(1).getProviderId();
        if (providerId.equals("google.com") || providerId.equals("facebook.com")) {
            deleteAccount(null); // Skip password entry
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Password");

        final View inputView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        builder.setView(inputView);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            TextView passwordInput = inputView.findViewById(R.id.password_input);
            String password = passwordInput.getText().toString().trim();
            if (!password.isEmpty()) {
                deleteAccount(password);
            } else {
                ToastUtils.showInfo(ProfileEditActivity.this, "Password required");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // Delete user account with proper re-authentication
    private void deleteAccount(String password) {
        if (currentUser == null || databaseReference == null) {
            ToastUtils.showInfo(this, "Error: No user logged in");
            return;
        }

        // Re-authentication logic
        AuthCredential credential = null;
        if (password != null) {
            credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        } else if (currentUser.getProviderData().get(1).getProviderId().equals("google.com")) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            }
        } else if (currentUser.getProviderData().get(1).getProviderId().equals("facebook.com")) {
            credential = FacebookAuthProvider.getCredential(currentUser.getUid());
        }

        if (credential == null) {
            ToastUtils.showInfo(this, "Failed to authenticate. Please log in again.");
            return;
        }

        currentUser.reauthenticate(credential).addOnCompleteListener(authTask -> {
            if (authTask.isSuccessful()) {
                // Delete user data from Firebase Database
                databaseReference.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Delete user from Firebase Authentication
                        currentUser.delete().addOnCompleteListener(task1 -> {
                            if (task1.isSuccessful()) {
                                clearAppData(); // Clear storage and redirect
                            } else {
                                ToastUtils.showInfo(ProfileEditActivity.this, "Failed to delete account: " +
                                        task1.getException().getMessage());
                            }
                        });
                    } else {
                        ToastUtils.showInfo(ProfileEditActivity.this, "Failed to delete user data");
                    }
                });
            } else {
                ToastUtils.showInfo(ProfileEditActivity.this, "Re-authentication failed. Please check your password.");
            }
        });
    }

    // Clear shared preferences and redirect to login
    private void clearAppData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        // Also clear ReferralUtils per-user prefs if possible
        try {
            String uid = sharedPreferences.getString("userid", null);
            if (uid != null) {
                ReferralUtils.clearUserPrefs(ProfileEditActivity.this, uid);
            } else {
                ReferralUtils.clearCurrentUserPrefs(ProfileEditActivity.this);
            }
        } catch (Exception ignored) {
        }

        ToastUtils.showInfo(ProfileEditActivity.this, "Account deleted successfully");
        auth.signOut();

        Intent intent = new Intent(ProfileEditActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}