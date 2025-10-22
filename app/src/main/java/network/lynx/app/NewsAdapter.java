package network.lynx.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<NewsItem> newsItems;

    public NewsAdapter(List<NewsItem> newsItems) {
        this.newsItems = newsItems;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.news_layout, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsItem item = newsItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return newsItems.size();
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        private final ImageView newsImage;
        private final TextView newsCategory;
        private final TextView newsTitle;
        private final TextView newsDescription;
        private final TextView newsDate;
        private final TextView readMoreButton;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            newsImage = itemView.findViewById(R.id.newsImage);
            newsCategory = itemView.findViewById(R.id.newsCategory);
            newsTitle = itemView.findViewById(R.id.newsTitle);
            newsDescription = itemView.findViewById(R.id.newsDescription);
            newsDate = itemView.findViewById(R.id.newsDate);
            readMoreButton = itemView.findViewById(R.id.readMoreButton);
        }

        public void bind(NewsItem item) {
            newsImage.setImageResource(item.getImageRes());
            newsCategory.setText(item.getCategory());
            newsTitle.setText(item.getTitle());
            newsDescription.setText(item.getDescription());
            newsDate.setText(item.getDate());

            readMoreButton.setOnClickListener(v -> {
                // Handle read more action
            });
        }
    }
}