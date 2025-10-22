package network.lynx.app;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small helper to centralize Firebase Realtime Database listener management.
 * Tracks listeners so callers can safely remove them and avoid duplicate traffic.
 */
public class FirebaseRealtimeHelper {
    private static final String TAG = "FirebaseRealtimeHelper";
    private final DatabaseReference rootRef;
    private final Map<ValueEventListener, Query> listenerMap = new ConcurrentHashMap<>();

    public FirebaseRealtimeHelper() {
        this.rootRef = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Attach a continuous listener. If limit &gt; 0, uses limitToLast(limit).
     * Returns the same listener instance (for callers who want to keep a ref).
     */
    public ValueEventListener addLimitedListener(@NonNull DatabaseReference ref, int limit, @NonNull ValueEventListener listener) {
        Query q = (limit > 0) ? ref.limitToLast(limit) : ref;
        q.addValueEventListener(listener);
        listenerMap.put(listener, q);
        return listener;
    }

    /**
     * Perform a single-value read. Listener will not be tracked.
     */
    public void fetchOnce(@NonNull DatabaseReference ref, @NonNull ValueEventListener listener) {
        ref.addListenerForSingleValueEvent(listener);
    }

    /**
     * Remove a tracked listener.
     */
    public void removeListener(@NonNull ValueEventListener listener) {
        Query q = listenerMap.remove(listener);
        try {
            if (q != null) q.removeEventListener(listener);
        } catch (Exception e) {
            Log.w(TAG, "removeListener failed", e);
        }
    }

    /**
     * Remove all tracked listeners. Call from lifecycle cleanup.
     */
    public void removeAllListeners() {
        for (Map.Entry<ValueEventListener, Query> e : listenerMap.entrySet()) {
            try {
                Query q = e.getValue();
                if (q != null) q.removeEventListener(e.getKey());
            } catch (Exception ex) {
                Log.w(TAG, "removeAllListeners failed", ex);
            }
        }
        listenerMap.clear();
    }

    /**
     * Convenience for batched updates (fan-out): callers can use rootRef.updateChildren(...)
     */
    public void fanOutUpdate(@NonNull Map<String, Object> updates, DatabaseReference.CompletionListener cb) {
        rootRef.updateChildren(updates, cb);
    }
}
