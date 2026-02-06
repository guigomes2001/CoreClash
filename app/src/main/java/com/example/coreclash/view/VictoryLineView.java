package com.example.coreclash.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

public class VictoryLineView extends View {
    private final Paint paint;
    private float startX, startY, endX, endY;
    private float progress = 0f;
    private boolean shouldDraw = false;
    @Nullable
    private ValueAnimator animator;

    public VictoryLineView(Context context, @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.parseColor("#FBC02D"));
        paint.setStrokeWidth(16f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        paint.setShadowLayer(20, 0, 0, Color.parseColor("#FBC02D"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(float sX, float sY, float eX, float eY) {
        this.startX = sX;
        this.startY = sY;
        this.endX = eX;
        this.endY = eY;
        this.shouldDraw = true;
        this.progress = 0f;

        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void clear() {
        if (animator != null) {
            animator.cancel();
        }
        this.shouldDraw = false;
        this.progress = 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!shouldDraw) {
            return;
        }

        float currentX = startX + (endX - startX) * progress;
        float currentY = startY + (endY - startY) * progress;
        canvas.drawLine(startX, startY, currentX, currentY, paint);
    }
}
