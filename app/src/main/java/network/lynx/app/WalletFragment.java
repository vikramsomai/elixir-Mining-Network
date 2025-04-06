package network.lynx.app;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;




public class WalletFragment extends Fragment {


    SharedPreferences sharedPreferences;
    TextView textView;
    ImageView copyAddress;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @SuppressLint({"MissingInflatedId", "WrongViewCast"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_wallet, container, false);
        sharedPreferences = this.getActivity().getSharedPreferences("userData", Context.MODE_PRIVATE);
        textView=view.findViewById(R.id.cryptoAddress);
        copyAddress=view.findViewById(R.id.copyAddress);
        String userId = sharedPreferences.getString("userid", null);
        String address=createUniqueId(userId);
        textView.setText(address.toString());
        String addressFull=createFullUniqueId(userId);
        copyAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipboard(addressFull);
                Toast.makeText(getContext(), "Address copied", Toast.LENGTH_SHORT).show();
            }
        });
        return view;
    }

    public String createUniqueId(String userId) {
        try {
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userId.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 20; i++) { // Take the first 8 bytes for the short address
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            // Get the full hash string
            String fullHash = hexString.toString();

            // Extract the first 4 and last 6 characters
            String first4 = fullHash.substring(0, 4);
            String last6 = fullHash.substring(fullHash.length() - 6);

            // Format the unique ID
            return "0x" + first4 + "..." + last6;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    public String createFullUniqueId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(userId.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 20; i++) { // Take the first 8 bytes for the short address
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return "0x" + hexString.toString(); // Prefix with 0x
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Unique ID", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }
}