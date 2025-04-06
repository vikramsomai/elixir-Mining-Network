package network.lynx.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;



public class ReferralFragment extends Fragment {

    private TextView referralCode, totalreferral;
    private ImageView copyRef;
    private RecyclerView recyclerView;
    private String refer;
    private List<ReferralModel> items;
    private DatabaseReference databaseReference;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_referral, container, false);
        sharedPreferences = requireActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString("userid", null);
        if(userId == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return view;
        }
        // Use "users" (lowercase) for Firebase reference if your rules are set that way.
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);
        referralCode = view.findViewById(R.id.referralCode);
        totalreferral = view.findViewById(R.id.referralcount);
        copyRef = view.findViewById(R.id.copyref);
        recyclerView = view.findViewById(R.id.recyclerView);
        items = new ArrayList<>();

        // Restore cached referral data immediately for smooth UI display.
        restoreReferralData();

        // Listen for referral data from Firebase.
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String newRefer = snapshot.child("referralCode").getValue(String.class);
                    Integer referCount = snapshot.child("referralCount").getValue(Integer.class);
                    if (referCount == null) {
                        referCount = 0;
                    }
                    // Animate update if referral code has changed.
                    if (newRefer != null && !newRefer.equals(refer)) {
                        refer = newRefer;
                        animateTextUpdate(referralCode, refer);
                        cacheReferralData("referralCode", refer);
                    }
                    animateTextUpdate(totalreferral, String.valueOf(referCount));
                    cacheReferralData("referralCount", String.valueOf(referCount));

                    // Process referrer IDs for the RecyclerView if available.
                    items.clear();
                    if (snapshot.child("referrerId").exists()){
                        for (DataSnapshot dataSnapshot : snapshot.child("referrerId").getChildren()){
                            String referUsername = dataSnapshot.child("refer_username").getValue(String.class);
                            if(referUsername != null) {
                                items.add(new ReferralModel(referUsername, R.drawable.user_man));
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load referral data", Toast.LENGTH_SHORT).show();
            }
        });

        copyRef.setOnClickListener(view1 -> {
            copyToClipboard(refer);
            Toast.makeText(getActivity(), "Referral code copied", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    // Copies referral code to clipboard.
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Referral Code", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    // Animates the text update using fade-out/fade-in animation.
    private void animateTextUpdate(final TextView textView, final String newText) {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(150);
        fadeOut.setFillAfter(true);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(150);
        fadeIn.setFillAfter(true);
        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) { }
            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                textView.setText(newText);
                textView.startAnimation(fadeIn);
            }
            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) { }
        });
        textView.startAnimation(fadeOut);
    }

    // Cache referral data locally.
    private void cacheReferralData(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    // Restore cached referral data for immediate display.
    private void restoreReferralData() {
        String cachedCode = sharedPreferences.getString("referralCode", "Unknown");
        String cachedCount = sharedPreferences.getString("referralCount", "0");
        referralCode.setText(cachedCode);
        totalreferral.setText(cachedCount);
    }
}
