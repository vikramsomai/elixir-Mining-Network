package network.lynx.app;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import network.lynx.app.R;
import network.lynx.app.CommissionAdapter;
import network.lynx.app.CommissionInfo;

public class CommissionsTabFragment extends Fragment {

    private TextView todayCommission, weekCommission, totalCommission, emptyCommissionsText;
    private RecyclerView commissionsRecyclerView;
    private CommissionAdapter commissionAdapter;
    private List<CommissionInfo> commissionList = new ArrayList<>();

    // Track listeners for cleanup
    private ValueEventListener commissionsListener;
    private DatabaseReference userRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_commissions_tab, container, false);

        todayCommission = view.findViewById(R.id.todayCommission);
        weekCommission = view.findViewById(R.id.weekCommission);
        totalCommission = view.findViewById(R.id.totalCommission);
        emptyCommissionsText = view.findViewById(R.id.emptyCommissionsText);
        commissionsRecyclerView = view.findViewById(R.id.commissionsRecyclerView);

        setupRecyclerView();
        initializeCommissionsNode();
        loadCommissionData();

        return view;
    }

    private void setupRecyclerView() {
        commissionAdapter = new CommissionAdapter(commissionList);
        commissionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        commissionsRecyclerView.setAdapter(commissionAdapter);
    }

    private void initializeCommissionsNode() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        userRef.child("commissions").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    userRef.child("commissions").setValue(new HashMap<>());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void loadCommissionData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Remove existing listener to prevent duplicates
        if (commissionsListener != null) {
            userRef.child("commissions").removeEventListener(commissionsListener);
        }

        // OPTIMIZATION: Use single value event listener to reduce Firebase reads
        commissionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                commissionList.clear();
                double todayTotal = 0.0;
                double weekTotal = 0.0;
                double allTimeTotal = 0.0;

                // Get current time
                Calendar calendar = Calendar.getInstance();
                long currentTime = calendar.getTimeInMillis();

                // Start of today
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long startOfToday = calendar.getTimeInMillis();

                // Start of this week (assuming week starts on Monday)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
                long startOfWeek = calendar.getTimeInMillis();

                for (DataSnapshot commissionSnapshot : snapshot.getChildren()) {
                    CommissionInfo commission = commissionSnapshot.getValue(CommissionInfo.class);

                    if (commission != null) {
                        commissionList.add(commission);
                        allTimeTotal += commission.getAmount();

                        if (commission.getTimestamp() >= startOfToday) {
                            todayTotal += commission.getAmount();
                        }

                        if (commission.getTimestamp() >= startOfWeek) {
                            weekTotal += commission.getAmount();
                        }
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(commissionList, (c1, c2) ->
                        Long.compare(c2.getTimestamp(), c1.getTimestamp()));

                // Update UI
                todayCommission.setText(String.format("%.2f LYX", todayTotal));
                weekCommission.setText(String.format("%.2f LYX", weekTotal));
                totalCommission.setText(String.format("%.2f LYX", allTimeTotal));

                commissionAdapter.notifyDataSetChanged();

                // Show empty state if no commissions
                if (commissionList.isEmpty()) {
                    emptyCommissionsText.setVisibility(View.VISIBLE);
                    commissionsRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyCommissionsText.setVisibility(View.GONE);
                    commissionsRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        };

        userRef.child("commissions").addListenerForSingleValueEvent(commissionsListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // OPTIMIZATION: Clean up Firebase listeners
        if (commissionsListener != null && userRef != null) {
            userRef.child("commissions").removeEventListener(commissionsListener);
            commissionsListener = null;
        }
    }
}