package view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class VictoryLineView extends View {
    private Paint linePaint;
    private float animProgress = 0f;
    private float startX, startY, endX, endY;
    private boolean isVisible = false;

    private int colorStart = Color.CYAN;
    private int colorEnd = Color.parseColor("#0088FF");
    private int glowColor = Color.CYAN;

    public VictoryLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(18f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        updateGlow();
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setTheme(String themeId) {
        if ("ROYAL".equals(themeId)) {
            colorStart = Color.parseColor("#FFD700");
            colorEnd = Color.parseColor("#FFA500");
            glowColor = Color.YELLOW;
        } else if ("VOID".equals(themeId)) {
            colorStart = Color.parseColor("#9D00FF");
            colorEnd = Color.parseColor("#FF00E5");
            glowColor = Color.parseColor("#9D00FF");
        } else {
            colorStart = Color.CYAN;
            colorEnd = Color.parseColor("#0088FF");
            glowColor = Color.CYAN;
        }
        updateGlow();
        invalidate();
    }

    private void updateGlow() {
        linePaint.setShadowLayer(25, 0, 0, glowColor);
    }

    public void setData(int r1, int c1, int r2, int c2) {
        float cellSize = getWidth() / 3f;
        this.startX = (c1 * cellSize) + (cellSize / 2);
        this.startY = (r1 * cellSize) + (cellSize / 2);
        this.endX = (c2 * cellSize) + (cellSize / 2);
        this.endY = (r2 * cellSize) + (cellSize / 2);
        this.isVisible = true;
    }

    public void startVictoryAnimation() {
        animProgress = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void clear() {
        isVisible = false;
        animProgress = 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isVisible) return;

        linePaint.setShader(new LinearGradient(startX, startY, endX, endY,
                colorStart, colorEnd, Shader.TileMode.CLAMP));

        float currentX = startX + (endX - startX) * animProgress;
        float currentY = startY + (endY - startY) * animProgress;
        canvas.drawLine(startX, startY, currentX, currentY, linePaint);
    }
}