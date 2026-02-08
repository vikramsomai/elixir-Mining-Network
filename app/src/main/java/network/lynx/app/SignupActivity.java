package network.lynx.app;

import static android.content.ContentValues.TAG;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {
    private EditText email, username, password,referral;
    private CheckBox termsCheckbox;
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        username = findViewById(R.id.username);
        referral= findViewById(R.id.referral);
        termsCheckbox = findViewById(R.id.termsCheckBox);
        TextView login = findViewById(R.id.loginid);

        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.signupBtn).setOnClickListener(view -> registerUser());
        findViewById(R.id.googleSignUpBtn).setOnClickListener(view -> signInWithGoogle());
        login.setOnClickListener(view -> startActivity(new Intent(SignupActivity.this, LoginActivity.class)));

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w("GoogleSignIn", "Google sign in failed", e);
                ToastUtils.showInfo(this, "Google Sign-In Failed");
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToDatabase(user, user.getDisplayName());

                            // FIX: Clear old user data before saving new user
                            SharedPreferences prefs = getSharedPreferences("userData", Context.MODE_PRIVATE);
                            String oldUserId = prefs.getString("userid", null);
                            if (oldUserId != null && !oldUserId.equals(user.getUid())) {
                                clearOldUserPreferences(oldUserId);
                            }

                            // Save user ID and username in SharedPreferences for ReferralActivity
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.clear(); // Clear all old data
                            editor.putString("userid", user.getUid());
                            editor.putString("username", user.getDisplayName());
                            editor.putString("profilePicUrl", user.getPhotoUrl()!=null?user.getPhotoUrl().toString():"");
                            editor.putBoolean("isNewUser", true);
                            editor.apply();

                            ToastUtils.showInfo(this, "Google Sign-In Successful");

                            // Navigate to ReferralActivity
                            Intent intent = new Intent(SignupActivity.this, ReferralActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish(); // Close SignupActivity
                        }
                    } else {
                        Log.w("GoogleSignIn", "Sign in with credential failed", task.getException());
                        ToastUtils.showInfo(this, "Authentication Failed");
                    }
                });
    }



    private void registerUser() {
        String edit_email = email.getText().toString().trim();
        String edit_password = password.getText().toString().trim();
        String edit_username = username.getText().toString().trim();
        String edit_referral = referral.getText().toString().trim(); // Get referral code

        if (TextUtils.isEmpty(edit_username) || TextUtils.isEmpty(edit_email) || TextUtils.isEmpty(edit_password)) {
            ToastUtils.showInfo(this, "Please fill in all fields");
            return;
        }
        if (!termsCheckbox.isChecked()) {
            ToastUtils.showInfo(this, "Please accept the terms and conditions");
            return;
        }

        mAuth.createUserWithEmailAndPassword(edit_email, edit_password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification().addOnCompleteListener(verificationTask -> {
                                if (verificationTask.isSuccessful()) {
                                    // Save user to database
                                    saveUserToDatabase(user, edit_username);

                                    // FIX: Clear old user data before saving new user
                                    SharedPreferences prefs = getSharedPreferences("userData", Context.MODE_PRIVATE);
                                    String oldUserId = prefs.getString("userid", null);
                                    if (oldUserId != null && !oldUserId.equals(user.getUid())) {
                                        clearOldUserPreferences(oldUserId);
                                    }

                                    // Save to SharedPreferences
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.clear(); // Clear all old data
                                    editor.putString("userid", user.getUid());
                                    editor.putString("username", edit_username);
                                    editor.putString("email", edit_email);
                                    editor.apply();

                                    // Process referral code if provided
                                    if (!TextUtils.isEmpty(edit_referral)) {
                                        processReferralCode(user.getUid(), edit_username, edit_referral);
                                    } else {
                                        // No referral code, go directly to MainActivity
                                        ToastUtils.showInfo(this, "Verification email sent! Please check your inbox.");
                                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                                        finish();
                                    }
                                } else {
                                    Log.e(TAG, "Email verification failed", verificationTask.getException());
                                    ToastUtils.showInfo(this, "Failed to send verification email.");
                                }
                            });
                        }
                    } else {
                        Log.w(TAG, "Signup failed", task.getException());
                        String errorMessage = "Signup failed. ";
                        if (task.getException() != null) {
                            String exceptionMessage = task.getException().getMessage();
                            if (exceptionMessage != null) {
                                if (exceptionMessage.contains("email address is already in use")) {
                                    errorMessage = "This email is already registered. Please login instead.";
                                } else if (exceptionMessage.contains("password is invalid") || exceptionMessage.contains("at least 6 characters")) {
                                    errorMessage = "Password must be at least 6 characters.";
                                } else if (exceptionMessage.contains("email address is badly formatted")) {
                                    errorMessage = "Please enter a valid email address.";
                                } else if (exceptionMessage.contains("network error")) {
                                    errorMessage = "Network error. Please check your internet connection.";
                                } else {
                                    errorMessage += exceptionMessage;
                                }
                            }
                        }
                        ToastUtils.showError(this, errorMessage);
                    }
                });
    }

    // Add this new method to process referral codes
    private void processReferralCode(String userId, String username, String referralCode) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        usersRef.orderByChild("referralCode").equalTo(referralCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        boolean referralApplied = false;
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                String referrerUserId = snapshot.getKey();

                                // Update referrer's data
                                DatabaseReference referrerRef = usersRef.child(referrerUserId);

                                Map<String, Object> referralData = new HashMap<>();
                                referralData.put("refer_UserId", userId);
                                referralData.put("refer_username", username);

                                referrerRef.child("referrals").push().updateChildren(referralData);
                                referrerRef.child("referralCount").setValue(ServerValue.increment(1));
                                referrerRef.child("bonusPoints").setValue(ServerValue.increment(5));
                                referralApplied = true;
                                ToastUtils.showInfo(SignupActivity.this, "Referral code applied successfully!");
                                // FIXED: Use 'totalcoins' (lowercase) to match rest of app
                                usersRef.child(referrerUserId).child("totalcoins").setValue(ServerValue.increment(50));

                            }
                        } else {
                            Toast.makeText(SignupActivity.this, "Invalid referral code, but account created successfully", Toast.LENGTH_SHORT).show();
                        }
                        if (referralApplied) {
                            // FIXED: Use 'totalcoins' (lowercase) to match rest of app
                            usersRef.child(userId).child("totalcoins").setValue(ServerValue.increment(50));
                            ToastUtils.showInfo(SignupActivity.this, "Referral code applied! 50 coins added.");
                        } else {
                            ToastUtils.showInfo(SignupActivity.this, "Invalid referral code, but account created successfully");
                        }

                        // Regardless of referral code validity, proceed to MainActivity
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        ToastUtils.showInfo(SignupActivity.this, "Error processing referral code");
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    }
                });
    }

    private void saveUserToDatabase(FirebaseUser user, String username) {
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

        // Generate referral code
        String referralCode = generateReferralCode(user.getUid());

        // Create a complete user object with all required fields
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("email", user.getEmail());
        userData.put("referralCode", referralCode);
        userData.put("totalcoins", 0.0);
        userData.put("dailyStreak", 0);
        userData.put("level", 1);
        userData.put("createdAt", ServerValue.TIMESTAMP);
        userData.put("lastLogin", ServerValue.TIMESTAMP);

        // Initialize mining object
        Map<String, Object> mining = new HashMap<>();
        mining.put("isMiningActive", false);
        mining.put("startTime", 0);
        userData.put("mining", mining);

        // Save to ReferralUtils for immediate local caching (avoids multiple Firebase reads)
        ReferralUtils.saveProfileToPrefs(this, user.getUid(), username, user.getEmail(), referralCode);
        Log.d(TAG, "Saved user profile to local prefs: userId=" + user.getUid() + ", email=" + user.getEmail() + ", referralCode=" + referralCode);

        // Use setValue for initial creation, then updateChildren won't have issues
        databaseReference.setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved successfully to Firebase");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user to Firebase: " + e.getMessage(), e);
                    // Show error to user
                    ToastUtils.showError(SignupActivity.this, "Failed to create account. Please try again.");
                });
    }

    public static String generateReferralCode(String userId) {
        try {
            // Use Base64 encoding and clean it up
            String encoded = Base64.encodeToString(userId.getBytes(), Base64.NO_WRAP);
            // Remove non-alphanumeric characters and convert to uppercase
            String cleaned = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            // Take first 6 characters
            if (cleaned.length() >= 6) {
                return cleaned.substring(0, 6);
            } else {
                // Pad with hash code if too short
                return cleaned + String.valueOf(Math.abs(userId.hashCode()) % 10000);
            }
        } catch (Exception e) {
            // Fallback: use hash code
            return "LYX" + String.valueOf(Math.abs(userId.hashCode()) % 100000);
        }
    }

    /**
     * FIX: Clear old user's preferences to prevent data leaking between accounts
     */
    private void clearOldUserPreferences(String oldUserId) {
        Log.d(TAG, "Clearing old user preferences for: " + oldUserId);
        try {
            getSharedPreferences("user_checkin_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("spinPrefs_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("spinPrefs", MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("MiningSync_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("TokenPrefs_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("BoostManager_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("Achievements_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("HourlyBonus_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("LuckyNumber_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("ScratchCard_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("MiningStreak_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("TaskManager_" + oldUserId, MODE_PRIVATE).edit().clear().apply();

            // Clear old user's ReferralUtils prefs
            ReferralUtils.clearUserPrefs(this, oldUserId);

            // Reset singleton managers
            BoostManager.resetInstance();
            MiningSyncManager.resetInstance();
            MiningStreakManager.resetInstance();
            AchievementManager.resetInstance();
            HourlyBonusManager.resetInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing old user preferences", e);
        }
    }
}
