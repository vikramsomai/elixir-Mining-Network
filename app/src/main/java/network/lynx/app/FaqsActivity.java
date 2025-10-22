package network.lynx.app;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FaqsActivity extends AppCompatActivity {
    private static final String TAG = "FaqsActivity";
    private RecyclerView recyclerViewFaqs;
    private FaqAdapter faqAdapter;
    private List<FaqItem> faqItems;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_faqs);

            initializeViews();
            setupToolbar();
            setupRecyclerView();
            populateFaqData();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error loading FAQs", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeViews() {
        recyclerViewFaqs = findViewById(R.id.recyclerViewFaqs);
        toolbar = findViewById(R.id.toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setTitle("Frequently Asked Questions");
            }
        }
    }

    private void setupRecyclerView() {
        recyclerViewFaqs.setLayoutManager(new LinearLayoutManager(this));
        faqItems = new ArrayList<>();
        faqAdapter = new FaqAdapter(faqItems, this);
        recyclerViewFaqs.setAdapter(faqAdapter);
    }

    private void populateFaqData() {
        try {
            faqItems.clear();

            // General Questions
            faqItems.add(new FaqItem(
                    "What is Lynx Network?",
                    "Lynx Network is a visionary platform with a clear goal: to build our own high-performance blockchain. Our journey begins by building a strong, engaged user community through social features, rewards, and referrals. This community will form the foundation for launching a scalable, secure, and decentralized blockchain network — built for real-world use, powered by the people. Future plans include AI integration, DePIN support, and governance by users."
            ));

            faqItems.add(new FaqItem("How do I get started?",
                    "Just download the app, create your account, and explore! You can immediately begin earning rewards through referrals, check-ins, and engagement tasks."));

            // Rewards
            faqItems.add(new FaqItem("How can I earn rewards?",
                    "Earn through daily mining, referring friends, completing social media tasks, participating in events, and using boost features. Stay active to maximize your rewards!"));

            faqItems.add(new FaqItem("What are boost features?",
                    "Boosts increase your earning rate. You can get them by referring users, completing social media tasks, and participating in daily challenges or promotional events."));

            // Referrals
            faqItems.add(new FaqItem("How does the referral program work?",
                    "Share your referral code. When users join with your code, both of you receive rewards. You earn 20% commission on their activity."));

            faqItems.add(new FaqItem("How do I track my referral earnings?",
                    "Track referrals, earnings, and boost impact in the Referral section. View active vs inactive users and share tools easily."));

            // Security
            faqItems.add(new FaqItem("Is my data secure?",
                    "Yes. We use encryption, secure authentication, and routine audits to protect your data. We never share personal data without consent."));

            faqItems.add(new FaqItem("How do you protect my account?",
                    "We use Firebase for secure login, detect fraud, and monitor activity. Always use a strong password and report suspicious behavior."));

            // Support
            faqItems.add(new FaqItem("What if I encounter technical issues?",
                    "Check your internet, restart the app, update to the latest version, and clear cache. If the issue remains, contact support."));

            faqItems.add(new FaqItem("Which devices are supported?",
                    "Lynx Network supports Android 6.0+ and will support iOS 12.0+ soon. Optimized for both phones and tablets."));

            // Future Plans
            faqItems.add(new FaqItem("What's planned for the future?",
                    "Upcoming features include UI/UX improvements, analytics, community governance, and cutting-edge tech like blockchain, AI, and DePIN (Decentralized Physical Infrastructure Networks)."));

            faqItems.add(new FaqItem("Will there be blockchain integration?",
                    "Yes. Lynx Network is planning to build its own high-performance blockchain, inspired by modern platforms like Solana. Our future blockchain will support decentralized identity, reward systems, smart contracts, and governance. All implementations will follow regulations and prioritize scalability, speed, and user security."));

            faqItems.add(new FaqItem("Will the app use AI?",
                    "Currently no, but we plan to use AI for fraud detection, smart recommendations, and automated support to improve the user experience."));

            faqItems.add(new FaqItem("What is DePIN and how will it be used?",
                    "DePIN connects real-world activity to rewards. In the future, your location, movement, and real-life engagement may help you earn bonuses and unlock features."));

            faqItems.add(new FaqItem("Why stay connected with Lynx?",
                    "Early users will benefit from new features, token launches, and community growth. Stay active, share the app, and grow with us!"));

            // Community & Policies
            faqItems.add(new FaqItem("How can I connect with the community?",
                    "Join in-app social spaces, follow us on social media, attend events, and refer friends to grow your network."));

            faqItems.add(new FaqItem("How do I contact support?",
                    "You can reach us via in-app chat, email (support@lynxnetwork.app), forums, and social channels. Response time is typically within 24 hours."));

            faqItems.add(new FaqItem("What are the platform rules?",
                    "Respect others, avoid spam/fraud, follow laws, and use the platform responsibly. Violations may lead to account bans."));

            faqItems.add(new FaqItem("How often is the app updated?",
                    "We update regularly to add features, fix bugs, improve security, and ensure performance. Keep auto-updates enabled for the best experience!"));

            if (faqAdapter != null) {
                faqAdapter.notifyDataSetChanged();
            }

            Log.d(TAG, "FAQ data populated successfully with " + faqItems.size() + " items");

        } catch (Exception e) {
            Log.e(TAG, "Error populating FAQ data", e);
            Toast.makeText(this, "Error loading FAQ content", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "FaqsActivity resumed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "FaqsActivity destroyed");
    }
}
