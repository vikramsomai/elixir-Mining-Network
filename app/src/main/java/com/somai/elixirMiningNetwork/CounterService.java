package com.somai.elixirMiningNetwork;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class CounterService extends Service {

    private final IBinder binder = new LocalBinder();
    private double count = 0.0000;
    String userId;
    private final double increment = 0.00278;
    private final Handler handler = new Handler();
    private final long updateInterval = 1000L; // 1 second
    private long elapsedTime = 0L;
    private long countdownTotalTime = 4 * 3600 * 1000L; // 4 hours in milliseconds
    private long countdownRemainingTime = countdownTotalTime;
    public boolean isRunning = false;
    Integer boost=0;
    private SharedPreferences sharedPreferences;
    private DatabaseReference databaseReference;

    private final Runnable updateCounterRunnable = new Runnable() {
        @Override
        public void run() {
            try {

                if (isRunning && elapsedTime < countdownTotalTime) {
                    count += increment;
                    elapsedTime +=updateInterval;
                    sendBroadcastUpdate();
                    updateFirebaseCounter();
                    handler.postDelayed(this, updateInterval);
                } else {
                    stopCounter();
                    if (elapsedTime >= countdownTotalTime) {
                        try {
                            sendMiningCompletedBroadcast();
                        } catch (Exception e) {
                        }
                    }
                }
            }
            catch (Exception e){}
        }
    };

    public class LocalBinder extends Binder {
        CounterService getService() {
            return CounterService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent.getAction();
            if ("START_COUNTER".equals(action)) {
                SharedPreferences sharedPreferences = getSharedPreferences("userData", MODE_PRIVATE);
                String userId = sharedPreferences.getString("userid", null);
                if (userId != null) {
                    startCounter();
                }
            } else if ("STOP_COUNTER".equals(action)) {
                stopCounter(); // Make sure this stops counting and updates Firebase
                stopSelf(); // Stops the service itself
            }
        }
        catch (Exception e){

        }
        return START_NOT_STICKY;
    }


    private void startCounter() {
        try {
            if (!isRunning) {
                SharedPreferences sharedPreferences = getSharedPreferences("userData", MODE_PRIVATE);
                String userId = sharedPreferences.getString("userid", null);

                if (userId != null) {
                    databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId);

                    databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                Double value = dataSnapshot.child("value").getValue(Double.class);
                                Long lastTimestamp = dataSnapshot.child("lastTimestamp").getValue(Long.class);
                                Long savedElapsedTime = dataSnapshot.child("elapsedTime").getValue(Long.class);

                                if (value != null && savedElapsedTime != null && lastTimestamp != null) {
                                    count = value;
                                    long timeGap = System.currentTimeMillis() - lastTimestamp;
                                    long totalElapsedTime = savedElapsedTime + timeGap;
                                    elapsedTime = Math.min(totalElapsedTime, countdownTotalTime);
                                    double incrementDuringGap = (totalElapsedTime / 1000.0) * increment;
                                    count += incrementDuringGap;

                                    databaseReference.child("value").setValue(count);
                                    databaseReference.child("lastTimestamp").setValue(System.currentTimeMillis());
                                    databaseReference.child("elapsedTime").setValue(elapsedTime);
                                } else {
                                    count = 0.0000;
                                    elapsedTime = 0L;
                                }

                                isRunning = true;
                                handler.post(updateCounterRunnable);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            // Handle error
                        }
                    });
                }
            }
        }catch (Exception e){

        }
    }

    private void stopCounter() {

        isRunning = false;
        handler.removeCallbacks(updateCounterRunnable);
        updateFirebaseCounter();
        sendBroadcastUpdate();
        stopSelf();
        if (databaseReference != null) {
            databaseReference.child("coins").setValue(count);
            databaseReference.child("elapsedTime").setValue(0);
        }
    }

    private void sendBroadcastUpdate() {
        try {
            Intent intent = new Intent("CounterUpdate");
            intent.putExtra("count", count);
            intent.putExtra("remainingTime", countdownTotalTime - elapsedTime);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }catch (Exception e){}
    }

    private void sendMiningCompletedBroadcast() {
        try {
            Intent intent = new Intent("MiningCompleted");
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }catch (Exception e){}
    }

    private void updateFirebaseCounter() {
        try{
        if (databaseReference != null) {
            databaseReference.child("value").setValue(count);
            databaseReference.child("lastTimestamp").setValue(System.currentTimeMillis());
            databaseReference.child("elapsedTime").setValue(elapsedTime);
        }}
        catch (Exception e){}
    }

    public double getCount() {
        return count;
    }

    public long getCountdownRemainingTime() {
        return countdownTotalTime - elapsedTime;
    }

    @Override
    public void onDestroy() {
        try {
            saveMiningState();
            super.onDestroy();
        }
        catch (Exception e){}
    }

    private void saveMiningState() {
        try {
            if (databaseReference != null) {
                databaseReference.child("value").setValue(count);
                databaseReference.child("lastTimestamp").setValue(System.currentTimeMillis());
                databaseReference.child("elapsedTime").setValue(elapsedTime);
            }
        }
        catch (Exception e){}
    }
}
