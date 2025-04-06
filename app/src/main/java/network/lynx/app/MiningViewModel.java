package network.lynx.app;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class MiningViewModel extends ViewModel {
    private final MutableLiveData<Double> totalCoinsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> miningRateLiveData = new MutableLiveData<>();
    private DatabaseReference userRef;

    public MiningViewModel() {
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userID);
        fetchUserData();
    }

    public LiveData<Double> getTotalCoins() {
        return totalCoinsLiveData;
    }

    public LiveData<Double> getMiningRate() {
        return miningRateLiveData;
    }

    public void fetchUserData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double totalCoins = snapshot.child("totalcoins").getValue(Double.class);
                Double totalStreak = snapshot.child("totalStreak").getValue(Double.class);
                Integer referralCount = snapshot.child("referralCount").getValue(Integer.class);

                if (totalCoins == null) totalCoins = 0.0;
                if (totalStreak == null) totalStreak = 0.0;
                if (referralCount == null) referralCount = 0;

                double totalBalance = totalCoins;
                double miningRate = (1.0 + (referralCount * 0.10)) * 0.001; // Apply referral bonus

                totalCoinsLiveData.setValue(totalBalance);
                miningRateLiveData.setValue(miningRate);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
}

