package com.somai.elixirMiningNetwork;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    TextView login;
    EditText emailBox,password;;
    FirebaseAuth auth,mauth;
    ImageView signInBtn;
    MaterialButton loginBtn;
    private DatabaseReference databaseReference,refers,databaseReference2;
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

        mauth = FirebaseAuth.getInstance();
        SharedPreferences prefs=getSharedPreferences("userData",MODE_PRIVATE);
        String userIds=prefs.getString("userid",null);
        String name=prefs.getString("username",null);
        String email=prefs.getString("email",null);
        String picture=prefs.getString("picture",null);
        databaseReference2 = FirebaseDatabase.getInstance().getReference("Users");
        if(userIds!=null){
            Intent intent=new Intent(LoginActivity.this,MainActivity.class);
            intent.putExtra("email",email);
            intent.putExtra("username",name);
            intent.putExtra("picture",picture);
            startActivity(intent);

        }

        login=findViewById(R.id.loginid);
//        signInBtn = findViewById(R.id.signinbtn);
        loginBtn=findViewById(R.id.loginbtn);
        emailBox=findViewById(R.id.email);
        password=findViewById(R.id.password);



        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String edit_email,edit_password;
                edit_email=String.valueOf(emailBox.getText()) ;
                edit_password=String.valueOf(password.getText()) ;
            if(TextUtils.isEmpty(edit_email))
                {
                    Toast.makeText(LoginActivity.this, "Please enter the email", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(TextUtils.isEmpty(edit_password))
                {
                    Toast.makeText(LoginActivity.this, "Please enter the password", Toast.LENGTH_SHORT).show();
                    return;
                }

                mauth.signInWithEmailAndPassword(edit_email, edit_password)
                        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    Log.d(TAG, "signInWithEmail:success");
//                                    Toast.makeText(LoginActivity.this, "successfully", Toast.LENGTH_SHORT).show();
                                    FirebaseUser user = mauth.getCurrentUser();
                                    SharedPreferences.Editor editor=getSharedPreferences("userData",MODE_PRIVATE).edit();

//                                  editor.putString("username",ed);
                                    Toast.makeText(LoginActivity.this, "username "+user.getDisplayName(), Toast.LENGTH_SHORT).show();
//                                    SharedPreferences.Editor editor=getSharedPreferences("userData",MODE_PRIVATE).edit();

//                                    editor.putString("username",account.getDisplayName());
//                                    editor.putString("email",account.getEmail());
                                    editor.putString("userid",user.getUid().toString());
                                    editor.apply();
                                    editor.putString("email",user.getEmail());
                                    editor.putString("userid",user.getUid().toString());
                                    editor.apply();
                                    Intent i=new Intent(LoginActivity.this,MainActivity.class);
                                    startActivity(i);

                                } else {
                                    // If sign in fails, display a message to the user.
                                    Log.w(TAG, "signInWithEmail:failure", task.getException());
                                    Toast.makeText(LoginActivity.this, "Authentication failed.",
                                            Toast.LENGTH_SHORT).show();
                                    updateUI(null);
                                }
                            }
                        });
            }
        });

        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(LoginActivity.this, googleSignInOptions);
//        signInBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent signInIntent = googleSignInClient.getSignInIntent();
//                startActivityForResult(signInIntent, RC_SIGN_IN);
//            }
//        });

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i=new Intent(LoginActivity.this,SignupActivity.class);
                startActivity(i);
            }
        });

    }
    public static String getDisplayName(FirebaseUser user) {
        String displayName = user.getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        for (UserInfo userInfo : user.getProviderData()) {
            if (!TextUtils.isEmpty(userInfo.getDisplayName())) {
                return userInfo.getDisplayName();
            }
        }

        return null;
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
private  void customUpdateUI(String user){
    Toast.makeText(this, "user:"+user, Toast.LENGTH_SHORT).show();
}
    private void updateUI(GoogleSignInAccount account) {
        if (account != null) {
            String email = account.getEmail();
            String displayName = account.getDisplayName();
            String id = account.getId();

            String profilePicUrl = String.valueOf(account.getPhotoUrl());


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
//                        databaseReference.orderByChild("email").equalTo(email)
//                                .addValueEventListener(new ValueEventListener() {
//                                    @Override
//                                    public void onDataChange(DataSnapshot dataSnapshot) {
//                                        if (dataSnapshot.exists()) {
//                                            getUserData();
//                                         //   Toast.makeText(LoginActivity.this, "Account found", Toast.LENGTH_SHORT).show();
////                                            Intent intent = new Intent(LoginActivity.this,ReferralActivity.class);
////                                            startActivity(intent);
//
//                                        } else {
                                            getUserData();
                                            // Handle case where the referral code is invalid
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
//                                        }
//                                    }
//
//                                    @Override
//                                    public void onCancelled(@NonNull DatabaseError error) {
//
//                                    }});

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
    public void getUserData(){
        SharedPreferences.Editor editor=getSharedPreferences("userData",MODE_PRIVATE).edit();

        editor.putString("username",account.getDisplayName());
        editor.putString("email",account.getEmail());
        editor.putString("picture",String.valueOf(account.getPhotoUrl()));
        editor.putString("userid",account.getId().toString());
        editor.apply();
    }
}
