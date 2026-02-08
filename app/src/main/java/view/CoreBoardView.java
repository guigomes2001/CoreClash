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
    private final Paint boardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final String[][] boardMatrix = new String[3][3];
    private final float[][] cellScales = new float[3][3];

    private final RectF boardRect = new RectF();
    private final RectF tempRect = new RectF();
    private final Path trianglePath = new Path();

    private int boardColor = Color.WHITE;
    private String currentStyle = "DEFAULT";

    private int[][] effectCells = new int[0][0];
    private float abilityEffectProgress = 0f;

    public CoreBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boardFillPaint.setStyle(Paint.Style.FILL);

        boardStrokePaint.setStyle(Paint.Style.STROKE);
        boardStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        boardStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeCap(Paint.Cap.ROUND);
        symbolPaint.setStrokeJoin(Paint.Join.ROUND);

        ghostOverlayPaint.setStyle(Paint.Style.FILL);

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

    public void playAbilityEffect(int[][] targets) {
        effectCells = targets == null ? new int[0][0] : targets;
        ValueAnimator effect = ValueAnimator.ofFloat(0f, 1f);
        effect.setDuration(180);
        effect.addUpdateListener(animation -> {
            abilityEffectProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        effect.start();
    }

    private void animateCellPop(int row, int col) {
        ValueAnimator anim = ValueAnimator.ofFloat(0.7f, 1f);
        anim.setDuration(180);
        anim.setInterpolator(new OvershootInterpolator(1.2f));
        anim.addUpdateListener(animation -> {
            cellScales[row][col] = (float) animation.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft();
        float top = getPaddingTop();
        float right = getWidth() - getPaddingRight();
        float bottom = getHeight() - getPaddingBottom();
        if (right <= left || bottom <= top) return;

        boardRect.set(left, top, right, bottom);
        float cellW = boardRect.width() / 3f;
        float cellH = boardRect.height() / 3f;

        drawBoardBase(canvas, cellW, cellH);
        drawSymbols(canvas, cellW, cellH);
        drawAbilityEffects(canvas, cellW, cellH);
    }

    private void drawBoardBase(Canvas canvas, float cellW, float cellH) {
        boardFillPaint.setColor(Color.parseColor("#2B4367"));
        canvas.drawRoundRect(boardRect, 16f, 16f, boardFillPaint);

        float stroke = Math.max(3f, Math.min(cellW, cellH) * 0.06f);
        boardStrokePaint.setStrokeWidth(stroke);
        boardStrokePaint.setColor(resolveBoardAccentColor());

        for (int i = 1; i <= 2; i++) {
            float x = boardRect.left + cellW * i;
            float y = boardRect.top + cellH * i;
            canvas.drawLine(x, boardRect.top, x, boardRect.bottom, boardStrokePaint);
            canvas.drawLine(boardRect.left, y, boardRect.right, y, boardStrokePaint);
        }

        boardStrokePaint.setStrokeWidth(Math.max(2f, stroke * 0.9f));
        canvas.drawRoundRect(boardRect, 16f, 16f, boardStrokePaint);
    }

    private void drawSymbols(Canvas canvas, float cellW, float cellH) {
        float baseSize = Math.min(cellW, cellH) * 0.66f;
        float stroke = Math.max(4f, Math.min(cellW, cellH) * 0.08f);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String symbol = boardMatrix[r][c];
                if (symbol.isEmpty()) continue;

                boolean ghost = symbol.startsWith("GHOST_");
                String clean = ghost ? symbol.substring(6) : symbol;

                float cx = boardRect.left + c * cellW + cellW / 2f;
                float cy = boardRect.top + r * cellH + cellH / 2f;

                canvas.save();
                canvas.scale(cellScales[r][c], cellScales[r][c], cx, cy);
                drawSymbol(canvas, clean, cx, cy, baseSize, stroke, ghost ? 0.5f : 1f);
                if (ghost) {
                    ghostOverlayPaint.setColor(withAlpha(Color.parseColor("#101E33"), 0.18f));
                    tempRect.set(boardRect.left + c * cellW, boardRect.top + r * cellH,
                            boardRect.left + (c + 1) * cellW, boardRect.top + (r + 1) * cellH);
                    canvas.drawRect(tempRect, ghostOverlayPaint);
                }
                canvas.restore();
            }
        }
    }

    private void drawSymbol(Canvas canvas, String symbol, float cx, float cy, float size, float stroke, float alpha) {
        symbolPaint.setStrokeWidth(stroke);

        if ("X".equals(symbol) || "✦".equals(symbol) || "✕".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#D44E45"), alpha));
            float half = size / 2f;
            canvas.drawLine(cx - half, cy - half, cx + half, cy + half, symbolPaint);
            canvas.drawLine(cx + half, cy - half, cx - half, cy + half, symbolPaint);
            return;
        }

        if ("O".equals(symbol) || "◉".equals(symbol) || "⬡".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#4B89D2"), alpha));
            canvas.drawCircle(cx, cy, size * 0.49f, symbolPaint);
            return;
        }

        if ("△".equals(symbol) || "▲".equals(symbol) || "TRIANGLE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#D0A157"), alpha));
            trianglePath.reset();
            trianglePath.moveTo(cx, cy - size * 0.50f);
            trianglePath.lineTo(cx + size * 0.48f, cy + size * 0.40f);
            trianglePath.lineTo(cx - size * 0.48f, cy + size * 0.40f);
            trianglePath.close();
            canvas.drawPath(trianglePath, symbolPaint);
            return;
        }

        if ("▢".equals(symbol) || "□".equals(symbol) || "SQUARE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#5F9FCA"), alpha));
            float half = size * 0.47f;
            tempRect.set(cx - half, cy - half, cx + half, cy + half);
            canvas.drawRoundRect(tempRect, 8f, 8f, symbolPaint);
        }
    }

    private void drawAbilityEffects(Canvas canvas, float cellW, float cellH) {
        if (effectCells.length == 0 || abilityEffectProgress <= 0f || abilityEffectProgress >= 1f) {
            return;
        }

        float centerX = boardRect.centerX();
        float centerY = boardRect.centerY();
        Paint effect = symbolPaint;
        effect.setStyle(Paint.Style.STROKE);
        effect.setStrokeWidth(Math.max(2f, Math.min(cellW, cellH) * 0.06f));
        effect.setColor(withAlpha(Color.parseColor("#E9D8A6"), 1f - abilityEffectProgress));

        for (int[] cell : effectCells) {
            if (cell.length < 2) continue;
            int r = cell[0];
            int c = cell[1];
            if (r < 0 || r > 2 || c < 0 || c > 2) continue;

            float tx = boardRect.left + c * cellW + cellW / 2f;
            float ty = boardRect.top + r * cellH + cellH / 2f;

            canvas.drawLine(centerX, centerY, tx, ty, effect);

            ghostOverlayPaint.setColor(withAlpha(Color.parseColor("#F1E7C8"), 0.35f * (1f - abilityEffectProgress)));
            canvas.drawCircle(tx, ty, Math.min(cellW, cellH) * (0.16f + 0.2f * (1f - abilityEffectProgress)), ghostOverlayPaint);
        }
    }

    private int resolveBoardAccentColor() {
        if (boardColor != Color.WHITE) return boardColor;
        if ("RUNE".equals(currentStyle)) return Color.parseColor("#B69560");
        if ("FUTURE".equals(currentStyle)) return Color.parseColor("#87A8C7");
        return Color.parseColor("#C4D6E5");
    }

    private int withAlpha(int color, float alphaFactor) {
        int alpha = Math.max(0, Math.min(255, Math.round(255f * alphaFactor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setTheme(String themeId) {
        if ("ROYAL".equals(themeId)) {
            boardColor = Color.parseColor("#CFAE61");
        } else if ("VOID".equals(themeId)) {
            boardColor = Color.parseColor("#7A6B9E");
        } else {
            boardColor = Color.WHITE;
        }
        invalidate();
    }

    public void setSymbolStyle(String styleId) {
        currentStyle = styleId;
        invalidate();
    }
}
