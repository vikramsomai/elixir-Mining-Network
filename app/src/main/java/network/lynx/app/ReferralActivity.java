package network.lynx.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;



public class ReferralActivity extends AppCompatActivity {

    MaterialButton continues;
    EditText referralEdit;
    SharedPreferences sharedPreferences;
    DatabaseReference databaseReference;
    String userId, name,profilePicUrl;
    TextView skip;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable Edge-to-Edge display
        setContentView(R.layout.activity_referral);

        sharedPreferences = getSharedPreferences("userData", Context.MODE_PRIVATE);
        userId = sharedPreferences.getString("userid", null);
        name = sharedPreferences.getString("username", null);
        profilePicUrl=sharedPreferences.getString("profilePicUrl",null);
        continues = findViewById(R.id.continueBtn);
        referralEdit = findViewById(R.id.referralEdit);
        skip=findViewById(R.id.skip);
        if (userId == null) {
            Intent i = new Intent(ReferralActivity.this, LoginActivity.class);
            startActivity(i);
            finish();
            return;
        }

        // Use consistent "users" node (all lowercase)
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        continues.setOnClickListener(view -> {
            // Disable button to prevent multiple clicks
            continues.setEnabled(false);
            String enteredReferral = referralEdit.getText().toString().trim();
            if (enteredReferral.isEmpty()) {
                Toast.makeText(ReferralActivity.this, "Please enter a referral code", Toast.LENGTH_SHORT).show();
                continues.setEnabled(true);
            } else {
                findReferrer(enteredReferral);
            }
        });
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ReferralActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
    }

    public void onReferralFound(String referrerUserId) {
        // Get a reference to the referrer's data.
        DatabaseReference referrerRef = databaseReference.child(referrerUserId);
        // Get a reference to the current user's data.
        DatabaseReference currentUserRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Push referral details under the referrer's "referrals" node.
        currentUserRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String referUser = snapshot.child("username").getValue(String.class);
                if (referUser == null) referUser = "Unknown";
                Map<String, Object> updates = new HashMap<>();
                updates.put("refer_UserId", userId);
                updates.put("refer_username", referUser);
                referrerRef.child("referrals").push().updateChildren(updates);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
        // Increment referral count and bonus points.
        referrerRef.child("referralCount").setValue(ServerValue.increment(1));
        referrerRef.child("bonusPoints").setValue(ServerValue.increment(5));

        animateAndProceed();
    }

    public void onReferralNotFound() {
        Toast.makeText(this, "Referral code not found", Toast.LENGTH_SHORT).show();
        continues.setEnabled(true);
        animateAndProceed();
    }

    public void findReferrer(String referralCode) {
        databaseReference.orderByChild("referralCode").equalTo(referralCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                String referrerUserId = snapshot.getKey();
                                onReferralFound(referrerUserId);
                                return;
                            }
                        } else {
                            onReferralNotFound();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Toast.makeText(ReferralActivity.this, "Error retrieving referral info", Toast.LENGTH_SHORT).show();
                        continues.setEnabled(true);
                    }
                });
    }

    // Animate the view (fade out) then proceed to MainActivity.
    private void animateAndProceed() {
        View rootView = findViewById(R.id.main);
        rootView.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            // Clear the isNewUser flag so subsequent logins go directly to MainActivity.
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isNewUser", false);
            editor.apply();
            Intent intent = new Intent(ReferralActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }).start();
    }

    // Optional: Copy referral code to clipboard if needed.
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Referral Code", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }
}
