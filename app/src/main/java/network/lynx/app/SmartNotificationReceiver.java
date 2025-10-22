package network.lynx.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SmartNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "SmartNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int notificationType = intent.getIntExtra("notification_type", -1);
        Log.d(TAG, "Smart notification triggered for type: " + notificationType);

        // Start the smart notification service
        Intent serviceIntent = new Intent(context, SmartNotificationService.class);
        serviceIntent.putExtra("notification_type", notificationType);
        context.startService(serviceIntent);
    }
}
