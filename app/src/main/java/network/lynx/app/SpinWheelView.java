package network.lynx.app;

import static android.content.Context.MODE_PRIVATE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class SpinWheelView extends View {

    private Paint paint, textPaint, centerPaint;
    private int[] colors = {
            Color.parseColor("#F94144"),
            Color.parseColor("#F3722C"),
            Color.parseColor("#F9C74F"),
            Color.parseColor("#90BE6D"),
            Color.parseColor("#43AA8B"),
            Color.parseColor("#577590")
    };

    private String[] labels = {"10 Coins", "50 Coins", "Try Again", "100 Coins", "5 Coins", "Better Luck"};
    private float angle = 0;
    private boolean spinning = false;
    private MediaPlayer spinSound;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private FirebaseUser currentUser;

    public SpinWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(33);
        textPaint.setTextAlign(Paint.Align.CENTER);

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(Color.parseColor("#1E2237")); // Matches your background

        spinSound = MediaPlayer.create(context, R.raw.spin); // Add spin.mp3 in res/raw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float radius = Math.min(w, h) / 2 - 20;
        float centerX = w / 2f, centerY = h / 2f;
        float sweepAngle = 360f / labels.length;

        canvas.save();
        canvas.rotate(angle, centerX, centerY);
        for (int i = 0; i < labels.length; i++) {
            paint.setColor(colors[i]);
            canvas.drawArc(centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                    i * sweepAngle, sweepAngle, true, paint);

            float textAngle = (i + 0.5f) * sweepAngle;
            float textX = (float) (centerX + radius / 1.5 * Math.cos(Math.toRadians(textAngle)));
            float textY = (float) (centerY + radius / 1.5 * Math.sin(Math.toRadians(textAngle)));
            canvas.save();
            canvas.rotate(textAngle, textX, textY);
            canvas.drawText(labels[i], textX, textY, textPaint);
            canvas.restore();
        }
        canvas.restore();

        // Center Circle
        float centerRadius = radius / 3;
        canvas.drawCircle(centerX, centerY, centerRadius, centerPaint);

        // White dot in center
        Paint smallCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallCirclePaint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, 25, smallCirclePaint);
    }

    public void spin() {
        if (spinning) return;

        if (spinSound != null) spinSound.start();

        spinning = true;
        float sweep = 360f / labels.length;

        float randomAngle = new Random().nextFloat() * 360;
        float targetAngle = 360 * 5 + randomAngle; // 5 full rotations + random stop
        ValueAnimator animator = ValueAnimator.ofFloat(angle, angle + targetAngle);
        animator.setDuration(4000);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue() % 360;
            invalidate();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                spinning = false;

                float adjustedAngle = (360 - angle + 270) % 360;

                int landedIndex = (int) (adjustedAngle / sweep);
                String reward = labels[landedIndex];

                ToastUtils.showInfo(getContext(), "You won: " + reward);

                sharedPreferences = getContext().getSharedPreferences("userData", MODE_PRIVATE);
                auth = FirebaseAuth.getInstance();
                currentUser = auth.getCurrentUser();
                String userId = sharedPreferences.getString("userid", null);

                if (currentUser != null && userId != null && !userId.isEmpty()) {
                    databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId);

                    int coinsWon = 0;
                    if (reward.equals("10 Coins")) coinsWon = 10;
                    else if (reward.equals("50 Coins")) coinsWon = 50;
                    else if (reward.equals("100 Coins")) coinsWon = 100;
                    else if (reward.equals("5 Coins")) coinsWon = 5;

                    if (coinsWon > 0) {
                        int finalCoinsWon = coinsWon;
                        databaseReference.child("totalcoins").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                Double currentCoins = snapshot.getValue(Double.class);
                                if (currentCoins == null) currentCoins = 0.0;
                                Double updatedCoins = currentCoins + finalCoinsWon;

                                databaseReference.child("totalcoins").setValue(updatedCoins)
                                        .addOnSuccessListener(aVoid -> {
                                            ToastUtils.showInfo(getContext(), "+ " + finalCoinsWon + " Coins!");
                                        })
                                        .addOnFailureListener(e -> {
                                            ToastUtils.showInfo(getContext(), "Failed to update coins");
                                        });
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                ToastUtils.showInfo(getContext(), "Error: " + error.getMessage());
                            }
                        });
                    }
                } else {
                    ToastUtils.showInfo(getContext(), "User not logged in");
                }
            }
        });

        animator.start();
    }
}
