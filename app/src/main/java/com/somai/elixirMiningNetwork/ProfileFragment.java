package com.somai.elixirMiningNetwork;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;


public class ProfileFragment extends Fragment {


TextView editProfile,username;
MaterialCardView logout;
ImageView imageView,telegram;
    private CounterService counterService;

    GoogleSignInClient mGoogleSignInClient;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_profile, container, false);
//      editProfile=view.findViewById(R.id.editProfile);
      logout=view.findViewById(R.id.logoutBtn);
        SharedPreferences prefs=this.getActivity().getSharedPreferences("userData", MODE_PRIVATE);

        String name=prefs.getString("username",null);
        if(name==null){
            name="knownk";
        }
        String email=prefs.getString("email",null);
        String picture=prefs.getString("picture",null);

        imageView=view.findViewById(R.id.profileCircleImageView);
        username=view.findViewById(R.id.usernameTextView);
//        telegram=view.findViewById(R.id.telegram);
        username.setText(name);
        if(picture!=null){
            Glide
                    .with(this)
                    .load(picture)
                    .centerCrop()
                    .placeholder(R.drawable.user_man)
                    .into(imageView);
        }
        else{
            Glide
                    .with(this)
                    .load(R.drawable.user_man)
                    .centerInside()
                    .placeholder(R.drawable.user_man)
                    .into(imageView);
        }
//      editProfile.setOnClickListener(new View.OnClickListener() {
//          @Override
//          public void onClick(View view) {
//              Toast.makeText(getContext(), "Coming Soon..", Toast.LENGTH_SHORT).show();
//          }
//      });

      logout.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {

              if (mGoogleSignInClient != null) {
                  mGoogleSignInClient.signOut().addOnCompleteListener(requireActivity(), new OnCompleteListener<Void>() {
                      @Override
                      public void onComplete(@NonNull Task<Void> task) {
                          // Handle sign-out success
                          Toast.makeText(requireActivity(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                          // Redirect to login activity
                          SharedPreferences prefs = getActivity().getSharedPreferences("userData", MODE_PRIVATE);
                          SharedPreferences.Editor editor = prefs.edit();
                          editor.clear(); // Or editor.remove("userid");
                          editor.apply();
                          Intent stopIntent = new Intent(getActivity(), CounterService.class);
                          stopIntent.setAction("STOP_COUNTER");
                          getActivity().startService(stopIntent);

                          Intent intent = new Intent(requireActivity(), LoginActivity.class);
                          startActivity(intent);
                          requireActivity().finish();
                      }
                  });
              } else {
                  Toast.makeText(requireActivity(), "Error: GoogleSignInClient not initialized", Toast.LENGTH_SHORT).show();
              }

          }
      });
//      telegram.setOnClickListener(new View.OnClickListener() {
//          @Override
//          public void onClick(View view) {
//            openTelegram();
//          }
//      });

       return  view;
    }
    private void openTelegram() {
        // Replace with your Telegram channel or chat link
        String telegramUrl = "https://t.me/elixir_network_annoucement";

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(telegramUrl));

        // Check if the Telegram app is installed
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Optional: Handle case where Telegram app is not installed
            // For example, you might open the Play Store or show a message
        }
    }
}