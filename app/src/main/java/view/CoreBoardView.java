package view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import com.example.coreclash.R;

public class CoreBoardView extends View {
    private Bitmap bitmapX, bitmapO, bitmapTriangle, bitmapSquare, bitmapBoard;
    private Paint paint;
    private Rect destRect = new Rect();
    private String[][] boardMatrix = new String[3][3];
    private float[][] cellScales = new float[3][3];

    // Variáveis para controle de tema
    private int boardColor = Color.WHITE;
    private String currentStyle = "DEFAULT";

    public CoreBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);

        // Carregamento inicial
        loadResources();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boardMatrix[i][j] = "";
                cellScales[i][j] = 1.0f;
            }
        }
    }

    private void loadResources() {
        bitmapX = BitmapFactory.decodeResource(getResources(), R.drawable.icon_x);
        bitmapO = BitmapFactory.decodeResource(getResources(), R.drawable.icon_o);
        bitmapTriangle = BitmapFactory.decodeResource(getResources(), R.drawable.icon_triangle);
        bitmapSquare = BitmapFactory.decodeResource(getResources(), R.drawable.icon_square);
        bitmapBoard = BitmapFactory.decodeResource(getResources(), R.drawable.icon_board);
    }

    public void updateBoard(String[][] newMatrix) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (!newMatrix[r][c].equals("") && boardMatrix[r][c].equals("")) {
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

        // Aplicar cor do tema ao tabuleiro se necessário
        if (boardColor != Color.WHITE) {
            paint.setColorFilter(new PorterDuffColorFilter(boardColor, PorterDuff.Mode.SRC_IN));
        } else {
            paint.setColorFilter(null);
        }

        if (bitmapBoard != null) {
            canvas.drawBitmap(bitmapBoard, null, new Rect(0, 0, w, h), paint);
        }

        // Limpar filtro para os ícones (ou manter se quiser que eles brilhem na cor do tema)
        paint.setColorFilter(null);

        int cellSize = w / 3;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String symbol = boardMatrix[r][c];
                if (symbol.isEmpty()) continue;

                boolean ghost = symbol.startsWith("GHOST_");
                String cleanSymbol = ghost ? symbol.substring(6) : symbol;

                Bitmap icon = resolveIcon(cleanSymbol);
                if (icon == null) continue;

                int oldAlpha = paint.getAlpha();
                paint.setAlpha(ghost ? 120 : 255);
                drawAnimatedIcon(canvas, icon, r, c, cellSize);
                paint.setAlpha(oldAlpha);
            }
        }
    }


    private Bitmap resolveIcon(String symbol) {
        if ("X".equals(symbol) || "✦".equals(symbol) || "✕".equals(symbol)) {
            return bitmapX;
        }
        if ("O".equals(symbol) || "◉".equals(symbol) || "⬡".equals(symbol)) {
            return bitmapO;
        }
        if ("△".equals(symbol) || "▲".equals(symbol) || "TRIANGLE".equals(symbol)) {
            return bitmapTriangle;
        }
        if ("▢".equals(symbol) || "□".equals(symbol) || "SQUARE".equals(symbol)) {
            return bitmapSquare;
        }
        return null;
    }

    private void drawAnimatedIcon(Canvas canvas, Bitmap icon, int r, int c, int cellSize) {
        int padding = cellSize / 6;
        float scale = cellScales[r][c];
        int left = (c * cellSize) + padding;
        int top = (r * cellSize) + padding;
        int size = cellSize - (padding * 2);

        destRect.set(left, top, left + size, top + size);

        canvas.save();
        canvas.scale(scale, scale, destRect.centerX(), destRect.centerY());
        canvas.drawBitmap(icon, null, destRect, paint);
        canvas.restore();
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