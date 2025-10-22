package network.lynx.app;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ToastUtils {

    public static void showCustomToast(Context context, String message) {
        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.custom_toast, null);

        // Set message
        TextView text = layout.findViewById(R.id.toast_text);
        text.setText(message);

        // Set icon
//        icon.setImageResource(iconResId);

        // Create and configure toast
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 100);

        toast.show();
    }

    // Overload for common toast types
    public static void showSuccess(Context context, String message) {
        showCustomToast(context, message);
    }

    public static void showError(Context context, String message) {
        showCustomToast(context, message);
    }

    public static void showInfo(Context context, String message) {
        showCustomToast(context, message);
    }

}