package com.example.coreclash.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class VictoryLineView extends View {
    private final Paint paint;
    private float startX, startY, endX, endY;
    private boolean shouldDraw = false;

    public VictoryLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.parseColor("#00FBFF"));
        paint.setStrokeWidth(20f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        paint.setShadowLayer(30, 0, 0, Color.parseColor("#00FBFF"));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(float sX, float sY, float eX, float eY) {
        this.startX = sX;
        this.startY = sY;
        this.endX = eX;
        this.endY = eY;
        this.shouldDraw = true;
        invalidate();
    }

    public void clear() {
        this.shouldDraw = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (shouldDraw) {
            canvas.drawLine(startX, startY, endX, endY, paint);
        }
    }
}