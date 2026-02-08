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
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final String[][] boardMatrix = new String[3][3];
    private final float[][] cellScales = new float[3][3];

    private final RectF boardRect = new RectF();
    private final RectF tempRect = new RectF();
    private final Path trianglePath = new Path();

    private float boardLeft;
    private float boardTop;
    private float boardSize;
    private float cellSize;

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

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeCap(Paint.Cap.ROUND);
        gridPaint.setStrokeJoin(Paint.Join.ROUND);

        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeCap(Paint.Cap.ROUND);
        symbolPaint.setStrokeJoin(Paint.Join.ROUND);

        effectPaint.setStyle(Paint.Style.STROKE);
        effectPaint.setStrokeCap(Paint.Cap.ROUND);
        effectPaint.setStrokeJoin(Paint.Join.ROUND);

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
        effect.setDuration(220);
        effect.addUpdateListener(animation -> {
            abilityEffectProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        effect.start();
    }

    private void animateCellPop(int r, int c) {
        ValueAnimator anim = ValueAnimator.ofFloat(0.6f, 1f);
        anim.setDuration(220);
        anim.setInterpolator(new OvershootInterpolator(1.5f));
        anim.addUpdateListener(animation -> {
            cellScales[r][c] = (float) animation.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateBoardGeometry();

        drawBoard(canvas);
        drawSymbols(canvas);
        drawAbilityEffects(canvas);
    }

    private void updateBoardGeometry() {
        int w = getWidth();
        int h = getHeight();
        boardSize = Math.min(w, h);
        boardLeft = (w - boardSize) / 2f;
        boardTop = (h - boardSize) / 2f;
        cellSize = boardSize / 3f;
        boardRect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
    }

    private void drawBoard(Canvas canvas) {
        boardFillPaint.setColor(Color.parseColor("#23395B"));
        canvas.drawRoundRect(boardRect, 22f, 22f, boardFillPaint);

        float stroke = Math.max(3f, boardSize * 0.012f);
        gridPaint.setStrokeWidth(stroke);
        gridPaint.setColor(resolveBoardAccentColor());

        for (int i = 1; i <= 2; i++) {
            float x = boardLeft + cellSize * i;
            float y = boardTop + cellSize * i;
            canvas.drawLine(x, boardTop + stroke, x, boardTop + boardSize - stroke, gridPaint);
            canvas.drawLine(boardLeft + stroke, y, boardLeft + boardSize - stroke, y, gridPaint);
        }

        gridPaint.setStrokeWidth(stroke * 1.1f);
        canvas.drawRoundRect(boardRect, 22f, 22f, gridPaint);
    }

    private void drawSymbols(Canvas canvas) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String symbol = boardMatrix[r][c];
                if (symbol.isEmpty()) continue;

                boolean ghost = symbol.startsWith("GHOST_");
                String cleanSymbol = ghost ? symbol.substring(6) : symbol;
                float alpha = ghost ? 0.5f : 1f;

                float cx = boardLeft + c * cellSize + cellSize / 2f;
                float cy = boardTop + r * cellSize + cellSize / 2f;
                float symbolSize = cellSize * 0.66f;

                canvas.save();
                canvas.scale(cellScales[r][c], cellScales[r][c], cx, cy);
                drawSymbol(canvas, cleanSymbol, cx, cy, symbolSize, alpha);
                canvas.restore();
            }
        }
    }

    private void drawSymbol(Canvas canvas, String symbol, float cx, float cy, float size, float alpha) {
        float stroke = Math.max(4f, cellSize * 0.08f);
        symbolPaint.setStrokeWidth(stroke);

        if ("X".equals(symbol) || "✦".equals(symbol) || "✕".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#D84E43"), alpha));
            float half = size / 2f;
            canvas.drawLine(cx - half, cy - half, cx + half, cy + half, symbolPaint);
            canvas.drawLine(cx + half, cy - half, cx - half, cy + half, symbolPaint);
            return;
        }

        if ("O".equals(symbol) || "◉".equals(symbol) || "⬡".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#4E8FDB"), alpha));
            canvas.drawCircle(cx, cy, size * 0.48f, symbolPaint);
            return;
        }

        if ("△".equals(symbol) || "▲".equals(symbol) || "TRIANGLE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#D19D3D"), alpha));
            trianglePath.reset();
            trianglePath.moveTo(cx, cy - size * 0.52f);
            trianglePath.lineTo(cx + size * 0.50f, cy + size * 0.43f);
            trianglePath.lineTo(cx - size * 0.50f, cy + size * 0.43f);
            trianglePath.close();
            canvas.drawPath(trianglePath, symbolPaint);
            return;
        }

        if ("▢".equals(symbol) || "□".equals(symbol) || "SQUARE".equals(symbol)) {
            symbolPaint.setColor(withAlpha(Color.parseColor("#5E9EC6"), alpha));
            float half = size * 0.47f;
            tempRect.set(cx - half, cy - half, cx + half, cy + half);
            canvas.drawRoundRect(tempRect, 8f, 8f, symbolPaint);
        }
    }

    private void drawAbilityEffects(Canvas canvas) {
        if (effectCells.length == 0 || abilityEffectProgress <= 0f || abilityEffectProgress >= 1f) {
            return;
        }

        float centerX = boardRect.centerX();
        float centerY = boardRect.centerY();

        for (int[] cell : effectCells) {
            if (cell.length < 2) continue;
            int r = cell[0];
            int c = cell[1];
            if (r < 0 || r > 2 || c < 0 || c > 2) continue;

            float targetX = boardLeft + c * cellSize + cellSize / 2f;
            float targetY = boardTop + r * cellSize + cellSize / 2f;

            effectPaint.setColor(withAlpha(Color.parseColor("#E9D8A6"), 1f - abilityEffectProgress));
            effectPaint.setStrokeWidth(Math.max(2f, cellSize * 0.05f));
            canvas.drawLine(centerX, centerY, targetX, targetY, effectPaint);

            float flash = cellSize * (0.20f + 0.25f * (1f - abilityEffectProgress));
            effectPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(targetX, targetY, flash, effectPaint);
            effectPaint.setStyle(Paint.Style.STROKE);
        }
    }

    private int resolveBoardAccentColor() {
        if (boardColor != Color.WHITE) {
            return boardColor;
        }
        if ("RUNE".equals(currentStyle)) {
            return Color.parseColor("#A8885A");
        }
        if ("FUTURE".equals(currentStyle)) {
            return Color.parseColor("#6A95B8");
        }
        return Color.parseColor("#8EA9C0");
    }

    private int withAlpha(int color, float alphaFactor) {
        int alpha = Math.max(0, Math.min(255, (int) (Color.alpha(color) * alphaFactor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setTheme(String themeId) {
        if (themeId.equals("ROYAL")) {
            this.boardColor = Color.parseColor("#CFAE61");
        } else if (themeId.equals("VOID")) {
            this.boardColor = Color.parseColor("#7A6B9E");
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
