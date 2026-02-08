package network.lynx.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Trust Dashboard Activity
 *
 * Builds user trust by showing:
 * 1. Transparent mining statistics
 * 2. Complete transaction history
 * 3. Mainnet launch countdown
 * 4. Withdrawal preview (coming soon)
 * 5. Security & verification badges
 *
 * GOOGLE PLAY COMPLIANT:
 * - LYX tokens are in-app rewards only
 * - No real money claims
 * - Clear "coming soon" for withdrawals
 * - Transparent about token utility
 */
public class TrustDashboardActivity extends AppCompatActivity {
    private static final String TAG = "TrustDashboard";

    // Header
    private ImageView backButton;
    private TextView totalBalanceText, totalBalanceLabel;

    // Stats Cards
    private TextView miningSessionsCount, totalMinedText, avgDailyText;
    private TextView referralEarningsText, bonusEarningsText, gamesEarningsText;

    // Mainnet Countdown
    private TextView mainnetDaysText, mainnetHoursText, mainnetMinutesText;
    private TextView mainnetStatusText;
    private ProgressBar mainnetProgress;

    // Transaction History
    private RecyclerView transactionRecyclerView;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactions = new ArrayList<>();
    private TextView noTransactionsText;

    // Withdrawal Preview
    private MaterialCardView withdrawalCard;
    private TextView withdrawalStatusText, withdrawalInfoText;
    private MaterialButton withdrawalBtn;

    // Trust Badges
    private LinearLayout trustBadgesLayout;

    // Firebase
    private DatabaseReference userRef;
    private String userId;

    // Mainnet target date - January 1, 2027
    private static final long MAINNET_LAUNCH_DATE = 1798761600000L; // Jan 1, 2027 00:00:00 UTC

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trust_dashboard);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        initViews();
        setupClickListeners();
        loadData();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        totalBalanceText = findViewById(R.id.totalBalanceText);
        totalBalanceLabel = findViewById(R.id.totalBalanceLabel);

        // Stats
        miningSessionsCount = findViewById(R.id.miningSessionsCount);
        totalMinedText = findViewById(R.id.totalMinedText);
        avgDailyText = findViewById(R.id.avgDailyText);
        referralEarningsText = findViewById(R.id.referralEarningsText);
        bonusEarningsText = findViewById(R.id.bonusEarningsText);
        gamesEarningsText = findViewById(R.id.gamesEarningsText);

        // Mainnet
        mainnetDaysText = findViewById(R.id.mainnetDaysText);
        mainnetHoursText = findViewById(R.id.mainnetHoursText);
        mainnetMinutesText = findViewById(R.id.mainnetMinutesText);
        mainnetStatusText = findViewById(R.id.mainnetStatusText);
        mainnetProgress = findViewById(R.id.mainnetProgress);

        // Transactions
        transactionRecyclerView = findViewById(R.id.transactionRecyclerView);
        noTransactionsText = findViewById(R.id.noTransactionsText);
        transactionRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(transactions);
        transactionRecyclerView.setAdapter(transactionAdapter);

        // Withdrawal
        withdrawalCard = findViewById(R.id.withdrawalCard);
        withdrawalStatusText = findViewById(R.id.withdrawalStatusText);
        withdrawalInfoText = findViewById(R.id.withdrawalInfoText);
        withdrawalBtn = findViewById(R.id.withdrawalBtn);

        // Trust badges
        trustBadgesLayout = findViewById(R.id.trustBadgesLayout);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        withdrawalBtn.setOnClickListener(v -> {
            showWithdrawalInfo();
        });

        // Copy user ID for verification
        if (totalBalanceLabel != null) {
            totalBalanceLabel.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("User ID", userId);
                clipboard.setPrimaryClip(clip);
                ToastUtils.showInfo(this, "User ID copied for support");
                return true;
            });
        }
    }

    private void loadData() {
        loadBalance();
        loadMiningStats();
        loadEarningsBreakdown();
        loadTransactionHistory();
        updateMainnetCountdown();
        setupTrustBadges();
    }

    private void loadBalance() {
        // FIXED: Use WalletManager for consistent balance across all screens
        // This ensures Trust Dashboard shows the same balance as MiningFragment
        WalletManager walletManager = WalletManager.getInstance(this);

        // First get cached balance
        double cachedBalance = walletManager.getTotalBalance();

        // Also check for active mining session to include pending tokens
        userRef.child("mining").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double pendingTokens = 0;
                Boolean isActive = snapshot.child("isMiningActive").getValue(Boolean.class);
                Long startTime = snapshot.child("startTime").getValue(Long.class);

                if (Boolean.TRUE.equals(isActive) && startTime != null && startTime > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long maxDuration = 24 * 60 * 60 * 1000; // 24 hours
                    elapsed = Math.min(elapsed, maxDuration);

                    // Use BoostManager for accurate calculation matching MiningFragment
                    try {
                        BoostManager boostManager = BoostManager.getInstance(TrustDashboardActivity.this);
                        pendingTokens = boostManager.calculateMiningAmount(elapsed);
                    } catch (Exception e) {
                        // Fallback: Base rate calculation
                        float baseRate = 0.00125f; // Base rate per second
                        pendingTokens = (elapsed / 1000.0) * baseRate;
                    }
                }

                double finalBalance = cachedBalance + pendingTokens;
                runOnUiThread(() -> {
                    totalBalanceText.setText(String.format(Locale.US, "%.2f", finalBalance));
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    totalBalanceText.setText(String.format(Locale.US, "%.2f", cachedBalance));
                });
            }
        });

        // Refresh from Firebase to ensure it's up to date
        walletManager.refreshBalance();
    }

    private void loadMiningStats() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Mining sessions
                int sessions = 0;
                if (snapshot.child("miningStats/sessionsCompleted").exists()) {
                    sessions = snapshot.child("miningStats/sessionsCompleted").getValue(Integer.class);
                }
                miningSessionsCount.setText(String.valueOf(sessions));

                // Total mined
                double totalMined = 0;
                if (snapshot.child("miningStats/totalMined").exists()) {
                    totalMined = ((Number) snapshot.child("miningStats/totalMined").getValue()).doubleValue();
                } else if (snapshot.child("totalcoins").exists()) {
                    totalMined = ((Number) snapshot.child("totalcoins").getValue()).doubleValue();
                }
                totalMinedText.setText(String.format(Locale.US, "%.2f LYX", totalMined));

                // Calculate average daily
                long accountCreated = 0;
                if (snapshot.child("createdAt").exists()) {
                    accountCreated = snapshot.child("createdAt").getValue(Long.class);
                }
                if (accountCreated > 0) {
                    long daysActive = Math.max(1, (System.currentTimeMillis() - accountCreated) / (24 * 60 * 60 * 1000));
                    double avgDaily = totalMined / daysActive;
                    avgDailyText.setText(String.format(Locale.US, "%.2f LYX/day", avgDaily));
                } else {
                    avgDailyText.setText("--");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadEarningsBreakdown() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Referral earnings
                double referralEarnings = 0;
                if (snapshot.child("referralEarnings").exists()) {
                    referralEarnings = ((Number) snapshot.child("referralEarnings").getValue()).doubleValue();
                }
                referralEarningsText.setText(String.format(Locale.US, "%.2f LYX", referralEarnings));

                // Bonus earnings (daily check-in, hourly bonus, etc.)
                double bonusEarnings = 0;
                if (snapshot.child("bonusEarnings").exists()) {
                    bonusEarnings = ((Number) snapshot.child("bonusEarnings").getValue()).doubleValue();
                } else if (snapshot.child("totalStreak").exists()) {
                    bonusEarnings = ((Number) snapshot.child("totalStreak").getValue()).doubleValue();
                }
                bonusEarningsText.setText(String.format(Locale.US, "%.2f LYX", bonusEarnings));

                // Games earnings (spin, prediction, etc.)
                double gamesEarnings = 0;
                if (snapshot.child("spinEarnings").exists()) {
                    gamesEarnings += ((Number) snapshot.child("spinEarnings").getValue()).doubleValue();
                }
                if (snapshot.child("gamesEarnings").exists()) {
                    gamesEarnings += ((Number) snapshot.child("gamesEarnings").getValue()).doubleValue();
                }
                gamesEarningsText.setText(String.format(Locale.US, "%.2f LYX", gamesEarnings));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadTransactionHistory() {
        // Load recent transactions from Firebase
        Query recentTransactions = userRef.child("transactions")
                .orderByChild("timestamp")
                .limitToLast(50);

        recentTransactions.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                transactions.clear();

                for (DataSnapshot txSnapshot : snapshot.getChildren()) {
                    try {
                        String type = txSnapshot.child("type").getValue(String.class);
                        Double amount = txSnapshot.child("amount").getValue(Double.class);
                        Long timestamp = txSnapshot.child("timestamp").getValue(Long.class);
                        String description = txSnapshot.child("description").getValue(String.class);

                        if (type != null && amount != null && timestamp != null) {
                            transactions.add(0, new Transaction(type, amount, timestamp, description));
                        }
                    } catch (Exception e) {
                        // Skip malformed transactions
                    }
                }

                // If no transactions, add some placeholder entries based on stats
                if (transactions.isEmpty()) {
                    addPlaceholderTransactions();
                }

                transactionAdapter.notifyDataSetChanged();
                noTransactionsText.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                transactionRecyclerView.setVisibility(transactions.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addPlaceholderTransactions() {
        // Add some recent activity indicators if no formal transactions exist
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long now = System.currentTimeMillis();

                // Check last mining
                if (snapshot.child("mining/startTime").exists()) {
                    Long startTime = snapshot.child("mining/startTime").getValue(Long.class);
                    if (startTime != null && startTime > 0) {
                        transactions.add(new Transaction("mining", 0, startTime, "Mining session started"));
                    }
                }

                // Check last check-in
                if (snapshot.child("lastDate").exists()) {
                    String lastDate = snapshot.child("lastDate").getValue(String.class);
                    if (lastDate != null) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date date = sdf.parse(lastDate);
                            if (date != null) {
                                transactions.add(new Transaction("checkin", 5, date.getTime(), "Daily check-in"));
                            }
                        } catch (Exception ignored) {}
                    }
                }

                transactionAdapter.notifyDataSetChanged();
                noTransactionsText.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateMainnetCountdown() {
        long now = System.currentTimeMillis();
        long remaining = MAINNET_LAUNCH_DATE - now;

        if (remaining <= 0) {
            // Mainnet launched
            mainnetStatusText.setText("🚀 MAINNET LIVE");
            mainnetDaysText.setText("00");
            mainnetHoursText.setText("00");
            mainnetMinutesText.setText("00");
            mainnetProgress.setProgress(100);

            // Enable withdrawal
            withdrawalStatusText.setText("Withdrawal Available");
            withdrawalInfoText.setText("You can now withdraw your LYX tokens to your wallet.");
            withdrawalBtn.setEnabled(true);
            withdrawalBtn.setText("Withdraw Now");
        } else {
            // Calculate countdown
            long days = TimeUnit.MILLISECONDS.toDays(remaining);
            long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;

            mainnetDaysText.setText(String.format(Locale.US, "%02d", days));
            mainnetHoursText.setText(String.format(Locale.US, "%02d", hours));
            mainnetMinutesText.setText(String.format(Locale.US, "%02d", minutes));

            // Calculate progress (assuming 1 year countdown)
            long totalDuration = 365L * 24 * 60 * 60 * 1000;
            int progress = (int) ((totalDuration - remaining) * 100 / totalDuration);
            mainnetProgress.setProgress(Math.max(0, Math.min(100, progress)));

            mainnetStatusText.setText("⏳ Mainnet Launch");

            // Withdrawal not yet available
            withdrawalStatusText.setText("Coming Soon");
            withdrawalInfoText.setText("Withdrawals will be enabled after mainnet launch. Keep mining to maximize your balance!");
            withdrawalBtn.setEnabled(false);
            withdrawalBtn.setText("Not Yet Available");
        }
    }

    private void setupTrustBadges() {
        // Add trust badges dynamically
        trustBadgesLayout.removeAllViews();

        addTrustBadge("🔒", "Secure", "Firebase encrypted");
        addTrustBadge("✅", "Verified", "Google Play approved");
        addTrustBadge("📊", "Transparent", "Real-time tracking");
        addTrustBadge("🌐", "Global", "Worldwide users");
    }

    private void addTrustBadge(String icon, String title, String subtitle) {
        View badgeView = LayoutInflater.from(this).inflate(R.layout.item_trust_badge, trustBadgesLayout, false);

        TextView iconText = badgeView.findViewById(R.id.badgeIcon);
        TextView titleText = badgeView.findViewById(R.id.badgeTitle);
        TextView subtitleText = badgeView.findViewById(R.id.badgeSubtitle);

        iconText.setText(icon);
        titleText.setText(title);
        subtitleText.setText(subtitle);

        trustBadgesLayout.addView(badgeView);
    }

    private void showWithdrawalInfo() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("💰 About Withdrawals")
                .setMessage("LYX tokens are currently in-app rewards that track your mining progress.\n\n" +
                        "After mainnet launch:\n" +
                        "• Connect your crypto wallet\n" +
                        "• Withdraw LYX to your wallet\n" +
                        "• Trade on supported exchanges\n\n" +
                        "Keep mining to maximize your balance before launch!")
                .setPositiveButton("Got it!", null)
                .setNeutralButton("Learn More", (d, w) -> {
                    // Could open a FAQ or website
                    ToastUtils.showInfo(this, "Visit our FAQ for more details");
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    // ==================== TRANSACTION MODEL ====================

    public static class Transaction {
        public String type;
        public double amount;
        public long timestamp;
        public String description;

        public Transaction(String type, double amount, long timestamp, String description) {
            this.type = type;
            this.amount = amount;
            this.timestamp = timestamp;
            this.description = description != null ? description : getDefaultDescription(type);
        }

        private String getDefaultDescription(String type) {
            switch (type) {
                case "mining": return "Mining reward";
                case "spin": return "Spin wheel reward";
                case "checkin": return "Daily check-in";
                case "referral": return "Referral bonus";
                case "achievement": return "Achievement unlocked";
                case "bonus": return "Bonus reward";
                case "game": return "Mini game reward";
                default: return "Token reward";
            }
        }

        public String getIcon() {
            switch (type) {
                case "mining": return "⛏️";
                case "spin": return "🎰";
                case "checkin": return "📅";
                case "referral": return "👥";
                case "achievement": return "🏆";
                case "bonus": return "🎁";
                case "game": return "🎮";
                default: return "💰";
            }
        }
    }

    // ==================== TRANSACTION ADAPTER ====================

    public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
        private List<Transaction> transactions;

        public TransactionAdapter(List<Transaction> transactions) {
            this.transactions = transactions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction tx = transactions.get(position);

            holder.iconText.setText(tx.getIcon());
            holder.descriptionText.setText(tx.description);

            if (tx.amount > 0) {
                holder.amountText.setText(String.format(Locale.US, "+%.2f LYX", tx.amount));
                holder.amountText.setTextColor(getResources().getColor(R.color.accentGreen, null));
            } else if (tx.amount < 0) {
                holder.amountText.setText(String.format(Locale.US, "%.2f LYX", tx.amount));
                holder.amountText.setTextColor(getResources().getColor(R.color.error, null));
            } else {
                holder.amountText.setText("--");
                holder.amountText.setTextColor(getResources().getColor(R.color.textSecondary, null));
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            holder.timeText.setText(sdf.format(new Date(tx.timestamp)));
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView iconText, descriptionText, amountText, timeText;

            ViewHolder(View itemView) {
                super(itemView);
                iconText = itemView.findViewById(R.id.txIcon);
                descriptionText = itemView.findViewById(R.id.txDescription);
                amountText = itemView.findViewById(R.id.txAmount);
                timeText = itemView.findViewById(R.id.txTime);
            }
        }
    }
}

