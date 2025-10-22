package network.lynx.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.List;

public class ComingSoonTasksAdapter extends RecyclerView.Adapter<ComingSoonTasksAdapter.TaskViewHolder> {
    private static final String TAG = "ComingSoonTasksAdapter";
    private List<BoostActivity.TaskItem> tasksList;
    private boolean isCompletedList;
    private DatabaseReference userRef;
    private String userId;
    private TaskManager taskManager;
    private Handler handler = new Handler(Looper.getMainLooper());

    public ComingSoonTasksAdapter(List<BoostActivity.TaskItem> tasksList, boolean isCompletedList, TaskManager taskManager) {
        this.tasksList = tasksList;
        this.isCompletedList = isCompletedList;
        this.taskManager = taskManager;
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_boost_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        BoostActivity.TaskItem task = tasksList.get(position);
        holder.taskTitle.setText(task.getTitle());
        holder.taskMultiplier.setText(task.getMultiplier());

        // Show status, progress, or duration
        if (task.getStatus() != null && !task.getStatus().isEmpty()) {
            holder.taskDuration.setText(task.getStatus());
        } else if (task.getProgress() != null && !task.getProgress().isEmpty()) {
            holder.taskDuration.setText(task.getProgress());
        } else {
            holder.taskDuration.setText(task.getDuration());
        }

        boolean isCompleted = task.isCompleted();
        holder.checkmark.setVisibility(isCompleted ? View.VISIBLE : View.GONE);

        // Handle claim button visibility and text
        setupClaimButton(holder, task, position);

        // Set click listeners for incomplete tasks
        if (!isCompletedList) {
            holder.itemView.setOnClickListener(v -> handleTaskClick(task, holder, position));
            holder.claimTaskBoostBtn.setOnClickListener(v -> handleTaskClick(task, holder, position));
        }
    }

    private void setupClaimButton(TaskViewHolder holder, BoostActivity.TaskItem task, int position) {
        if (isCompletedList) {
            // For completed tasks
            switch (task.getTaskType()) {
                case INVITE_FRIENDS:
                case WATCH_AD:
                case TEMPORARY_BOOST:
                case RATE_APP:
                    holder.claimTaskBoostBtn.setVisibility(View.GONE);
                    break;
                default:
                    holder.claimTaskBoostBtn.setVisibility(View.VISIBLE);
                    holder.claimTaskBoostBtn.setText("Active");
                    holder.claimTaskBoostBtn.setEnabled(false);
                    break;
            }
        } else {
            // For incomplete tasks
            holder.claimTaskBoostBtn.setVisibility(View.VISIBLE);
            holder.claimTaskBoostBtn.setEnabled(true);
            switch (task.getTaskType()) {
                case INVITE_FRIENDS:
                    holder.claimTaskBoostBtn.setText("Invite");
                    break;
                case FOLLOW_TWITTER:
                    holder.claimTaskBoostBtn.setText("Follow");
                    break;
                case DAILY_CHECKIN:
                    holder.claimTaskBoostBtn.setText("Check In");
                    break;
                case WATCH_AD:
                    holder.claimTaskBoostBtn.setText("Go to Mining");
                    break;
                case TEMPORARY_BOOST:
                    holder.claimTaskBoostBtn.setText("Watch Ad");
                    break;
                case JOIN_TELEGRAM:
                    holder.claimTaskBoostBtn.setText("Join");
                    break;
                case SUBSCRIBE_YOUTUBE:
                    holder.claimTaskBoostBtn.setText("Subscribe");
                    break;
                case LIKE_FACEBOOK:
                    holder.claimTaskBoostBtn.setText("Like");
                    break;
                case FOLLOW_INSTAGRAM:
                    holder.claimTaskBoostBtn.setText("Follow");
                    break;
                case JOIN_DISCORD:
                    holder.claimTaskBoostBtn.setText("Join");
                    break;
                case PLAY_MINI_GAME:
                    holder.claimTaskBoostBtn.setText("Play");
                    break;
                case RATE_APP:
                    holder.claimTaskBoostBtn.setText("Rate");
                    break;
                case SHARE_APP:
                    holder.claimTaskBoostBtn.setText("Share");
                    break;
                default:
                    holder.claimTaskBoostBtn.setText("Complete");
                    break;
            }
        }
    }

    // Helper method to show confirmation dialog before opening external links
//    private void showConfirmationDialog(Context context, String title, String message, String url, Runnable onConfirm) {
//        new AlertDialog.Builder(context)
//                .setTitle(title)
//                .setMessage(message)
//                .setPositiveButton("Continue", (dialog, which) -> {
//                    // Open the external link
//                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                    context.startActivity(browserIntent);
//                    // Execute the confirmation callback
//                    if (onConfirm != null) {
//                        onConfirm.run();
//                    }
//                })
//                .setNegativeButton("Cancel", (dialog, which) -> {
//                    dialog.dismiss();
//                })
//                .setIcon(android.R.drawable.ic_dialog_info)
//                .show();
//    }

    private void handleTaskClick(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        switch (task.getTaskType()) {
            case INVITE_FRIENDS:
                handleInviteFriends(task, holder, position);
                break;
            case FOLLOW_TWITTER:
                handleTwitterFollow(task, holder, position);
                break;
            case DAILY_CHECKIN:
                handleDailyCheckin(task, holder, position);
                break;
            case WATCH_AD:
                handleWatchAd(holder);
                break;
            case TEMPORARY_BOOST:
                handleTemporaryBoost(holder);
                break;
            case JOIN_TELEGRAM:
                handleTelegramJoin(task, holder, position);
                break;
            case SUBSCRIBE_YOUTUBE:
                handleYouTubeSubscribe(task, holder, position);
                break;
            case LIKE_FACEBOOK:
                handleFacebookLike(task, holder, position);
                break;
            case FOLLOW_INSTAGRAM:
                handleInstagramFollow(task, holder, position);
                break;
            case JOIN_DISCORD:
                handleDiscordJoin(task, holder, position);
                break;
            case PLAY_MINI_GAME:
                handleMiniGame(task, holder, position);
                break;
            case RATE_APP:
                handleRateApp(task, holder, position);
                break;
            case SHARE_APP:
                handleShareApp(task, holder, position);
                break;
        }
    }

    private void handleInviteFriends(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        userRef.child("referrals").get().addOnSuccessListener(snapshot -> {
            long count = snapshot.exists() ? snapshot.getChildrenCount() : 0;
            if (count >= 3) {
                taskManager.completePermanentInviteBoost();
                ToastUtils.showInfo(holder.itemView.getContext(),
                        "🎉 Lifetime mining boost activated! +50% forever!");
                refreshTaskLists(holder.itemView.getContext());
            } else {
                ToastUtils.showInfo(holder.itemView.getContext(),
                        String.format("Invite %d more friends to unlock lifetime boost (%d/3)",
                                3 - count, count));
                shareInviteLink(holder.itemView.getContext());
            }
        });
    }

    private void handleTwitterFollow(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Follow on Twitter",
                "You will be redirected to Twitter to follow @lynxnetwork_. After following, you'll receive a +20% boost for 24 hours!",
                "https://twitter.com/lynxnetwork_",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeTwitterFollow();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Twitter follow completed! +20% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleDailyCheckin(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        if (taskManager != null) {
            taskManager.completeDailyCheckin();
            ToastUtils.showInfo(holder.itemView.getContext(),
                    "✅ Daily check-in completed! +10% boost for 24 hours!");
            refreshTaskLists(holder.itemView.getContext());
        }
    }

    private void handleTelegramJoin(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Join Telegram",
                "You will be redirected to Telegram to join our community. After joining, you'll receive a +15% boost for 24 hours!",
                "https://t.me/lynx_network_annoucement",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeTelegramJoin();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Telegram joined! +15% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleYouTubeSubscribe(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Subscribe on YouTube",
                "You will be redirected to YouTube to subscribe to our channel. After subscribing, you'll receive a +15% boost for 24 hours!",
                "https://youtube.com/@lynxnetwork",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeYouTubeSubscribe();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ YouTube subscribed! +15% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleFacebookLike(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Like on Facebook",
                "You will be redirected to Facebook to like our page. After liking, you'll receive a +10% boost for 24 hours!",
                "https://facebook.com/lynxnetwork",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeFacebookLike();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Facebook liked! +10% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleInstagramFollow(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Follow on Instagram",
                "You will be redirected to Instagram to follow our account. After following, you'll receive a +10% boost for 24 hours!",
                "https://instagram.com/lynxnetwork",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeInstagramFollow();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Instagram followed! +10% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleDiscordJoin(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Join Discord",
                "You will be redirected to Discord to join our server. After joining, you'll receive a +15% boost for 24 hours!",
                "https://discord.gg/veQgvUD8",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 10, () -> {
                        if (taskManager != null) {
                            taskManager.completeDiscordJoin();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Discord joined! +15% boost for 24 hours!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleMiniGame(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        if (taskManager != null) {
            taskManager.completeMiniGame();
            ToastUtils.showInfo(holder.itemView.getContext(),
                    "✅ Mini game completed! +5% boost for 24 hours!");
            refreshTaskLists(holder.itemView.getContext());
        }
    }
    private void showConfirmationDialog(Context context, String title, String message, String url, Runnable onConfirm) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_social_media, null);

        TextView titleText = dialogView.findViewById(R.id.socialTitle);
        TextView messageText = dialogView.findViewById(R.id.socialMessage);
        ImageView icon = dialogView.findViewById(R.id.socialIcon);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button continueButton = dialogView.findViewById(R.id.continueButton);

        titleText.setText(title);
        messageText.setText(message);
        // Optionally set icon with Glide or a resource

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        continueButton.setOnClickListener(v -> {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });

        dialog.show();
    }


    private void handleRateApp(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        showConfirmationDialog(
                holder.itemView.getContext(),
                "Rate Our App",
                "You will be redirected to Google Play Store to rate our app. After rating, you'll receive a +20% lifetime boost!",
                "https://play.google.com/store/apps/details?id=network.lynx.app",
                () -> {
                    holder.claimTaskBoostBtn.setEnabled(false);
                    holder.claimTaskBoostBtn.setText("Opening...");
                    showCountdownAndComplete(holder, 15, () -> {
                        if (taskManager != null) {
                            taskManager.completeAppRating();
                            ToastUtils.showInfo(holder.itemView.getContext(),
                                    "✅ Thank you for rating! +20% lifetime boost!");
                            refreshTaskLists(holder.itemView.getContext());
                        }
                    });
                }
        );
    }

    private void handleShareApp(BoostActivity.TaskItem task, TaskViewHolder holder, int position) {
        shareInviteLink(holder.itemView.getContext());
        if (taskManager != null) {
            taskManager.completeAppShare();
            ToastUtils.showInfo(holder.itemView.getContext(),
                    "✅ App shared! +10% boost for 24 hours!");
            refreshTaskLists(holder.itemView.getContext());
        }
    }

    private void handleWatchAd(TaskViewHolder holder) {
        ToastUtils.showInfo(holder.itemView.getContext(),
                "Go to Mining tab and watch an ad to activate this boost");
        try {
            Context context = holder.itemView.getContext();
            if (context instanceof BoostActivity) {
                ((BoostActivity) context).finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to mining", e);
        }
    }

    private void handleTemporaryBoost(TaskViewHolder holder) {
        ToastUtils.showInfo(holder.itemView.getContext(),
                "Go to Mining tab and watch a boost ad to activate this");
        try {
            Context context = holder.itemView.getContext();
            if (context instanceof BoostActivity) {
                ((BoostActivity) context).finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to mining", e);
        }
    }

    private void showCountdownAndComplete(TaskViewHolder holder, int seconds, Runnable onComplete) {
        if (seconds <= 0) {
            holder.claimTaskBoostBtn.setText("Completed!");
            holder.claimTaskBoostBtn.setEnabled(false);
            handler.postDelayed(() -> {
                if (onComplete != null) {
                    onComplete.run();
                }
            }, 500);
            return;
        }
        holder.claimTaskBoostBtn.setText(String.format("Wait %ds...", seconds));
        handler.postDelayed(() -> showCountdownAndComplete(holder, seconds - 1, onComplete), 1000);
    }

    private void refreshTaskLists(Context context) {
        handler.postDelayed(() -> {
            if (context instanceof BoostActivity) {
                ((BoostActivity) context).refreshTaskLists();
            }
        }, 1000);
    }

    private void shareInviteLink(Context context) {
        userRef.child("referralCode").get().addOnSuccessListener(snapshot -> {
            String referralCode = snapshot.getValue(String.class);
            if (referralCode != null) {
                String inviteLink = "https://play.google.com/store/apps/details?id=network.lynx.app&ref=" + referralCode;
                String message = "🚀 Join Lynx Network and earn rewards! Use my referral code: " + referralCode +
                        "\nDownload here: " + inviteLink +
                        "\n\n💰 We both get mining speed boosts and you earn 20% of my mining rewards!";
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, message);
                sendIntent.setType("text/plain");
                Intent shareIntent = Intent.createChooser(sendIntent, "Share via");
                context.startActivity(shareIntent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasksList.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView taskTitle, taskMultiplier, taskDuration;
        ImageView checkmark;
        Button claimTaskBoostBtn;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            taskTitle = itemView.findViewById(R.id.taskTitle);
            taskMultiplier = itemView.findViewById(R.id.taskMultiplier);
            taskDuration = itemView.findViewById(R.id.taskDuration);
            checkmark = itemView.findViewById(R.id.checkmark);
            claimTaskBoostBtn = itemView.findViewById(R.id.claimTaskBoostBtn);
        }
    }
}