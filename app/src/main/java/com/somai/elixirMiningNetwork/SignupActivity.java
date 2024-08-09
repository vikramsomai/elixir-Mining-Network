package com.somai.elixirMiningNetwork;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {
    MaterialButton signupbtn;
    TextView login;
    EditText email,username,password;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
// ...
// Initialize Firebase Auth

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        signupbtn=findViewById(R.id.signupBtn);
        login=findViewById(R.id.loginid);
        email=findViewById(R.id.email);
        password=findViewById(R.id.password);
        username=findViewById(R.id.username);

        mAuth = FirebaseAuth.getInstance();

        SharedPreferences prefs=getSharedPreferences("userData",MODE_PRIVATE);

        String name=prefs.getString("username",null);
        String emails=prefs.getString("email",null);
        String picture=prefs.getString("picture",null);

        if(name!=null){
            Intent intent=new Intent(SignupActivity.this,MainActivity.class);
            intent.putExtra("email",emails);
            intent.putExtra("username",name);
            intent.putExtra("picture",picture);
            startActivity(intent);

        }
        signupbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Intent i =new Intent(SignupActivity.this,MainActivity.class);
//                startActivity(i);
                String edit_email,edit_password,edit_username;
                edit_email=String.valueOf(email.getText()) ;
                edit_password=String.valueOf(password.getText()) ;
                edit_username=String.valueOf(username.getText()) ;
                if(TextUtils.isEmpty(edit_username))
                {
                    Toast.makeText(SignupActivity.this, "Please enter the username", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(TextUtils.isEmpty(edit_email))
                {
                    Toast.makeText(SignupActivity.this, "Please enter the email", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(TextUtils.isEmpty(edit_password))
                {
                    Toast.makeText(SignupActivity.this, "Please enter the password", Toast.LENGTH_SHORT).show();
                    return;
                }

                mAuth.createUserWithEmailAndPassword(edit_email, edit_password)
                        .addOnCompleteListener(SignupActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    FirebaseUser user = mAuth.getCurrentUser();
//                                    Toast.makeText(SignupActivity.this, ""+user.getUid()+"jbdfjgfg  "+user.getProviderId(), Toast.LENGTH_SHORT).show();
                                    if (user != null) {
                                        databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(user.getUid());
                                        String referral=generateReferralCode(user.getUid());
                                        SharedPreferences.Editor editor = getSharedPreferences("userData", MODE_PRIVATE).edit();
                                        editor.putString("email", user.getEmail());
                                        editor.putString("username", edit_username);
                                        editor.putString("userid", mAuth.getUid().toString());
                                        editor.apply();


                                                String email = user.getEmail(); // or your logic to get email

                                        if (edit_username != null && email != null) {
                                            Map<String, Object> updates = new HashMap<>();
                                            updates.put("username", edit_username);
                                            updates.put("email", email);
                                            updates.put("referralCode", referral);
                                            databaseReference.updateChildren(updates)
                                                    .addOnSuccessListener(aVoid -> {
                                                        // Update successful
                                                        Log.d("FirebaseUpdate", "Update successful");
                                                        Intent i=new Intent(SignupActivity.this,ReferralActivity.class);
                                                        startActivity(i);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        // Update failed
                                                        Log.e("FirebaseUpdate", "Update failed", e);
                                                    });
                                        } else {
                                            Log.e("FirebaseUpdate", "Username or email is null");
                                        }
                                    } else {
                                        Log.e("FirebaseUpdate", "User is null");
                                    }



                                } else {
                                    // If sign in fails, display a message to the user.
                                    Log.w(TAG, "createUserWithEmail:failure", task.getException());
                                    Toast.makeText(SignupActivity.this, "Account Found.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

            }
        });
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i=new Intent(SignupActivity.this,LoginActivity.class);
                startActivity(i);
            }
        });
    }
    public static String generateReferralCode(String userId) {
        // Base64 encode the user ID and take the first 6 characters
        return Base64.encodeToString(userId.getBytes(), Base64.NO_WRAP).substring(0, 6);
    }
}