package network.lynx.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MiningBlobView extends View {
    private Paint mainPaint;
    private Paint particlePaint;
    private Paint glowPaint;
    private List<Particle> particles;
    private float centerX, centerY;
    private float baseRadius;
    private float[] layerRadii;
    private float[] layerPhases;
    private float[] layerSpeeds;
    private float time = 0;
    private Random random;
    private final int LAYERS = 3;
    private final int PARTICLES = 20;

    // Green mining colors
    private final int MINING_MAIN = Color.parseColor("#00E676");
    private final int MINING_GLOW = Color.parseColor("#69F0AE");
    private final int MINING_ACCENT = Color.parseColor("#B9F6CA");

    private class Particle {
        float x, y;
        float size;
        float speed;
        float angle;
        float distance;
        float alpha;

        Particle(float centerX, float centerY, float baseRadius) {
            reset(centerX, centerY, baseRadius);
        }

        void reset(float centerX, float centerY, float baseRadius) {
            angle = random.nextFloat() * 360;
            distance = baseRadius * (0.5f + random.nextFloat() * 0.8f);
            x = centerX + (float) Math.cos(Math.toRadians(angle)) * distance;
            y = centerY + (float) Math.sin(Math.toRadians(angle)) * distance;
            size = 4 + random.nextFloat() * 8;
            speed = 0.2f + random.nextFloat() * 0.8f;
            alpha = 50 + random.nextFloat() * 150;
        }

        void update(float centerX, float centerY, float baseRadius) {
            angle += speed;
            distance = baseRadius * (0.5f + 0.3f * (float) Math.sin(time * 0.5f + angle * 0.1f));
            x = centerX + (float) Math.cos(Math.toRadians(angle)) * distance;
            y = centerY + (float) Math.sin(Math.toRadians(angle)) * distance;
            alpha = 50 + 100 * (float) Math.sin(time + angle * 0.2f);

            if (distance > baseRadius * 1.5f) {
                reset(centerX, centerY, baseRadius);
            }
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setAlpha((int) alpha);
            canvas.drawCircle(x, y, size, paint);
        }
    }

    public MiningBlobView(Context context) {
        super(context);
        init();
    }

    public MiningBlobView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiningBlobView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        random = new Random();

        mainPaint = new Paint();
        mainPaint.setAntiAlias(true);
        mainPaint.setStyle(Paint.Style.FILL);

        particlePaint = new Paint();
        particlePaint.setAntiAlias(true);
        particlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setColor(MINING_GLOW);

        glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(4f);
        glowPaint.setColor(MINING_ACCENT);

        layerRadii = new float[LAYERS];
        layerPhases = new float[LAYERS];
        layerSpeeds = new float[LAYERS];

        for (int i = 0; i < LAYERS; i++) {
            layerPhases[i] = random.nextFloat() * 360;
            layerSpeeds[i] = 0.5f + random.nextFloat() * 1.5f;
        }

        particles = new ArrayList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) * 0.35f;

        for (int i = 0; i < LAYERS; i++) {
            layerRadii[i] = baseRadius * (0.7f + 0.3f * i / LAYERS);
        }

        RadialGradient gradient = new RadialGradient(
                centerX, centerY, baseRadius * 1.5f,
                new int[]{MINING_MAIN, MINING_GLOW, Color.TRANSPARENT},
                new float[]{0.3f, 0.7f, 1.0f},
                Shader.TileMode.CLAMP
        );
        mainPaint.setShader(gradient);

        particles.clear();
        for (int i = 0; i < PARTICLES; i++) {
            particles.add(new Particle(centerX, centerY, baseRadius));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw outer glow
        glowPaint.setAlpha(50 + (int) (50 * Math.sin(time * 0.8)));
        canvas.drawCircle(centerX, centerY, baseRadius * 1.1f, glowPaint);

        // Draw base layer with pulsing effect
        float pulseFactor = 0.9f + 0.1f * (float) Math.sin(time);
        canvas.drawCircle(centerX, centerY, baseRadius * pulseFactor, mainPaint);

        // Draw animated layers
        for (int i = 0; i < LAYERS; i++) {
            float phase = layerPhases[i] + time * layerSpeeds[i];
            float radius = layerRadii[i] * (0.9f + 0.1f * (float) Math.sin(time * 0.5f + i));

            Path path = new Path();
            float startX = centerX + radius * (float) Math.cos(Math.toRadians(phase));
            float startY = centerY + radius * (float) Math.sin(Math.toRadians(phase));
            path.moveTo(startX, startY);

            for (int angle = 0; angle <= 360; angle += 10) {
                float waveAmplitude = baseRadius * 0.1f * (float) Math.sin(Math.toRadians(angle * 3 + phase * 2));
                float x = centerX + (radius + waveAmplitude) * (float) Math.cos(Math.toRadians(angle + phase));
                float y = centerY + (radius + waveAmplitude) * (float) Math.sin(Math.toRadians(angle + phase));
                path.lineTo(x, y);
            }

            mainPaint.setAlpha(80 - i * 20);
            canvas.drawPath(path, mainPaint);
        }

        // Draw particles
        for (Particle particle : particles) {
            particle.update(centerX, centerY, baseRadius);
            particle.draw(canvas, particlePaint);
        }

        // Draw inner highlight ring
        glowPaint.setAlpha(100 + (int) (100 * Math.sin(time * 1.2)));
        glowPaint.setStrokeWidth(2f);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.7f, glowPaint);

        mainPaint.setAlpha(255);
        glowPaint.setAlpha(255);

        time += 0.05f;
        invalidate();
    }
}