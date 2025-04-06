package network.lynx.app;

import static java.security.AccessController.getContext;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.lelloman.identicon.view.IdenticonView;

import java.security.AccessControlContext;
import java.text.DecimalFormat;
import java.util.List;

public class LeaderBoardAdapter extends RecyclerView.Adapter<LeaderBoardViewHolder> {

    private Context context;
    private List<LeaderBoardModel> items;
    private String currentUid;
    IdenticonView identiconView;

    public LeaderBoardAdapter(Context context, List<LeaderBoardModel> items, String currentUid) {
        this.context = context;
        this.items = items;
        this.currentUid = currentUid;
    }

    @NonNull
    @Override
    public LeaderBoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.leaderboard_item, parent, false);
        return new LeaderBoardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderBoardViewHolder holder, int position) {
        LeaderBoardModel model = items.get(position);

        // Reset visibility to prevent view recycling issues
        holder.imageView.setVisibility(View.VISIBLE);
        holder.medalImage.setVisibility(View.GONE);
        holder.profilePic.setVisibility(View.GONE);
//        identiconView.setHash();

        // Always set a default avatar to avoid missing images
        if (model.getImage() !=null) {
            holder.imageView.setHash(model.getUsername().hashCode());
            //            holder.imageView.setEnabled(false);
//            Glide.with(holder.itemView.getContext())
//                    .load(model.getImage())
//                    .centerCrop()
//                    .into(holder.profilePic);
        } else {
            holder.imageView.setHash(model.getUsername().hashCode());

        }
        // Set username and coins
        holder.username.setText(model.getUsername());
        holder.coins.setText(formatNumber(model.getCoins()));

        // Highlight top 3 users with medals
        if (position == 0) {
            holder.medalImage.setImageResource(R.drawable.medalgold);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
        } else if (position == 1) {
            holder.medalImage.setImageResource(R.drawable.silver_medal);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
        } else if (position == 2) {
            holder.medalImage.setImageResource(R.drawable.bronze_medal);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
        }

        // Apply gradient background for the current user
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ContextCompat.getColor(holder.itemView.getContext(), R.color.card1),
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.card3)}
        );
        gradientDrawable.setCornerRadius(25f);

        if (model.getUid().equals(currentUid)) {
            holder.itemView.setBackground(gradientDrawable);
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));
        }
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    public static String formatNumber(Double number) {
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        if (number >= 1_000_000) {
            return decimalFormat.format(number / 1_000_000.0) + "M";
        } else if (number >= 1_000) {
            return decimalFormat.format(number / 1_000.0) + "K";
        } else {
            return String.valueOf(number);
        }
    }

}