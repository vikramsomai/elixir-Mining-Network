package com.example.patra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.app.Service;

public class CounterService extends Service {

    private final IBinder binder = new LocalBinder();
    private double count = 0.0000;
    private final double increment = 0.0001;
    private final Handler handler = new Handler();
    private final long updateInterval = 100L; // 100 milliseconds
    private long elapsedTime = 20L;
    public final long maxTime = 12*60* 60 * 1000L; // 5 minutes in milliseconds
    public boolean isRunning = false;
    private SharedPreferences sharedPreferences;

    private final Runnable updateCounterRunnable = new Runnable() {
        @Override
        public void run() {
            if (elapsedTime < maxTime) {
                count += increment;
                elapsedTime += updateInterval;
                sendBroadcastUpdate();
                handler.postDelayed(this, updateInterval);
            } else {
                stopCounter();
            }
        }
    };

    public class LocalBinder extends Binder {
        CounterService getService() {
            return CounterService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getSharedPreferences("CounterServicePrefs", Context.MODE_PRIVATE);
        createNotificationChannel();
        startForeground(1, createNotification());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START_COUNTER".equals(intent.getAction())) {
            startCounter();
        } else if (intent != null && "STOP_COUNTER".equals(intent.getAction())) {
            stopCounter();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateCounterRunnable);
    }

    private void startCounter() {
        if (!isRunning) {
            isRunning = true;
            count = 0.0000; // Reset count to 0.0000
            elapsedTime = 0L; // Reset elapsed time
            handler.post(updateCounterRunnable);
        }
    }

    private void stopCounter() {
        isRunning = false;
        handler.removeCallbacks(updateCounterRunnable);
        storeCount();
        sendBroadcastUpdate(); // Send final update
    }

    private void storeCount() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        double totalCount = sharedPreferences.getFloat("totalCount", 0.0000f);
        totalCount += count;
        editor.putFloat("totalCount", (float) totalCount);
        editor.apply();
    }

    public double getCount() {
        return count;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        }

        return new NotificationCompat.Builder(this, "CounterServiceChannel")
                .setContentTitle("Counter Service")
                .setContentText("Counter is running")// Ensure this drawable exists
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "CounterServiceChannel",
                    "Counter Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void sendBroadcastUpdate() {
        Intent intent = new Intent("CounterUpdate");
        intent.putExtra("count", count);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
