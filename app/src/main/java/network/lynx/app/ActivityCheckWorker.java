package network.lynx.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;

import java.util.concurrent.TimeUnit;

import network.lynx.app.UserActivityTracker;

public class ActivityCheckWorker extends Worker {
    private static final String WORK_NAME = "activity_check_work";

    public ActivityCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        UserActivityTracker.checkAllUsersActivity();
        return Result.success();
    }

    public static void scheduleDaily(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                ActivityCheckWorker.class, 1, TimeUnit.DAYS)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest);
    }
}