package network.lynx.app;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import network.lynx.app.R;
import network.lynx.app.CommissionInfo;

public class CommissionAdapter extends RecyclerView.Adapter<CommissionAdapter.CommissionViewHolder> {

    private final List<CommissionInfo> commissionList;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public CommissionAdapter(List<CommissionInfo> commissionList) {
        this.commissionList = commissionList;
    }

    @NonNull
    @Override
    public CommissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.commison_layout, parent, false);
        return new CommissionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommissionViewHolder holder, int position) {
        CommissionInfo commission = commissionList.get(position);

        // Set commission type
        String typeText;
        int iconResId;

        switch (commission.getType()) {
            case "mining_tier2":
                typeText = "Tier 2 Mining Commission";
                iconResId = R.drawable.mining;
                break;
            case "mining":
            default:
                typeText = "Mining Commission";
                iconResId = R.drawable.mining;
                break;
        }

        holder.commissionType.setText(typeText);
        holder.commissionTypeIcon.setImageResource(iconResId);

        // Set other details
        holder.commissionFrom.setText("From: " + commission.getFromUsername());
        holder.commissionAmount.setText(String.format("+%.4f LYX", commission.getAmount()));
        holder.commissionDate.setText(dateFormat.format(new Date(commission.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return commissionList.size();
    }

    static class CommissionViewHolder extends RecyclerView.ViewHolder {
        ImageView commissionTypeIcon;
        TextView commissionType, commissionFrom, commissionAmount, commissionDate;

        public CommissionViewHolder(@NonNull View itemView) {
            super(itemView);
            commissionTypeIcon = itemView.findViewById(R.id.commissionTypeIcon);
            commissionType = itemView.findViewById(R.id.commissionType);
            commissionFrom = itemView.findViewById(R.id.commissionFrom);
            commissionAmount = itemView.findViewById(R.id.commissionAmount);
            commissionDate = itemView.findViewById(R.id.commissionDate);
        }
    }
}