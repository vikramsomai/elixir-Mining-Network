package network.lynx.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;

public class ProfileFragment extends Fragment {
    private MaterialCardView accountCard, supportCard, termsCard, privacyCard, rateCard, faqCard;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(view);
        setupClickListeners();

        return view;
    }

    private void initializeViews(View view) {
        accountCard = view.findViewById(R.id.accountCard);
        supportCard = view.findViewById(R.id.supportCard);
        termsCard = view.findViewById(R.id.termsCard);
        privacyCard = view.findViewById(R.id.privacyCard);
        rateCard = view.findViewById(R.id.rateCard);
        faqCard = view.findViewById(R.id.faqCard);
    }

    private void setupClickListeners() {
        if (accountCard != null) {
            accountCard.setOnClickListener(v -> {
                if (isAdded() && getContext() != null) {
                    startActivity(new Intent(getContext(), ProfileEditActivity.class));
                }
            });
        }

        if (privacyCard != null) {
            privacyCard.setOnClickListener(v -> {
                if (isAdded() && getContext() != null) {
                    startActivity(new Intent(getContext(), PrivacyPolicyActivity.class));
                }
            });
        }

        if (termsCard != null) {
            termsCard.setOnClickListener(v -> {
                if (isAdded() && getContext() != null) {
                    startActivity(new Intent(getContext(), TermsConditionActivity.class));
                }
            });
        }

        if (rateCard != null) {
            rateCard.setOnClickListener(v -> {
                if (isAdded()) {
                    showSocialMediaDialog(
                        "Rate Lynx Network",
                        "If you enjoy using the app, please rate us on the Play Store!",
                        "https://play.google.com/store/apps/details?id=network.lynx.app"
                    );
                }
            });
        }

        if (faqCard != null) {
            faqCard.setOnClickListener(v -> {
                if (isAdded() && getContext() != null) {
                    startActivity(new Intent(getContext(), FaqsActivity.class));
                }
            });
        }

        if (supportCard != null) {
            supportCard.setOnClickListener(v -> {
                if (isAdded()) {
                    showSocialMediaDialog(
                        "Contact Support",
                        "Join our Telegram for support and updates!",
                        "https://t.me/lynx_network_annoucement"
                    );
                }
            });
        }
    }

    private void showSocialMediaDialog(String title, String message, String url) {
        if (getContext() == null) return;

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog_social_media, null);

        TextView socialTitle = dialogView.findViewById(R.id.socialTitle);
        TextView socialMessage = dialogView.findViewById(R.id.socialMessage);
        TextView cancelButton = dialogView.findViewById(R.id.cancelButton);
        TextView continueButton = dialogView.findViewById(R.id.continueButton);

        socialTitle.setText(title);
        socialMessage.setText(message);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        continueButton.setOnClickListener(v -> {
            openExternalLink(url);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void openExternalLink(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }
}