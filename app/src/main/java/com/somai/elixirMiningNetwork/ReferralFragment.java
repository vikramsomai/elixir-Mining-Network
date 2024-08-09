package com.somai.elixirMiningNetwork;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ReferralFragment extends Fragment {

   TextView referralCode,totalreferral;
   ImageView copyRef;
   String refer;
    private DatabaseReference databaseReference;
    private SharedPreferences sharedPreferences;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
       View view;
       view=inflater.inflate(R.layout.fragment_referral, container, false);
        sharedPreferences = this.getActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString("userid", null);
        databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        referralCode=view.findViewById(R.id.referralCode);
        totalreferral=view.findViewById(R.id.referralcount);
        copyRef=view.findViewById(R.id.copyref);



        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                String refer=snapshot.child("referralCode").getValue(String.class);
//                Integer referCount=snapshot.child("referralCount").getValue(Integer.class);
                if (snapshot.exists()) {
                    refer = snapshot.child("referralCode").getValue(String.class);
                    Integer referCount = snapshot.child("referralCount").getValue(Integer.class);
                    if (referCount == null) {
                        referCount = 0;
                    }
                    totalreferral.setText(String.valueOf(referCount));

                    referralCode.setText(refer);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
//                 copyToClipboard(refer);
            }

        });
        // Initialize Firebase Database reference
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("referrerId");

// Read the data at the "referrerId" location
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Iterate through all children under "referrerId"
                    for (DataSnapshot referrerSnapshot : dataSnapshot.getChildren()) {
                        // Get the refer_username value for each referrer
                        String referUsername = referrerSnapshot.child("refer_username").getValue(String.class);
                        if (referUsername != null) {

                        }
                    }
                } else {

                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle possible errors
                System.err.println("Error: " + databaseError.getMessage());
            }
        });

        copyRef.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            copyToClipboard(refer);
            Toast.makeText(getActivity(), "Referral code copied", Toast.LENGTH_SHORT).show();
        }
    });
        return view;
    }
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Unique ID", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }
}