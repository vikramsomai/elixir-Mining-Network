package com.somai.elixirMiningNetwork;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReferralFragment extends Fragment {

   TextView referralCode,totalreferral;
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

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                String refer=snapshot.child("referralCode").getValue(String.class);
//                Integer referCount=snapshot.child("referralCount").getValue(Integer.class);
                if (snapshot.exists()) {
                    String refer = snapshot.child("referralCode").getValue(String.class);
                    Integer referCount = snapshot.child("referralCount").getValue(Integer.class);
                    if (referCount == null) {
                        referCount = 0;
                    }
                    totalreferral.setText(String.valueOf(referCount));

                    referralCode.setText("Code: " + refer);
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }

        });

        return view;
    }
}