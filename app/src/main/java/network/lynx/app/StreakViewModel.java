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
    private String currentUserId = null;

    public LiveData<Integer> getStreak() {
        return streak;
    }

    public void fetchStreakFromFirebase(String userId) {
        // FIX: Always fetch if userId changes (different user logged in)
        if (userId == null || userId.isEmpty()) return;

        // Force refetch if different user
        if (!userId.equals(currentUserId)) {
            currentUserId = userId;
            streak.setValue(null); // Clear old value
        } else if (streak.getValue() != null) {
            // Same user, already have value
            return;
        }

        FirebaseDatabase.getInstance().getReference("streaks").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Integer streakValue = snapshot.getValue(Integer.class);
                        streak.setValue(streakValue != null ? streakValue : 0);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        streak.setValue(0);
                    }
                });
    }

    /**
     * Clear cached data - call on logout
     */
    public void clearCache() {
        currentUserId = null;
        streak.setValue(null);
    }
}

