package com.somai.elixirMiningNetwork;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class RewardService extends Service {

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler();
    private static final long REWARD_INTERVAL = 20 * 60 * 1000L; // 20 minutes in milliseconds
    private long remainingTime = REWARD_INTERVAL;
    private boolean isRunning = false;

    private final Runnable rewardRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning && remainingTime > 0) {
                remainingTime -= 1000;
                sendBroadcastUpdate();
                handler.postDelayed(this, 1000);
            } else {
                stopRewardTimer();
                sendRewardAvailableBroadcast();
            }
        }
    };

    public class LocalBinder extends Binder {
        RewardService getService() {
            return RewardService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if ("START_REWARD_TIMER".equals(action)) {
            startRewardTimer();
        } else if ("STOP_REWARD_TIMER".equals(action)) {
            stopRewardTimer();
        }
        return START_NOT_STICKY;
    }

    private void startRewardTimer() {
        if (!isRunning) {
            isRunning = true;
            remainingTime = REWARD_INTERVAL; // Reset the timer to 20 minutes
            handler.post(rewardRunnable);
        }
    }

    private void stopRewardTimer() {
        isRunning = false;
        handler.removeCallbacks(rewardRunnable);
    }

    private void sendBroadcastUpdate() {
        Intent intent = new Intent("RewardUpdate");
        intent.putExtra("remainingTime", remainingTime);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendRewardAvailableBroadcast() {
        Intent intent = new Intent("RewardAvailable");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRewardTimer();
    }
}
