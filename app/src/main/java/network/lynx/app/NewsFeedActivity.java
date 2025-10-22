package network.lynx.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class NewsFeedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news_feed);

        // Setup toolbar
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Setup RecyclerView
        RecyclerView recyclerView = findViewById(R.id.newsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new NewsAdapter(getSampleNewsData()));
    }

    private List<NewsItem> getSampleNewsData() {
        List<NewsItem> newsItems = new ArrayList<>();
// "ANNOUNCEMENT",UPDATE,PARTNERSHIP
        newsItems.add(new NewsItem(
                "ANNOUNCEMENT",
                "Lynx Blockchain: Development Roadmap & Beta Access",
                "We're excited to announce that development for Lynx Network’s own blockchain has officially begun! 🛠️\n\nOur upcoming blockchain will be fast, scalable, and optimized for rewards and decentralized governance — inspired by platforms like Solana.\n\nExpected public Testnet launch: Mid-2026 🚀\n\nOnce ready, users will be invited to join the **Beta Testnet Program**, where you can test features early and earn exclusive rewards.\n\nStay tuned for future updates!",
                "July 5, 2025",
                R.drawable.blockchain
        ));
        newsItems.add(new NewsItem(
                "UPDATE",
                "Mobile App Version 2.0 Launch",
                "The latest update includes a redesigned UI, improved security features, and faster transaction processing.",
                "March 15, 2025",
                R.drawable.gradient_background
        ));



        return newsItems;
    }
}