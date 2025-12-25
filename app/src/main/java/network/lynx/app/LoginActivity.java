package network.lynx.app;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    TextView login;
    EditText emailBox, password;
    FirebaseAuth mAuth;
    MaterialCardView loginBtn, googleSignInBtn;
    GoogleSignInClient googleSignInClient;
    DatabaseReference databaseReference;
    FirebaseDatabase database;

    private static final int RC_SIGN_IN = 9001;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        SharedPreferences prefs = getSharedPreferences("userData", MODE_PRIVATE);
        String userId = prefs.getString("userid", null);

        if (userId != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        // Init views
        emailBox = findViewById(R.id.email);
        password = findViewById(R.id.password);
        loginBtn = findViewById(R.id.loginbtn);
        googleSignInBtn = findViewById(R.id.googleSignUpBtn);
        login = findViewById(R.id.loginid);

        // Setup Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInBtn.setOnClickListener(view -> signInWithGoogle());

        loginBtn.setOnClickListener(view -> {
            String email = emailBox.getText().toString().trim();
            String pass = password.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                emailBox.setError("Please enter your email");
                return;
            }
            if (TextUtils.isEmpty(pass)) {
                password.setError("Please enter your password");
                return;
            }

            mAuth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            saveUserData(user);
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            ToastUtils.showInfo(this, "Login failed. Please check your credentials");
                        }
                    });
        });

        login.setOnClickListener(view -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            // BUG FIX: Add null check for data
            if (data == null) {
                Log.e(TAG, "Google Sign-in returned null data");
                ToastUtils.showError(this, "Google Sign-in failed: No data returned");
                return;
            }
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                firebaseAuthWithGoogle(account);
            }
        } catch (ApiException e) {
            Log.w(TAG, "Google sign in failed", e);
            ToastUtils.showInfo(this, "Google Sign-in failed: " + e.getMessage());
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                updateUI(user);
            } else {
                Log.e("FirebaseAuth", "Firebase sign-in failed", task.getException());
                ToastUtils.showInfo(this, "Sign-in failed." );
            }
        });
    }

    private void updateUI(FirebaseUser user) {
        if (user == null) {
//            Toast.makeText(this, "User is null!", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = user.getUid();
        String email = user.getEmail();
        String displayName = getDisplayName(user);
        String profilePicUrl = (user.getPhotoUrl() != null) ? user.getPhotoUrl().toString() : "";
        String referralCode = generateReferralCode(id);

        databaseReference = database.getReference("users").child(id);

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                saveUserData(user);

                if (snapshot.exists()) {
                    // Existing user
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                } else {
                    // New user, store info and go to ReferralActivity
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("username", displayName);
                    updates.put("email", email);
                    updates.put("profilePicUrl",profilePicUrl);
                    updates.put("referralCode", referralCode);

                    databaseReference.updateChildren(updates).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Intent intent = new Intent(LoginActivity.this, ReferralActivity.class);
                            intent.putExtra("email", email);
                            intent.putExtra("username", displayName);
                            intent.putExtra("profilePicUrl", profilePicUrl);
                            intent.putExtra("userid", id);
                            startActivity(intent);
                            finish();
                        } else {
                            ToastUtils.showInfo(LoginActivity.this, "Failed to save new user info.");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
//                Toast.makeText(LoginActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserData(FirebaseUser user) {
        SharedPreferences prefs = getSharedPreferences("userData", MODE_PRIVATE);

        // FIX: Check if this is a different user - clear old cached data
        String oldUserId = prefs.getString("userid", null);
        String newUserId = user.getUid();

        if (oldUserId != null && !oldUserId.equals(newUserId)) {
            // Different user logging in - clear ALL old user data
            Log.d(TAG, "Different user detected. Clearing old user data. Old: " + oldUserId + ", New: " + newUserId);

            // Clear old user's specific preferences
            getSharedPreferences("user_checkin_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
            getSharedPreferences("spinPrefs_" + oldUserId, MODE_PRIVATE).edit().clear().apply();
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

            // Reset singleton managers to ensure fresh state
            try {
                BoostManager.resetInstance();
                MiningSyncManager.resetInstance();
                MiningStreakManager.resetInstance();
                AchievementManager.resetInstance();
                HourlyBonusManager.resetInstance();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting managers during user switch", e);
            }
        }

        // Clear old userData prefs and save new user data
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // Clear all old data
        editor.putString("userid", newUserId);
        editor.putString("email", user.getEmail());
        editor.putString("username", user.getDisplayName());
        editor.putString("profilePicUrl", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);
        editor.apply();

        // Generate referral code for immediate local caching
        String referralCode = generateReferralCode(newUserId);

        // Save to ReferralUtils for centralized access (email, name, referral code)
        ReferralUtils.saveProfileToPrefs(this, newUserId, user.getDisplayName(), user.getEmail(), referralCode);
        Log.d(TAG, "Saved user profile to local prefs: " + newUserId + ", email: " + user.getEmail() + ", referralCode: " + referralCode);

        // Ensure user has a referral code in Firebase (for existing users who don't have one)
        ensureReferralCodeExists(user.getUid());
    }

    private void ensureReferralCodeExists(String userId) {
        DatabaseReference userRef = database.getReference("users").child(userId);
        userRef.child("referralCode").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String existingCode = snapshot.getValue(String.class);
                if (existingCode == null || existingCode.isEmpty() || existingCode.equals("XXXXXX")) {
                    // Generate and save a new referral code
                    String newCode = generateReferralCode(userId);
                    userRef.child("referralCode").setValue(newCode);
                    // Also save to local prefs
                    ReferralUtils.saveProfileToPrefs(LoginActivity.this, userId, null, null, newCode);
                    Log.d(TAG, "Generated new referral code for user: " + newCode);
                } else {
                    // Save existing code to local prefs for quick access
                    ReferralUtils.saveProfileToPrefs(LoginActivity.this, userId, null, null, existingCode);
                    Log.d(TAG, "Cached existing referral code: " + existingCode);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking referral code", error.toException());
            }
        });
    }

    public static String getDisplayName(FirebaseUser user) {
        if (!TextUtils.isEmpty(user.getDisplayName())) return user.getDisplayName();

        for (UserInfo userInfo : user.getProviderData()) {
            if (!TextUtils.isEmpty(userInfo.getDisplayName())) {
                return userInfo.getDisplayName();
            }
        }
        return "User";
    }

    public static String generateReferralCode(String userId) {
        try {
            String encoded = Base64.encodeToString(userId.getBytes(), Base64.NO_WRAP);
            String cleaned = encoded.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (cleaned.length() >= 6) {
                return cleaned.substring(0, 6);
            } else {
                return cleaned + String.valueOf(Math.abs(userId.hashCode()) % 10000);
            }
        } catch (Exception e) {
            return "LYX" + String.valueOf(Math.abs(userId.hashCode()) % 100000);
        }
    }
}
