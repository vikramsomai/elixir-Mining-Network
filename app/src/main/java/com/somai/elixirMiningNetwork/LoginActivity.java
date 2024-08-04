package com.somai.elixirMiningNetwork;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    TextView login;
    FirebaseAuth auth;
    MaterialCardView signInBtn;
    private DatabaseReference databaseReference,refers;
    GoogleSignInAccount account;
    FirebaseDatabase database;
    private static final int RC_SIGN_IN = 1;
    GoogleSignInOptions googleSignInOptions;
    GoogleSignInClient googleSignInClient;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        SharedPreferences prefs=getSharedPreferences("userData",MODE_PRIVATE);

        String name=prefs.getString("username",null);
        String email=prefs.getString("email",null);
        String picture=prefs.getString("picture",null);

        if(name!=null){
            Intent intent=new Intent(LoginActivity.this,MainActivity.class);
            intent.putExtra("email",email);
            intent.putExtra("username",name);
            intent.putExtra("picture",picture);
            startActivity(intent);

        }


        signInBtn = findViewById(R.id.siginbtn);

        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(LoginActivity.this, googleSignInOptions);
        signInBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
             account = completedTask.getResult(ApiException.class);
            if (account != null) {
                updateUI(account);
            } else {
                updateUI(null);
            }
        } catch (ApiException e) {
            Log.e("TAG", "Sign-in failed jj: ", e);
            updateUI(null);
        }
    }

    private void updateUI(GoogleSignInAccount account) {
        if (account != null) {
            String email = account.getEmail();
            String displayName = account.getDisplayName();
            String id = account.getId();

            String profilePicUrl = String.valueOf(account.getPhotoUrl());

            SharedPreferences.Editor editor=getSharedPreferences("userData",MODE_PRIVATE).edit();

            editor.putString("username",account.getDisplayName());
            editor.putString("email",account.getEmail());
            editor.putString("picture",profilePicUrl);
            editor.putString("userid",account.getId().toString());
            editor.apply();
            String referral=generateReferralCode(id);
            databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(id);


            refers = FirebaseDatabase.getInstance().getReference("Users").child(id);

            refers.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String refer = snapshot.child("referralCode").getValue(String.class);

                        if(refer!=null){
                            Intent intents=new Intent(LoginActivity.this,MainActivity.class);
                            startActivity(intents);
                        }

                    }
                    else {
                        Intent intent = new Intent(LoginActivity.this,ReferralActivity.class);
                        intent.putExtra("email", email);
                        intent.putExtra("username", displayName);
                        intent.putExtra("profilePicUrl", profilePicUrl);
                        intent.putExtra("userid",id);
                             Map<String, Object> updates = new HashMap<>();
                            updates.put("username", displayName);
                            updates.put("email",email);
                            updates.put("referralCode", referral);
                            databaseReference.updateChildren(updates);
                        startActivity(intent);
                    }

                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }

            });



        } else {
            Toast.makeText(this, "Sign-in failed.", Toast.LENGTH_SHORT).show();
        }
    }
    public static String generateReferralCode(String userId) {
        // Base64 encode the user ID and take the first 6 characters
        return Base64.encodeToString(userId.getBytes(), Base64.NO_WRAP).substring(0, 6);
    }
}
