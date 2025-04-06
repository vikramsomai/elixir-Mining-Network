package network.lynx.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CircleProgressView extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint indicatorPaint;
    private RectF rectF;
    private float progress = 0.25f; // 25% progress

    public CircleProgressView(Context context) {
        super(context);
        init();
    }

    public CircleProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(15f);

        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setColor(Color.parseColor("#FFBB33"));
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(15f);

        indicatorPaint = new Paint();
        indicatorPaint.setAntiAlias(true);
        indicatorPaint.setColor(Color.parseColor("#2196F3"));
        indicatorPaint.setStyle(Paint.Style.FILL);

        rectF = new RectF();

        // Start with 0 progress
        progress = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float radius = Math.min(width, height) / 2 - backgroundPaint.getStrokeWidth() / 2;

        rectF.set(width / 2 - radius, height / 2 - radius, width / 2 + radius, height / 2 + radius);

        // Draw background circle
        canvas.drawCircle(width / 2, height / 2, radius, backgroundPaint);

        // Draw progress arc - only if there's progress to show
        if (progress > 0) {
            float sweepAngle = 360 * progress;
            canvas.drawArc(rectF, -90, sweepAngle, false, progressPaint);

            // Draw indicator dot
            float angle = (float) Math.toRadians(-90 + sweepAngle);
            float dotX = (float) (width / 2 + radius * Math.cos(angle));
            float dotY = (float) (height / 2 + radius * Math.sin(angle));
            canvas.drawCircle(dotX, dotY, 8, indicatorPaint);
        }
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }
}

