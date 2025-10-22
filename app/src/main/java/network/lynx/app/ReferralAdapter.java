package network.lynx.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import network.lynx.app.R;
import network.lynx.app.ReferralInfo;
import network.lynx.app.ReferralPingManager;

public class ReferralAdapter extends RecyclerView.Adapter<ReferralAdapter.ReferralViewHolder> {

    private final List<ReferralInfo> referralList;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public ReferralAdapter(List<ReferralInfo> referralList) {
        this.referralList = referralList;
    }

    @NonNull
    @Override
    public ReferralViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.referral_item, parent, false);
        return new ReferralViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReferralViewHolder holder, int position) {
        ReferralInfo referral = referralList.get(position);
        Context context = holder.itemView.getContext();

        // Handle null or empty username
        String username = referral.getUsername();
        if (username == null || username.isEmpty()) {
            username = "Unknown User";
        }
        holder.userName.setText(username);

        // Handle date formatting
        long joinDate = referral.getJoinDate();
        if (joinDate <= 0) {
            holder.joinDate.setText("Recently joined");
        } else {
            holder.joinDate.setText("Joined " + dateFormat.format(new Date(joinDate)));
        }

        // Handle commission display
        double commission = referral.getTotalCommission();
        holder.totalEarned.setText(String.format("+%.2f LYX", commission));

        // Handle active status
        if (referral.isActive()) {
            holder.userStatus.setText("Active");
            holder.userStatus.setTextColor(context.getResources().getColor(R.color.green));
            holder.userStatus.setBackgroundResource(R.drawable.status_background_active);
            holder.pingButton.setVisibility(View.GONE); // Hide ping button for active users
        } else {
            holder.userStatus.setText("Inactive");
            holder.userStatus.setTextColor(context.getResources().getColor(R.color.gold_gradient_end));
            holder.userStatus.setBackgroundResource(R.drawable.status_background_inactive);
            holder.pingButton.setVisibility(View.VISIBLE); // Show ping button for inactive users
        }

        // Set ping button click listener
        holder.pingButton.setOnClickListener(v -> {
            ReferralPingManager.pingReferral(referral.getUserId(), success -> {
                if (success) {
                    Toast.makeText(context, "Ping sent to " + referral.getUsername(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to send ping", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return referralList.size();
    }

    static class ReferralViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar;
        TextView userName, joinDate, totalEarned, userStatus;
        Button pingButton;

        public ReferralViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            joinDate = itemView.findViewById(R.id.joinDate);
            totalEarned = itemView.findViewById(R.id.totalEarned);
            userStatus = itemView.findViewById(R.id.userStatus);
            pingButton = itemView.findViewById(R.id.pingButton);
        }
    }
}