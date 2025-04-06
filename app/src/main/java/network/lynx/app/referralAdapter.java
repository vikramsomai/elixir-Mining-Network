package network.lynx.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import java.util.List;



public class referralAdapter extends RecyclerView.Adapter<ReferralViewHolder> {

   Context context;
   List<ReferralModel> item;
    referralAdapter(Context context, List<ReferralModel> item){
            this.context=context;
            this.item=item;
    }
    @NonNull
    @Override
    public ReferralViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReferralViewHolder(LayoutInflater.from(context).inflate(R.layout.referral_item,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReferralViewHolder holder, int position) {
        holder.textView.setText(item.get(position).getUsername());
        holder.imageView.setImageResource(item.get(position).getImage());
    }

    @Override
    public int getItemCount() {
        return item.size();
    }
}
