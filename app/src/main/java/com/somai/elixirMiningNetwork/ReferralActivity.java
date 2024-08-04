package com.somai.elixirMiningNetwork;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    DatabaseReference databaseReference,refers;
    String userId,name;
    FirebaseAuth auth;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_referral);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
         sharedPreferences = getSharedPreferences("userData", Context.MODE_PRIVATE);
         userId = sharedPreferences.getString("userid", null);
         name=sharedPreferences.getString("username",null);
        continues=findViewById(R.id.continueBtn);
        referralEdit=findViewById(R.id.referralEdit);
       auth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("Users");


        continues.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                        findReferrer(referralEdit.getText().toString());
            }
        });
    }
    public void onReferralFound(String referrerUserId) {

        DatabaseReference referrerRef = databaseReference.child(referrerUserId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("referUserId", userId);


        databaseReference.updateChildren(updates);
        referrerRef.child("referrerId").push().child("Userid").setValue(userId);
        databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String referUser=snapshot.child("username").getValue(String.class);
                referrerRef.child("referrerId").push().child("username").setValue(referUser);

                updates.put("totalStreak", referUser);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        referrerRef.child("referralCount").setValue(ServerValue.increment(1)); // Example: increment referral count
        referrerRef.child("bonusPoints").setValue(ServerValue.increment(10)); // Example: add bonus points
        // Proceed with linking the new user to the referrer
        Intent intent=new Intent(ReferralActivity.this,MainActivity.class);
        startActivity(intent);
    }

    public void onReferralNotFound() {
        Toast.makeText(this, "not found", Toast.LENGTH_SHORT).show();
        Intent intent=new Intent(ReferralActivity.this,MainActivity.class);
        startActivity(intent);
        // Handle invalid referral code case
    }
    public void findReferrer(String referralCode) {
        databaseReference.orderByChild("referralCode").equalTo(referralCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Assume only one user has this referral code
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                String referrerUserId = snapshot.getKey();
                                onReferralFound(referrerUserId);
                            }
                        } else {
                            // Handle case where the referral code is invalid
                            onReferralNotFound();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Handle error
                    }
                });
    }
}