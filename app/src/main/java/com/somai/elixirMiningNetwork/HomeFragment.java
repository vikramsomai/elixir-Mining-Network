package com.somai.elixirMiningNetwork;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;


public class HomeFragment extends Fragment {

        View view;
        TextView username;
        ImageView imageView;
        MaterialButton claimbtn;
MaterialCardView airdrop,kyc,wallet,claimtoken;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPreferences;
    String lastDate;
    private String todayDate;
    int streakCount;
    TextView countStreak;
    Double totalDailyStreak,totalcoins;
    private RewardedAd mRewardedAd;
    private boolean isAdLoaded = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MobileAds.initialize(getActivity(), initializationStatus -> {});
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view= inflater.inflate(R.layout.fragment_home, container, false);
        todayDate = new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime());

        sharedPreferences = this.getActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        String userId = sharedPreferences.getString("userid", null);
        if (currentUser != null || !userId.isEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        }
        loadRewardedAd();
        // Inflate the layout for this fragment
        username=view.findViewById(R.id.usernameTextView);
        airdrop=view.findViewById(R.id.airdrop);
        kyc=view.findViewById(R.id.kyc);
        wallet=view.findViewById(R.id.wallet);
        claimtoken=view.findViewById(R.id.claimToken);
        countStreak=view.findViewById(R.id.countStreak);
        claimbtn=view.findViewById(R.id.claimbtn);

        SharedPreferences prefs=this.getActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
        String name=prefs.getString("username",null);
        String picture=prefs.getString("picture",null);
        imageView=view.findViewById(R.id.profileCircleImageView);
        Glide
                .with(this)
                .load(picture)
                .centerCrop()
                .placeholder(R.drawable.default_user)
                .into(imageView);

        username.setText(name.toString());
        final String todayDate = new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime());

        checkIfTokenClaimed();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                                             @Override
                                                             public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                                 String lastDate = snapshot.child("lastDate").getValue(String.class);
                                                                 Integer streakCount = snapshot.child("streakCount").getValue(Integer.class);
                                                                 totalDailyStreak=snapshot.child("totalStreak").getValue(Double.class);
                                                                 totalcoins=snapshot.child("coins").getValue(Double.class);
                                                                 if(totalcoins==null){totalcoins=0.00;}
                                                                 if(totalDailyStreak==null){
                                                                     totalDailyStreak=0.00;
                                                                 }
                                                                 if (lastDate == null || streakCount == null) {
                                                                     // Initialize the values if they do not exist
                                                                     int count = 0;
                                                                     countStreak.setText(String.valueOf(count));
                                                                 }
                                                                 else {
                                                                     countStreak.setText(String.valueOf(streakCount));
                                                                 }

                                                             }

                                                             @Override
                                                             public void onCancelled(@NonNull DatabaseError error) {

                                                             }
                                                         });
                claimbtn.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("ResourceAsColor")
                    @Override
                    public void onClick(View view) {
                        if (isAdLoaded) {
                            showRewardedAd();
                        } else {
                            Toast.makeText(getActivity(), "Ad not loaded yet", Toast.LENGTH_SHORT).show();
                        }

                    }
                });
        airdrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Airdrop coming soon", Toast.LENGTH_SHORT).show();
            }
        });
        kyc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "kyc coming soon", Toast.LENGTH_SHORT).show();
            }
        });
        wallet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.framelayout, new WalletFragment());
                fragmentTransaction.commit();
//                Toast.makeText(getActivity(), "wallet coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }
    private void checkIfTokenClaimed() {
        databaseReference.child("lastDate").addListenerForSingleValueEvent(new ValueEventListener() {
            @SuppressLint("ResourceAsColor")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String lastDate = snapshot.getValue(String.class);
                if (todayDate.equals(lastDate)) {
                    claimbtn.setEnabled(false);
                    claimbtn.setText("Claimed");
                    claimtoken.setRadius(20);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to check token claim status", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private boolean isNextDay(String lastDate, String todayDate) {
        Calendar lastCal = Calendar.getInstance();
        Calendar todayCal = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");

        try {
            lastCal.setTime(format.parse(lastDate));
            todayCal.setTime(format.parse(todayDate));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        lastCal.add(Calendar.DAY_OF_YEAR, 1);
        return todayCal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
                todayCal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR);
    }
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(getActivity(), "ca-app-pub-1396109779371789/8267278610", adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mRewardedAd = null;
                isAdLoaded = false;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                mRewardedAd = rewardedAd;
                isAdLoaded = true;
            }
        });
    }
    private void showRewardedAd() {
        if (mRewardedAd != null) {
            mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    mRewardedAd = null;
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError adError) {
                    Toast.makeText(getActivity(), "Failed to show ad", Toast.LENGTH_SHORT).show();
                    claimToken();
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    loadRewardedAd();
                }
            });

            mRewardedAd.show(getActivity(), new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    claimToken();
                }
            });
        } else {
            Toast.makeText(getActivity(), "Ad not loaded", Toast.LENGTH_SHORT).show();
            claimToken();
        }
    }
    private void claimToken() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String lastDate = snapshot.child("lastDate").getValue(String.class);
                Integer streakCount = snapshot.child("streakCount").getValue(Integer.class);

                if (lastDate == null || streakCount == null) {
                    streakCount = 5;
                } else {
                    if (!lastDate.equals(todayDate)) {
                        if (isNextDay(lastDate, todayDate)) {
                            streakCount += 5;
                        } else {
                            streakCount = 5;
                        }
                    }
                }
                countStreak.setText(String.valueOf(streakCount));
                totalDailyStreak += streakCount;
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastDate", todayDate);
                updates.put("totalStreak", totalDailyStreak);
                updates.put("streakCount", streakCount);
                updates.put("totalcoins", totalDailyStreak + totalcoins);
                claimbtn.setEnabled(false);
                databaseReference.updateChildren(updates);
                Toast.makeText(getActivity(), "Streak updated! Current streak: " + streakCount, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to update streak", Toast.LENGTH_SHORT).show();
            }
        });
    }
}