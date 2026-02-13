package com.example.coreclash.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import util.NullUtil;

public class VictoryLineView extends View {
    private final Paint paint;
    private float startX, startY, endX, endY;
    private float altStartX, altStartY, altEndX, altEndY;
    private float progress = 0f;
    private boolean shouldDraw = false;
    private boolean drawDouble = false;
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
        this.drawDouble = false;
        paint.setColor(Color.parseColor("#FBC02D"));
        paint.setShadowLayer(20, 0, 0, Color.parseColor("#FBC02D"));
        startAnim();
    }

    public void setDrawData(float s1X, float s1Y, float e1X, float e1Y,
                            float s2X, float s2Y, float e2X, float e2Y) {
        this.startX = s1X;
        this.startY = s1Y;
        this.endX = e1X;
        this.endY = e1Y;
        this.altStartX = s2X;
        this.altStartY = s2Y;
        this.altEndX = e2X;
        this.altEndY = e2Y;
        this.drawDouble = true;
        paint.setColor(Color.parseColor("#A78BFA"));
        paint.setShadowLayer(18, 0, 0, Color.parseColor("#A78BFA"));
        startAnim();
    }

    private void startAnim() {
        this.shouldDraw = true;
        this.progress = 0f;

        if (!NullUtil.isNull(animator)) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(460);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void clear() {
        if (!NullUtil.isNull(animator)) {
            animator.cancel();
        }
        this.shouldDraw = false;
        this.progress = 0f;
        this.drawDouble = false;
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

        if (drawDouble && progress > 0.35f) {
            float p2 = (progress - 0.35f) / 0.65f;
            float currentX2 = altStartX + (altEndX - altStartX) * p2;
            float currentY2 = altStartY + (altEndY - altStartY) * p2;
            canvas.drawLine(altStartX, altStartY, currentX2, currentY2, paint);
        }
    }
}
