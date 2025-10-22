package network.lynx.app;

import static android.content.Context.MODE_PRIVATE;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.card.MaterialCardView;

public class ProfileFragment extends Fragment {
    TextView editProfile, username;
    MaterialCardView logout, telegram, kyc, privacy, terms, faqs, website, account, support, discord, twitter, rate;
    ImageView imageView;
    GoogleSignInClient mGoogleSignInClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        account = view.findViewById(R.id.accountCard);
        support = view.findViewById(R.id.supportCard);
        terms = view.findViewById(R.id.termsCard);
        privacy = view.findViewById(R.id.privacyCard);
        rate = view.findViewById(R.id.rateCard);
        discord = view.findViewById(R.id.discordCard);
        twitter = view.findViewById(R.id.twitterCard);

        SharedPreferences prefs = this.getActivity().getSharedPreferences("userData", MODE_PRIVATE);
        String name = prefs.getString("username", null);
        if (name == null) {
            name = "knownk";
        }
        String email = prefs.getString("email", null);
        String picture = prefs.getString("picture", null);

        account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getContext(), ProfileEditActivity.class);
                startActivity(i);
            }
        });

        privacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getContext(), PrivacyPolicyActivity.class);
                startActivity(i);
            }
        });

        // Updated Twitter click listener with confirmation dialog
        twitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSocialMediaDialog(
                        "Follow us on Twitter",
                        "You will be redirected to our Twitter page. Follow us for the latest updates!",
                        "https://twitter.com/lynxnetwork_"
                );
            }
        });

        // Updated Discord click listener with confirmation dialog
        discord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSocialMediaDialog(
                        "Join our Discord",
                        "You will be redirected to our Discord server. Join our community for discussions and support!",
                        "https://discord.gg/veQgvUD8"
                );
            }
        });

        terms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getContext(), TermsConditionActivity.class);
                startActivity(i);
            }
        });

        rate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSocialMediaDialog(
                        "Rate Lynx Network",
                        "If you enjoy using the app, please rate us on the Play Store. Your support means a lot!",
                        "https://play.google.com/store/apps/details?id=network.lynx.app"
                );
            }
        });


        support.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ToastUtils.showInfo(getContext(), "Support feature is under development");
            }
        });

        return view;
    }

    // Helper method to show confirmation dialog for social media links
    private void showSocialMediaDialog(String title, String message, String url) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog_social_media, null);

        // Find views from the custom layout
        ImageView socialIcon = dialogView.findViewById(R.id.socialIcon);
        TextView socialTitle = dialogView.findViewById(R.id.socialTitle);
        TextView socialMessage = dialogView.findViewById(R.id.socialMessage);
        TextView cancelButton = dialogView.findViewById(R.id.cancelButton);
        TextView continueButton = dialogView.findViewById(R.id.continueButton);

        // Set title and message
        socialTitle.setText(title);
        socialMessage.setText(message);

        // Optional: Change icon based on platform
        if (title.toLowerCase().contains("twitter")) {
//            socialIcon.setImageResource(R.drawable.ic_twitter);
        } else if (title.toLowerCase().contains("discord")) {
            socialIcon.setImageResource(R.drawable.ic_discord);
        } else {
//            socialIcon.setImageResource(R.drawable.ic_link); // fallback icon
        }

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Handle button clicks
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        continueButton.setOnClickListener(v -> {
            openExternalLink(url);
            dialog.dismiss();
        });

        dialog.show();
    }


    // Helper method to open external links
    private void openExternalLink(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback: try to open in browser
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        } catch (Exception e) {
            // Handle any errors
            Toast.makeText(getContext(), "Unable to open link. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // Keep the existing openTelegram method for reference (if needed elsewhere)
    private void openTelegram() {
        // Replace with your Telegram channel or chat link
        String telegramUrl = "https://t.me/lynx_network_annoucement";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(telegramUrl));
        // Check if the Telegram app is installed
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Optional: Handle case where Telegram app is not installed
            // For example, you might open the Play Store or show a message
        }
    }
}