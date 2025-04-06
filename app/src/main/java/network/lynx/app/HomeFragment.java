package network.lynx.app;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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
import com.lelloman.identicon.view.IdenticonView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;


public class HomeFragment extends Fragment {

    View view;
    TextView username;
    IdenticonView imageView;
    MaterialButton claimbtn;
    MaterialCardView claimtoken;
    GridView gridView;
    ImageView profilePic;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SharedPreferences sharedPreferences;
    String todayDate;
    TextView countStreak,TotalStreak,levelTextView;
    Double totalDailyStreak, totalcoins;
    private RewardedAd mRewardedAd;
    private boolean isAdLoaded = false;
    private Integer currentLevel = 1;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            Window window = getActivity().getWindow();
            View decorView = window.getDecorView();

            // Remove light status bar flag
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            decorView.setSystemUiVisibility(0);



    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
        todayDate = new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime());

        sharedPreferences = getActivity().getSharedPreferences("userData", MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        String userId = sharedPreferences.getString("userid", null);

        if (currentUser != null && userId != null && !userId.isEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
        }

        loadRewardedAd();

        // Initialize views
        username = view.findViewById(R.id.usernameTextView);
        imageView = view.findViewById(R.id.profileCircleImageView);
        claimbtn = view.findViewById(R.id.claimButton);
//        claimtoken = view.findViewById(R.id.claimToken);
        countStreak = view.findViewById(R.id.streakCountReward);
        gridView = view.findViewById(R.id.gridview);
        TotalStreak=view.findViewById(R.id.streakCount);
        levelTextView = view.findViewById(R.id.levelTextView);
        profilePic=view.findViewById(R.id.profilePic);

        // Start the animation
        // Set grid adapter for home options
        String[] optionName = {"Board","Boost", };
        int[] optionImage = { R.drawable.leaderboard,R.drawable.rocket};
        homeAdapater adapter = new homeAdapater(getActivity(), optionName, optionImage);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(optionName[i]=="Board"){
                    Intent intent =new Intent(getActivity(),LeaderBoardActivity.class);
                    startActivity(intent);
                }
                else if(optionName[i]=="Boost"){
                    Toast.makeText(getActivity(), "Boost Comming soon", Toast.LENGTH_SHORT).show();
                }
            }
        });



        // Restore cached username and profile picture for smooth transitions
        String cachedName = sharedPreferences.getString("username", "none");
        username.setText(cachedName);
        String picture = sharedPreferences.getString("profilePicUrl", null);
        if (picture != null) {
            imageView.setEnabled(false);
            profilePic.setEnabled(true);
            Glide.with(this)
                    .load(picture)
                    .centerCrop()
                    .into(profilePic);

        } else {
            if(!cachedName.isEmpty()){
            imageView.setHash(cachedName.hashCode());
            }
        }
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i =new Intent(getActivity(), ProfileEditActivity.class);
                startActivity(i);
            }
        });
        profilePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i =new Intent(getActivity(), ProfileEditActivity.class);
                startActivity(i);
            }
        });
        // If username is missing, fetch from Firebase and animate update
        if ("none".equals(cachedName)) {
            databaseReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String fir_name = snapshot.child("username").getValue(String.class);
                        String profilePic = snapshot.child("profilePicUrl").getValue(String.class);
//                        Glide.with(getContext())
//                                .load(profilePic)
//                                .centerCrop()
//                                .placeholder(R.drawable.user_man)
//                                .into(imageView);
                        if (fir_name != null) {
                            SharedPreferences.Editor editor = getActivity().getSharedPreferences("userData", MODE_PRIVATE).edit();
                            editor.putString("username", fir_name);
                            currentLevel = snapshot.child("level").getValue(Integer.class);
                            if (currentLevel == null) currentLevel = 1;
                            levelTextView.setText("Level " + String.valueOf(currentLevel));
                            editor.apply();
                            animateTextUpdate(username, fir_name);
                        }
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            });
        }

        checkIfTokenClaimed();

        // Retrieve token streak data and update UI
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Integer streakCount = snapshot.child("streakCount").getValue(Integer.class);
                    totalDailyStreak = snapshot.child("totalStreak").getValue(Double.class);
                    if (streakCount == null) streakCount = 0;
                    if (totalDailyStreak == null) totalDailyStreak = 0.0;
                    countStreak.setText(String.valueOf(totalDailyStreak)+" LYX");
                    TotalStreak.setText(String.valueOf(streakCount));
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        claimbtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("ResourceAsColor")
            @Override
            public void onClick(View view) {
                if (isAdLoaded) {
                    showRewardedAd();
                } else {
                    Toast.makeText(getActivity(), "Ad not loaded yet. Please try again in few minutes.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    // Checks if the token has already been claimed for today.
    private void checkIfTokenClaimed() {
        // Immediately check the local cached "lastDate"
        SharedPreferences prefs = getActivity().getSharedPreferences("userData", MODE_PRIVATE);
        String localLastDate = prefs.getString("lastDate", "");
        if (todayDate.equals(localLastDate)) {
            // Immediately show "Claimed"
            claimbtn.setText("Claimed");
            claimbtn.setEnabled(false);
//            claimtoken.setRadius(20);
        } else {
            claimbtn.setText("Claim");
            claimbtn.setEnabled(true);
        }

        // Then query Firebase to update (if necessary)
        databaseReference.child("lastDate").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String firebaseLastDate = snapshot.getValue(String.class);
                if (todayDate.equals(firebaseLastDate)) {
                    claimbtn.setText("Claimed");
                    claimbtn.setEnabled(false);
//                    claimtoken.setRadius(20);
                    // Optionally, update local cache with the latest value
                    SharedPreferences.Editor editor = getActivity().getSharedPreferences("userData", MODE_PRIVATE).edit();
                    editor.putString("lastDate", firebaseLastDate);
                    editor.apply();
                } else {
                    claimbtn.setText("Claim");
                    claimbtn.setEnabled(true);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to check token claim status", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void animateButtonUpdate(MaterialButton button, String newText, boolean isEnabled) {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(300);
        fadeOut.setFillAfter(true);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(300);
        fadeIn.setFillAfter(true);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                button.setText(newText);
                button.setEnabled(isEnabled);
                button.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
        });

        button.startAnimation(fadeOut);
    }


    // Helper method to determine if today is the next day after lastDate.
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

    // Load rewarded ad.
    private void loadRewardedAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(getActivity(), "ca-app-pub-1396109779371789/7595812194", adRequest, new RewardedAdLoadCallback() {
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

    // Show rewarded ad; on reward, call claimToken().
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
            mRewardedAd.show(requireActivity(), new OnUserEarnedRewardListener() {
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

    // Claim token logic: update user's streak, total streak, and coins.
    private void claimToken() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Fetch current values from Firebase.
                String lastDate = snapshot.child("lastDate").getValue(String.class);
                Integer streakCount = snapshot.child("streakCount").getValue(Integer.class);
                Double totalDailyStreak = snapshot.child("totalStreak").getValue(Double.class);
                Double totalcoins = snapshot.child("totalcoins").getValue(Double.class);


                Integer referrals = snapshot.child("referrals").getValue(Integer.class);
                if (referrals == null) referrals = 0;


                // Initialize defaults if null.
                if (streakCount == null) streakCount = 0;
                if (totalDailyStreak == null) totalDailyStreak = 0.0;
                if (totalcoins == null) totalcoins = 0.0;

                // Check if the user has already claimed today.
                if (todayDate.equals(lastDate)) {
                    Toast.makeText(getActivity(), "You have already claimed your reward today.", Toast.LENGTH_SHORT).show();
                    claimbtn.setEnabled(false);
                    return;
                }

                // Always increase the streak count by 1 (even if there was a gap).
                int newStreak = streakCount + 1;

                // Calculate daily reward based on the new streak.
                double dailyReward = newStreak * 5.0;

                // Update totals.
                totalDailyStreak += dailyReward; // Tracking total streak rewards.
                double newTotalCoins = totalcoins + dailyReward; // Add the daily reward.

                // Update UI.
                animateTextUpdate(countStreak, String.valueOf(newStreak));

                int newLevel = LevelSystem.getNewLevel(
                        newTotalCoins,  // Convert to int
                        referrals
                );

                // Prepare updates map
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastDate", todayDate);
                updates.put("streakCount", newStreak);
                updates.put("totalStreak", totalDailyStreak);
                updates.put("totalcoins", newTotalCoins);
                updates.put("level", newLevel);

                if (newLevel > currentLevel) {
                    updates.put("level", newLevel);
                    currentLevel = newLevel;
                    requireActivity().runOnUiThread(() -> {
                        levelTextView.setText("Level " + newLevel);
                        showLevelUpDialog(newLevel);
                    });
                }

                claimbtn.setEnabled(false);
                TotalStreak.setText(String.valueOf(newStreak));
                databaseReference.updateChildren(updates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getActivity(), "Streak updated! Current streak: " + newStreak +
                                ", Reward: " + dailyReward + " coins", Toast.LENGTH_SHORT).show();
                    } else {
                        Exception e = task.getException();
                        Toast.makeText(getActivity(), "Failed to update streak: " +
                                (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                        Log.e("ClaimToken", "Update failed", e);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Failed to update streak", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void showLevelUpDialog(int newLevel) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Level Up! 🎉")
                .setMessage("Congratulations! You've reached Level " + newLevel +
                        "\n\nNew Benefits:" +
                        "\n• " + LevelSystem.levels.get(newLevel-1).miningBonus + "% Mining Bonus")
                .setPositiveButton("Awesome!", null)
                .show();
    }

//    private void animateLevelUp() {
//        Animation scaleUp = AnimationUtils.loadAnimation(getContext(), R.anim.Sca);
//        Animation rotate = AnimationUtils.loadAnimation(getContext(), R.anim.rotate);
//
//        levelTextView.startAnimation(scaleUp);
//        levelTextView.startAnimation(rotate);
//    }

    // Helper method to animate TextView updates for smooth transitions.
    private void animateTextUpdate(final TextView textView, final String newText) {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(150);
        fadeOut.setFillAfter(true);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(150);
        fadeIn.setFillAfter(true);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }
            @Override
            public void onAnimationEnd(Animation animation) {
                textView.setText(newText);
                textView.startAnimation(fadeIn);
            }
            @Override
            public void onAnimationRepeat(Animation animation) { }
        });
        textView.startAnimation(fadeOut);
    }
}
