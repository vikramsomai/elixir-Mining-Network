package network.lynx.app;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lelloman.identicon.view.IdenticonView;

public class LeaderBoardViewHolder extends RecyclerView.ViewHolder {
    ImageView  medalImage,profilePic;
    TextView coins, username;
    IdenticonView imageView;

    public LeaderBoardViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.lead_img);
        profilePic=itemView.findViewById(R.id.profilePic);
        medalImage = itemView.findViewById(R.id.medal_image);
        coins = itemView.findViewById(R.id.lead_coins);
        username = itemView.findViewById(R.id.lead_username);
    }
}