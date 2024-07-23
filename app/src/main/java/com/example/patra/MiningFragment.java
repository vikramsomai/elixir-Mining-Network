package com.example.patra;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.xml.sax.Parser;

public class MiningFragment extends Fragment {

    private TextView counterTextView,tokenValue;
    private Button startButton;
    private CounterService counterService;
    private boolean isBound = false;
    double total=0.000;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CounterService.LocalBinder binder = (CounterService.LocalBinder) service;
            counterService = binder.getService();
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver counterUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isBound && counterService != null) {
                double count = intent.getDoubleExtra("count", 0.0000);
                counterTextView.setText(String.format("%.4f", count));
                updateUI();
            } else {
                Log.e("MiningFragment", "CounterService is not bound.");
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mining, container, false);

        counterTextView = view.findViewById(R.id.counterTextView);
        startButton = view.findViewById(R.id.startButton);
        tokenValue=view.findViewById(R.id.tokenid);
        startButton.setOnClickListener(v -> {

            if (isBound && counterService != null) {
                total+=counterService.getCount();
                tokenValue.setText(String.format("%.4f", total));
                Intent intent = new Intent(getActivity(), CounterService.class);
                intent.setAction("START_COUNTER");
                getActivity().startService(intent);
                startButton.setEnabled(false);
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(counterUpdateReceiver, new IntentFilter("CounterUpdate"));
        Intent intent = new Intent(getActivity(), CounterService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(counterUpdateReceiver);
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void updateUI() {
        if (isBound && counterService != null) {
            startButton.setEnabled(!counterService.isRunning);
        }
    }
}
