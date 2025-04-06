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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {
    private EditText email, username, password;
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
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show();
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

                            // Save user ID and username in SharedPreferences for ReferralActivity
                            SharedPreferences.Editor editor = getSharedPreferences("userData", Context.MODE_PRIVATE).edit();
                            editor.putString("userid", user.getUid());
                            editor.putString("username", user.getDisplayName());
                            editor.putBoolean("isNewUser", true);
                            editor.apply();

                            Toast.makeText(this, "Google Sign-In Successful", Toast.LENGTH_SHORT).show();

                            // Navigate to ReferralActivity
                            Intent intent = new Intent(SignupActivity.this, ReferralActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish(); // Close SignupActivity
                        }
                    } else {
                        Log.w("GoogleSignIn", "Sign in with credential failed", task.getException());
                        Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }



    private void registerUser() {
        String edit_email = email.getText().toString().trim();
        String edit_password = password.getText().toString().trim();
        String edit_username = username.getText().toString().trim();

        if (TextUtils.isEmpty(edit_username) || TextUtils.isEmpty(edit_email) || TextUtils.isEmpty(edit_password)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!termsCheckbox.isChecked()) {
            Toast.makeText(this, "Please accept the terms and conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(edit_email, edit_password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification().addOnCompleteListener(verificationTask -> {
                                if (verificationTask.isSuccessful()) {

                                    saveUserToDatabase(user,user.getDisplayName());
                                    Toast.makeText(this, "Verification email sent! Please check your inbox.", Toast.LENGTH_LONG).show();
                                } else {
                                    Log.e(TAG, "Email verification failed", verificationTask.getException());
                                    Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        Log.w(TAG, "Signup failed", task.getException());
                        Toast.makeText(this, "Signup failed. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToDatabase(FirebaseUser user, String username) {
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        Map<String, Object> updates = new HashMap<>();
        updates.put("username", username);
        updates.put("email", user.getEmail());
        updates.put("referralCode", generateReferralCode(user.getUid()));
        updates.put("mining", new HashMap<>());

        databaseReference.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User saved successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save user", e));
    }

    public static String generateReferralCode(String userId) {
        return Base64.encodeToString(userId.getBytes(), Base64.NO_WRAP).substring(0, 6);
    }
}
