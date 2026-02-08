package network.lynx.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MiningBlobView extends View {

    private Paint corePaint;
    private Paint coreGlowPaint;
    private Paint wavePaint;
    private Paint neuralPaint;
    private Paint particlePaint;
    private Paint pulsePaint;

    private float centerX, centerY;
    private float baseRadius;

    private float time = 0;
    private float wavePhase = 0;
    private float pulsePhase = 0;
    private boolean isMining = false;

    private List<NeuralNode> neuralNodes = new ArrayList<>();
    private List<EnergyParticle> particles = new ArrayList<>();
    private List<WaveRing> waveRings = new ArrayList<>();
    private Random random = new Random();

    private static final int CORE_BRIGHT = Color.parseColor("#00F5FF");
    private static final int CORE_MID = Color.parseColor("#00CED1");
    private static final int CORE_DARK = Color.parseColor("#008B8B");
    private static final int NEURAL_COLOR = Color.parseColor("#00FFFF");
    private static final int WAVE_COLOR = Color.parseColor("#40E0D0");
    private static final int PARTICLE_GLOW = Color.parseColor("#7FFFD4");
    private static final int PULSE_COLOR = Color.parseColor("#00BFFF");

    private class NeuralNode {
        float x, y, baseAngle, orbitRadius, pulseOffset, size, alpha;
        List<Integer> connections = new ArrayList<>();

        NeuralNode(int index, int total) {
            baseAngle = (360f / total) * index;
            orbitRadius = baseRadius * (0.5f + random.nextFloat() * 0.4f);
            pulseOffset = random.nextFloat() * (float) Math.PI * 2;
            size = 3f + random.nextFloat() * 4f;
            if (index > 0) connections.add(index - 1);
            if (index < total - 1) connections.add(index + 1);
            if (random.nextFloat() > 0.5f) connections.add((index + total / 2) % total);
        }

        void update() {
            float angleOffset = (float) Math.sin(time * 0.5f + pulseOffset) * 15f;
            float radiusOffset = (float) Math.sin(time + pulseOffset) * baseRadius * 0.1f;
            float currentRadius = orbitRadius + radiusOffset;
            float currentAngle = baseAngle + angleOffset + (isMining ? time * 20 : time * 5);
            x = centerX + currentRadius * (float) Math.cos(Math.toRadians(currentAngle));
            y = centerY + currentRadius * (float) Math.sin(Math.toRadians(currentAngle));
            alpha = 150 + 105 * (float) Math.sin(time * 2 + pulseOffset);
        }

        void draw(Canvas canvas, Paint paint, List<NeuralNode> allNodes) {
            paint.setStrokeWidth(1.5f);
            for (int connIndex : connections) {
                if (connIndex < allNodes.size()) {
                    NeuralNode other = allNodes.get(connIndex);
                    paint.setAlpha((int) ((alpha + other.alpha) / 4f));
                    canvas.drawLine(x, y, other.x, other.y, paint);
                }
            }
            paint.setAlpha((int) (alpha * 0.3f));
            canvas.drawCircle(x, y, size * 2.5f, paint);
            paint.setAlpha((int) alpha);
            canvas.drawCircle(x, y, size, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (alpha * 0.8f));
            canvas.drawCircle(x, y, size * 0.4f, paint);
            paint.setColor(NEURAL_COLOR);
        }
    }

    private class WaveRing {
        float radius, maxRadius, alpha, speed, thickness;

        WaveRing() { reset(); }

        void reset() {
            radius = baseRadius * 0.3f;
            maxRadius = baseRadius * 1.5f;
            alpha = 255;
            speed = 1f + random.nextFloat() * 2f;
            thickness = 2f + random.nextFloat() * 3f;
        }

        void update() {
            radius += speed * (isMining ? 1.5f : 0.8f);
            alpha = 255 * (1 - (radius - baseRadius * 0.3f) / (maxRadius - baseRadius * 0.3f));
            if (radius > maxRadius) reset();
        }

        void draw(Canvas canvas, Paint paint) {
            if (alpha <= 0) return;
            paint.setAlpha((int) alpha);
            paint.setStrokeWidth(thickness);
            canvas.drawCircle(centerX, centerY, radius, paint);
        }
    }

    private class EnergyParticle {
        float x, y, angle, speed, size, alpha, orbitRadius, phaseOffset;
        boolean isOrbiting;

        EnergyParticle() { reset(); }

        void reset() {
            angle = random.nextFloat() * 360;
            speed = 0.3f + random.nextFloat() * 1.2f;
            size = 1.5f + random.nextFloat() * 3f;
            alpha = 100 + random.nextFloat() * 155;
            orbitRadius = baseRadius * (0.2f + random.nextFloat() * 1.0f);
            phaseOffset = random.nextFloat() * (float) Math.PI * 2;
            isOrbiting = random.nextFloat() > 0.3f;
        }

        void update() {
            if (isOrbiting) {
                angle += speed * (isMining ? 2f : 0.8f);
                float wobble = (float) Math.sin(time * 3 + phaseOffset) * 10f;
                float currentRadius = orbitRadius + (float) Math.sin(time + phaseOffset) * baseRadius * 0.1f;
                x = centerX + currentRadius * (float) Math.cos(Math.toRadians(angle + wobble));
                y = centerY + currentRadius * (float) Math.sin(Math.toRadians(angle + wobble));
            } else {
                orbitRadius -= speed * 0.5f;
                if (orbitRadius < baseRadius * 0.2f) {
                    orbitRadius = baseRadius * 1.2f;
                    angle = random.nextFloat() * 360;
                }
                x = centerX + orbitRadius * (float) Math.cos(Math.toRadians(angle));
                y = centerY + orbitRadius * (float) Math.sin(Math.toRadians(angle));
            }
            alpha = 80 + 120 * (float) Math.abs(Math.sin(time * 2 + phaseOffset));
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(PARTICLE_GLOW);
            paint.setAlpha((int) (alpha * 0.4f));
            canvas.drawCircle(x, y, size * 2f, paint);
            paint.setColor(NEURAL_COLOR);
            paint.setAlpha((int) alpha);
            canvas.drawCircle(x, y, size, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (alpha * 0.9f));
            canvas.drawCircle(x, y, size * 0.3f, paint);
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
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corePaint.setStyle(Paint.Style.FILL);

        coreGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coreGlowPaint.setStyle(Paint.Style.FILL);

        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setColor(WAVE_COLOR);

        neuralPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        neuralPaint.setStyle(Paint.Style.FILL);
        neuralPaint.setColor(NEURAL_COLOR);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setColor(PULSE_COLOR);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) * 0.4f;

        RadialGradient coreGradient = new RadialGradient(
                centerX, centerY, baseRadius * 0.5f,
                new int[]{Color.WHITE, CORE_BRIGHT, CORE_MID, CORE_DARK, Color.TRANSPARENT},
                new float[]{0f, 0.2f, 0.5f, 0.8f, 1f},
                Shader.TileMode.CLAMP
        );
        corePaint.setShader(coreGradient);

        RadialGradient glowGradient = new RadialGradient(
                centerX, centerY, baseRadius * 1.2f,
                new int[]{CORE_BRIGHT, WAVE_COLOR, Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        coreGlowPaint.setShader(glowGradient);

        neuralNodes.clear();
        for (int i = 0; i < 12; i++) {
            neuralNodes.add(new NeuralNode(i, 12));
        }

        particles.clear();
        for (int i = 0; i < 25; i++) {
            particles.add(new EnergyParticle());
        }

        waveRings.clear();
        for (int i = 0; i < 4; i++) {
            WaveRing ring = new WaveRing();
            ring.radius = baseRadius * (0.3f + i * 0.2f);
            waveRings.add(ring);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float deltaTime = isMining ? 0.05f : 0.025f;
        time += deltaTime;
        wavePhase += deltaTime * 2;
        pulsePhase += deltaTime * 3;

        drawOuterGlow(canvas);
        drawWaveRings(canvas);
        drawSineWaves(canvas);
        drawNeuralNetwork(canvas);
        drawParticles(canvas);
        drawCore(canvas);
        drawPulseRings(canvas);
        drawCenterDetails(canvas);

        invalidate();
    }

    private void drawOuterGlow(Canvas canvas) {
        float glowPulse = 0.15f + 0.1f * (float) Math.sin(pulsePhase);
        coreGlowPaint.setAlpha((int) (glowPulse * 255));
        canvas.drawCircle(centerX, centerY, baseRadius * 1.3f, coreGlowPaint);
    }

    private void drawWaveRings(Canvas canvas) {
        for (WaveRing ring : waveRings) {
            ring.update();
            ring.draw(canvas, wavePaint);
        }
    }

    private void drawSineWaves(Canvas canvas) {
        wavePaint.setStrokeWidth(2f);
        for (int w = 0; w < 3; w++) {
            float waveRadius = baseRadius * (0.7f + w * 0.15f);
            Path wavePath = new Path();
            boolean first = true;
            for (int i = 0; i <= 360; i += 3) {
                float angle = (float) Math.toRadians(i);
                float waveAmp = baseRadius * 0.08f * (float) Math.sin(i * 0.1f + wavePhase + w * 0.5f);
                float r = waveRadius + waveAmp;
                float x = centerX + r * (float) Math.cos(angle);
                float y = centerY + r * (float) Math.sin(angle);
                if (first) { wavePath.moveTo(x, y); first = false; }
                else wavePath.lineTo(x, y);
            }
            wavePath.close();
            wavePaint.setAlpha(80 - w * 20);
            canvas.drawPath(wavePath, wavePaint);
        }
    }

    private void drawNeuralNetwork(Canvas canvas) {
        neuralPaint.setStyle(Paint.Style.STROKE);
        for (NeuralNode node : neuralNodes) node.update();
        for (NeuralNode node : neuralNodes) node.draw(canvas, neuralPaint, neuralNodes);
        neuralPaint.setStyle(Paint.Style.FILL);
    }

    private void drawParticles(Canvas canvas) {
        for (EnergyParticle particle : particles) {
            particle.update();
            particle.draw(canvas, particlePaint);
        }
    }

    private void drawCore(Canvas canvas) {
        float corePulse = 1f + 0.08f * (float) Math.sin(pulsePhase * 2);
        float coreRadius = baseRadius * 0.35f * corePulse;

        coreGlowPaint.setAlpha(100);
        canvas.drawCircle(centerX, centerY, coreRadius * 1.5f, coreGlowPaint);
        canvas.drawCircle(centerX, centerY, coreRadius, corePaint);

        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(Color.WHITE);
        innerPaint.setAlpha(200 + (int) (55 * Math.sin(pulsePhase * 3)));
        canvas.drawCircle(centerX, centerY, coreRadius * 0.4f, innerPaint);
    }

    private void drawPulseRings(Canvas canvas) {
        pulsePaint.setStrokeWidth(2f);

        float innerRadius = baseRadius * 0.55f;
        RectF innerOval = new RectF(centerX - innerRadius, centerY - innerRadius, centerX + innerRadius, centerY + innerRadius);
        for (int i = 0; i < 4; i++) {
            float startAngle = time * 50 + i * 90;
            float sweepAngle = 60 + 20 * (float) Math.sin(time + i);
            pulsePaint.setAlpha(150 + (int) (50 * Math.sin(time * 2 + i)));
            canvas.drawArc(innerOval, startAngle, sweepAngle, false, pulsePaint);
        }

        float outerRadius = baseRadius * 0.85f;
        RectF outerOval = new RectF(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius, centerY + outerRadius);
        for (int i = 0; i < 6; i++) {
            float startAngle = -time * 30 + i * 60;
            float sweepAngle = 40 + 15 * (float) Math.sin(time * 1.5f + i);
            pulsePaint.setAlpha(100 + (int) (40 * Math.sin(time * 2 + i)));
            canvas.drawArc(outerOval, startAngle, sweepAngle, false, pulsePaint);
        }
    }

    private void drawCenterDetails(Canvas canvas) {
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(CORE_BRIGHT);

        float dotOrbitRadius = baseRadius * 0.25f;
        for (int i = 0; i < 8; i++) {
            float angle = (float) Math.toRadians(i * 45 + time * 60);
            float x = centerX + dotOrbitRadius * (float) Math.cos(angle);
            float y = centerY + dotOrbitRadius * (float) Math.sin(angle);
            dotPaint.setAlpha(150 + (int) (100 * Math.sin(time * 3 + i)));
            canvas.drawCircle(x, y, 2.5f, dotPaint);
        }

        Paint hexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hexPaint.setStyle(Paint.Style.STROKE);
        hexPaint.setStrokeWidth(1.5f);
        hexPaint.setColor(WAVE_COLOR);
        hexPaint.setAlpha(60 + (int) (30 * Math.sin(time)));

        Path hexPath = new Path();
        float hexRadius = baseRadius * 0.45f;
        for (int i = 0; i < 6; i++) {
            float angle = (float) Math.toRadians(60 * i + time * 10);
            float x = centerX + hexRadius * (float) Math.cos(angle);
            float y = centerY + hexRadius * (float) Math.sin(angle);
            if (i == 0) hexPath.moveTo(x, y);
            else hexPath.lineTo(x, y);
        }
        hexPath.close();
        canvas.drawPath(hexPath, hexPaint);
    }

    public void setMining(boolean mining) {
        this.isMining = mining;
        invalidate();
    }

    public boolean isMining() {
        return isMining;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1.0f : 0.5f);
    }
}

