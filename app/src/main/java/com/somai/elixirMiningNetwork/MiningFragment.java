package com.somai.elixirMiningNetwork;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MiningFragment extends Fragment {

    private TextView counterTextView, miningTimer,totalCoins;
    private MaterialCardView startButton;
    private CounterService counterService;
    private boolean isBound = false;
    private double total = 0.0000;
    private RewardedAd mRewardedAd;

    private SharedPreferences prefs;

    private DatabaseReference databaseReference;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                CounterService.LocalBinder binder = (CounterService.LocalBinder) service;
                counterService = binder.getService();
                isBound = true;
                updateUI();
            }
            catch (Exception e){}
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver counterUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (isBound && counterService != null) {
                    double count = intent.getDoubleExtra("count", 0.0000);
                    total = count;
                    counterTextView.setText(String.format("%.4f", total));
                    long remainingTime = intent.getLongExtra("remainingTime", 0);
                    if (counterService.isRunning) {
                        startTimer(remainingTime);
                    } else {
                        miningTimer.setText("00:00:00");
                        startButton.setEnabled(true);
                    }
                }
            }catch (Exception e){}
        }
    };

    private final BroadcastReceiver miningCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                startButton.setEnabled(true);
                Toast.makeText(context, "Mining session completed. You can start a new session.", Toast.LENGTH_SHORT).show();
            }catch (Exception e){}
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mining, container, false);

        counterTextView = view.findViewById(R.id.counterTextView);
        miningTimer = view.findViewById(R.id.miningTimer);
        startButton = view.findViewById(R.id.startButton);
        totalCoins=view.findViewById(R.id.totalcoinsValue);
        prefs = this.getActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);

        String userId = prefs.getString("userid", null);
try {
    databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);
    databaseReference.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            Double totalCoinss=snapshot.child("totalcoins").getValue(Double.class);
            Double coins=snapshot.child("value").getValue(Double.class);
            Double streak=snapshot.child("totalStreak").getValue(Double.class);
            if(coins==null){
                coins=0.00;
            }
            if(streak==null){
                streak=0.00;
            }
            totalCoins.setText(String.format("%.4f", coins+streak));
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {

        }
    });
    if (userId != null) {


        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Double value = dataSnapshot.child("value").getValue(Double.class);
//                         coins = dataSnapshot.child("coins").getValue(Double.class);
//                         totals = dataSnapshot.child("totalcoins").getValue(Double.class);
                    Long lastTimestamp = dataSnapshot.child("lastTimestamp").getValue(Long.class);
                    Long savedElapsedTime = dataSnapshot.child("elapsedTime").getValue(Long.class);

//                        totalCoins.setText(String.valueOf(totals));
                    if (value != null && savedElapsedTime != null && lastTimestamp != null) {
                        total = value;
                        counterTextView.setText(String.format("%.4f", total));

                        long timeGap = System.currentTimeMillis() - lastTimestamp;
                        long totalElapsedTime = savedElapsedTime + timeGap;
                        long remainingTime = 4 * 60 * 60 * 1000L - totalElapsedTime; // 4 hours in milliseconds

                        if (remainingTime > 0) {
                            Intent intent = new Intent(getActivity(), CounterService.class);
                            intent.setAction("START_COUNTER");
                            intent.putExtra("userId", userId); // Replace with actual userId
                            getActivity().startService(intent);
                            startButton.setEnabled(false);
                        } else {
                            miningTimer.setText("00:00:00");
                            startButton.setEnabled(true);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    } else {
        Toast.makeText(getContext(), "User ID not available", Toast.LENGTH_SHORT).show();
    }
}catch (Exception e){}

        startButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "clciked", Toast.LENGTH_SHORT).show();
                showRewardedAd();
            try {
                if (isBound && counterService != null) {
                    if (counterService.isRunning) {
                        Toast.makeText(getContext(), "Mining already in progress", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent intent = new Intent(getActivity(), CounterService.class);
                        intent.setAction("START_COUNTER");
                        getActivity().startService(intent);
                        startButton.setEnabled(false);
                    }
                } else {
                    Toast.makeText(getContext(), "Service not bound or unavailable", Toast.LENGTH_SHORT).show();
                }
            }catch (Exception e){}
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            IntentFilter filter = new IntentFilter("CounterUpdate");
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(counterUpdateReceiver, filter);
            IntentFilter completedFilter = new IntentFilter("MiningCompleted");
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(miningCompletedReceiver, completedFilter);
            getContext().bindService(new Intent(getContext(), CounterService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        }
        catch (Exception e){

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (isBound) {
                getContext().unbindService(serviceConnection);
                isBound = false;
            }
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(counterUpdateReceiver);
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(miningCompletedReceiver);
        }catch (Exception e){}
    }

    private void updateUI() {
        try {
            if (counterService != null) {
                counterTextView.setText(String.format("%.4f", counterService.getCount()));
                long remainingTime = counterService.getCountdownRemainingTime();
                if (counterService.isRunning) {
                    startTimer(remainingTime);
                }
            }
        }catch (Exception e){}
    }

    private void startTimer(long remainingTime) {
        try {
            if (remainingTime > 0) {
                miningTimer.setText(String.format("%02d:%02d:%02d",
                        (remainingTime / (1000 * 60 * 60)) % 24,
                        (remainingTime / (1000 * 60)) % 60,
                        (remainingTime / 1000) % 60));
            } else {
//            if (){
//
//            }
//            databaseReference.child("totalcoins").setValue(totals+coins);
                miningTimer.setText("00:00:00");
            }
        }catch (Exception e){}
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
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    loadRewardedAd();
                }
            });

            mRewardedAd.show(getActivity(), new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {

                }
            });
        } else {
            Toast.makeText(getActivity(), "Ad not loaded", Toast.LENGTH_SHORT).show();
        }
    }
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(getActivity(), "ca-app-pub-1396109779371789/9580360289", adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mRewardedAd = null;
//                isAdLoaded = false;
                Toast.makeText(getActivity(), "Failed to load ad", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                mRewardedAd = rewardedAd;
//                isAdLoaded = true;
            }
        });
    }
}
