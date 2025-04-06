package network.lynx.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

    private EditText editUsername;
    private TextView editEmail;
    ImageView back;
    private Button deleteAccountButton;

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
        editUsername = findViewById(R.id.username);
        editEmail = findViewById(R.id.email_view);
        deleteAccountButton = findViewById(R.id.deleteBtn);
        back=findViewById(R.id.backNav);

        // Load user data from Firebase
        loadUserProfile();
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        // Handle account deletion
        deleteAccountButton.setOnClickListener(view -> confirmDeleteAccount());
    }

    // Fetch user profile data from Firebase
    private void loadUserProfile() {
        if (databaseReference != null) {
            databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String username = snapshot.child("username").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);

                        if (username != null) {
                            editUsername.setText(username);
                        }
                        if (email != null) {
                            editEmail.setText(email);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ProfileEditActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                }
            });
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

    // Show an input dialog to get the user's password
    private void askForPasswordAndDelete() {
        if (currentUser == null) {
            Toast.makeText(this, "Error: No user logged in", Toast.LENGTH_SHORT).show();
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

        final EditText input = new EditText(this);
        input.setHint("Enter your password");
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (!password.isEmpty()) {
                deleteAccount(password);
            } else {
                Toast.makeText(ProfileEditActivity.this, "Password required", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // Delete user account with proper re-authentication
    private void deleteAccount(String password) {
        if (currentUser == null || databaseReference == null) {
            Toast.makeText(this, "Error: No user logged in", Toast.LENGTH_SHORT).show();
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
        }

        if (credential == null) {
            Toast.makeText(this, "Failed to authenticate. Please log in again.", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(ProfileEditActivity.this, "Failed to delete account: " + task1.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Toast.makeText(ProfileEditActivity.this, "Failed to delete user data", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(ProfileEditActivity.this, "Re-authentication failed. Please check your password.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Clear shared preferences and redirect to login
    private void clearAppData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        Toast.makeText(ProfileEditActivity.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
        auth.signOut();

        Intent intent = new Intent(ProfileEditActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
