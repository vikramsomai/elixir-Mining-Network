package network.lynx.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FaqAdapter extends RecyclerView.Adapter<FaqAdapter.FaqViewHolder> {

    private List<FaqItem> faqItems;
    private Context context;

    public FaqAdapter(List<FaqItem> faqItems, Context context) {
        this.faqItems = faqItems;
        this.context = context;
    }

    @NonNull
    @Override
    public FaqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_faq, parent, false);
        return new FaqViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FaqViewHolder holder, int position) {
        FaqItem faqItem = faqItems.get(position);

        holder.tvQuestion.setText(faqItem.getQuestion());
        holder.tvAnswer.setText(faqItem.getAnswer());

        // Set initial state
        boolean isExpanded = faqItem.isExpanded();
        holder.layoutAnswer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.ivExpand.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);

        // Set click listener
        holder.layoutQuestion.setOnClickListener(v -> {
            // Toggle expanded state
            faqItem.setExpanded(!faqItem.isExpanded());

            // Show/hide answer with animation
            if (faqItem.isExpanded()) {
                expandItem(holder);
            } else {
                collapseItem(holder);
            }

            notifyItemChanged(position);
        });
    }

    private void expandItem(FaqViewHolder holder) {
        // Change icon
        holder.ivExpand.setImageResource(R.drawable.ic_expand_less);

        // Show answer with animation
        holder.layoutAnswer.setVisibility(View.VISIBLE);
        Animation slideDown = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left);
        holder.layoutAnswer.startAnimation(slideDown);
    }

    private void collapseItem(FaqViewHolder holder) {
        // Change icon
        holder.ivExpand.setImageResource(R.drawable.ic_expand_more);

        // Hide answer with animation
        Animation slideUp = AnimationUtils.loadAnimation(context, android.R.anim.slide_out_right);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                holder.layoutAnswer.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        holder.layoutAnswer.startAnimation(slideUp);
    }

    @Override
    public int getItemCount() {
        return faqItems.size();
    }

    public static class FaqViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuestion, tvAnswer;
        ImageView ivExpand;
        LinearLayout layoutQuestion, layoutAnswer;

        public FaqViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestion = itemView.findViewById(R.id.tvQuestion);
            tvAnswer = itemView.findViewById(R.id.tvAnswer);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            layoutQuestion = itemView.findViewById(R.id.layoutQuestion);
            layoutAnswer = itemView.findViewById(R.id.layoutAnswer);
        }
    }
}
