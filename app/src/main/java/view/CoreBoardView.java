package view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

public class CoreBoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[][] boardMatrix = new String[3][3];
    private final float[][] cellScales = new float[3][3];

    private int boardColor = Color.WHITE;
    private String currentStyle = "DEFAULT";

    public CoreBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeCap(Paint.Cap.ROUND);
        symbolPaint.setStrokeJoin(Paint.Join.ROUND);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                boardMatrix[r][c] = "";
                cellScales[r][c] = 1f;
            }
        }
    }

    public void updateBoard(String[][] newMatrix) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (!newMatrix[r][c].isEmpty() && boardMatrix[r][c].isEmpty()) {
                    animateCellPop(r, c);
                }
                boardMatrix[r][c] = newMatrix[r][c];
            }
        }
        invalidate();
    }

    private void animateCellPop(int r, int c) {
        ValueAnimator anim = ValueAnimator.ofFloat(0.5f, 1f);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator(2.0f));
        anim.addUpdateListener(animation -> {
            cellScales[r][c] = (float) animation.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        float stroke = Math.max(4f, size * 0.015f);

        drawBoard(canvas, left, top, size, stroke);
        drawSymbols(canvas, left, top, size);
    }

    private void drawBoard(Canvas canvas, float left, float top, float size, float stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0D1C42"));
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), 24f, 24f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(resolveBoardAccentColor());

        float cell = size / 3f;
        for (int i = 1; i <= 2; i++) {
            float x = left + cell * i;
            float y = top + cell * i;
            canvas.drawLine(x, top + stroke, x, top + size - stroke, paint);
            canvas.drawLine(left + stroke, y, left + size - stroke, y, paint);
        }

        paint.setStrokeWidth(stroke * 0.9f);
        canvas.drawRoundRect(new RectF(left, top, left + size, top + size), 24f, 24f, paint);
    }

    private void drawSymbols(Canvas canvas, float boardLeft, float boardTop, float boardSize) {
        float cell = boardSize / 3f;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String symbol = boardMatrix[r][c];
                if (symbol.isEmpty()) continue;

                boolean ghost = symbol.startsWith("GHOST_");
                String cleanSymbol = ghost ? symbol.substring(6) : symbol;
                float alpha = ghost ? 0.45f : 1f;

                float cx = boardLeft + c * cell + cell / 2f;
                float cy = boardTop + r * cell + cell / 2f;
                float iconSize = cell * 0.62f;

                canvas.save();
                canvas.scale(cellScales[r][c], cellScales[r][c], cx, cy);
                drawSymbol(canvas, cleanSymbol, cx, cy, iconSize, alpha);
                canvas.restore();
            }
        }
    }

    private void drawSymbol(Canvas canvas, String symbol, float cx, float cy, float size, float alpha) {
        symbolPaint.setStrokeWidth(Math.max(6f, size * 0.12f));

        if ("X".equals(symbol) || "✦".equals(symbol) || "✕".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#FF4A5F"), alpha));
            float d = size / 2f;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, symbolPaint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, symbolPaint);
            return;
        }

        if ("O".equals(symbol) || "◉".equals(symbol) || "⬡".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#38D5FF"), alpha));
            canvas.drawCircle(cx, cy, size * 0.45f, symbolPaint);
            return;
        }

        if ("△".equals(symbol) || "▲".equals(symbol) || "TRIANGLE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#FFC84A"), alpha));
            Path triangle = new Path();
            triangle.moveTo(cx, cy - size * 0.48f);
            triangle.lineTo(cx + size * 0.48f, cy + size * 0.4f);
            triangle.lineTo(cx - size * 0.48f, cy + size * 0.4f);
            triangle.close();
            canvas.drawPath(triangle, symbolPaint);
            return;
        }

        if ("▢".equals(symbol) || "□".equals(symbol) || "SQUARE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#7DE0FF"), alpha));
            float half = size * 0.43f;
            canvas.drawRoundRect(new RectF(cx - half, cy - half, cx + half, cy + half), 10f, 10f, symbolPaint);
        }
    }

    private int resolveBoardAccentColor() {
        if (boardColor != Color.WHITE) {
            return boardColor;
        }
        if ("RUNE".equals(currentStyle)) {
            return Color.parseColor("#B288FF");
        }
        if ("FUTURE".equals(currentStyle)) {
            return Color.parseColor("#4DD7FF");
        }
        return Color.parseColor("#5FAEFF");
    }

    private int withAlpha(int color, float alphaFactor) {
        int alpha = Math.max(0, Math.min(255, (int) (Color.alpha(color) * alphaFactor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setTheme(String themeId) {
        if (themeId.equals("ROYAL")) {
            this.boardColor = Color.parseColor("#FFD700");
        } else if (themeId.equals("VOID")) {
            this.boardColor = Color.parseColor("#7000FF");
        } else {
            this.boardColor = Color.WHITE;
        }
        invalidate();
    }

    public void setSymbolStyle(String styleId) {
        this.currentStyle = styleId;
        invalidate();
    }
}
