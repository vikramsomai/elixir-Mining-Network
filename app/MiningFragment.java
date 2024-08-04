package com.somai.elixirMiningNetwork;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MiningFragment extends Fragment {

    private TextView counterTextView;
    private Button startButton;
    private CounterService counterService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CounterService.LocalBinder binder = (CounterService.LocalBinder) service;
            counterService = binder.getService();
            isBound = true;
            updateUI();

            // Listen for Firebase updates
            listenForCounterUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mining, container, false);

        counterTextView = view.findViewById(R.id.counterTextView);
        startButton = view.findViewById(R.id.startButton);

        startButton.setOnClickListener(v -> {
            if (isBound && counterService != null) {
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
        Intent intent = new Intent(getActivity(), CounterService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void updateUI() {
        if (isBound && counterService != null) {
            startButton.setEnabled(!counterService.isRunning());
        }
    }

    private void listenForCounterUpdates() {
        FirebaseDatabase.getInstance().getReference("counter")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Double counterValue = dataSnapshot.getValue(Double.class);
                        if (counterValue != null) {
                            counterTextView.setText(String.format("%.4f", counterValue));
                            Log.d("MiningFragment", "Counter value updated: " + counterValue);
                        } else {
                            Log.d("MiningFragment", "Counter value is null");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e("MiningFragment", "Failed to read counter value", databaseError.toException());
                    }
                });
    }
}
