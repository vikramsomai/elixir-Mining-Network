package network.lynx.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

public class StreakViewModel extends ViewModel {
    private final MutableLiveData<Integer> streak = new MutableLiveData<>();

    public LiveData<Integer> getStreak() {
        return streak;
    }

    public void fetchStreakFromFirebase(String userId) {
        // Avoid re-fetching if already available
        if (streak.getValue() != null) return;

        FirebaseDatabase.getInstance().getReference("streaks").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Integer streakValue = snapshot.getValue(Integer.class);
                        if (streakValue != null) {
                            streak.setValue(streakValue);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Optionally handle error
                    }
                });
    }
}

