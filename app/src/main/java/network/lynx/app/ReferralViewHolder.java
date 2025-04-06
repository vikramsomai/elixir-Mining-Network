package network.lynx.app;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;



public class ReferralViewHolder extends RecyclerView.ViewHolder {
    ImageView imageView;
    TextView textView;
    public ReferralViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView=itemView.findViewById(R.id.referralimg);
        textView=itemView.findViewById(R.id.referralUsername);
    }

}
