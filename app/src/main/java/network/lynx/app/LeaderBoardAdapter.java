package network.lynx.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.lelloman.identicon.view.IdenticonView;

import java.text.DecimalFormat;
import java.util.List;

public class LeaderBoardAdapter extends RecyclerView.Adapter<LeaderBoardViewHolder> {

    private Context context;
    private List<LeaderBoardModel> items;
    private String currentUid;
    private GradientDrawable currentUserBackground;

    public LeaderBoardAdapter(Context context, List<LeaderBoardModel> items, String currentUid) {
        this.context = context;
        this.items = items;
        this.currentUid = currentUid;
        // Initialize gradient once
        currentUserBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ContextCompat.getColor(context, R.color.card1),
                        ContextCompat.getColor(context, R.color.card1)}
        );
        currentUserBackground.setCornerRadius(25f);
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

        // Reset visibility
        holder.imageView.setVisibility(View.VISIBLE);
        holder.medalImage.setVisibility(View.GONE);
        holder.profilePic.setVisibility(View.GONE);

        // Image loading
        if (model.getImage() != null) {
            Glide.with(context)
                    .load(model.getImage())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(holder.profilePic);
            holder.profilePic.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
        } else {
            holder.imageView.setHash(model.getUsername().hashCode());
            holder.imageView.setVisibility(View.VISIBLE);
        }

        // Set username and coins
        holder.username.setText(model.getUsername());
        holder.coins.setText(formatNumber(model.getCoins()));

        // Highlight top 3 users with medals
        if (position == 0) {
            holder.medalImage.setImageResource(R.drawable.medalgold);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
            holder.profilePic.setVisibility(View.GONE);
        } else if (position == 1) {
            holder.medalImage.setImageResource(R.drawable.silver_medal);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
            holder.profilePic.setVisibility(View.GONE);
        } else if (position == 2) {
            holder.medalImage.setImageResource(R.drawable.bronze_medal);
            holder.medalImage.setVisibility(View.VISIBLE);
            holder.imageView.setVisibility(View.GONE);
            holder.profilePic.setVisibility(View.GONE);
        }

        // Apply gradient background for current user
        if (model.getUid().equals(currentUid)) {
            holder.itemView.setBackground(currentUserBackground);
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<LeaderBoardModel> newItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).getUid().equals(newItems.get(newItemPosition).getUid());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).equals(newItems.get(newItemPosition));
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
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