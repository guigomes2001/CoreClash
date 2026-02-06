package manager;

import static game.SymmetriesConfig.COLOR_DEAD;
import static game.SymmetriesConfig.COLOR_SQUARE;
import static game.SymmetriesConfig.COLOR_TRIANGLE;
import static game.SymmetriesConfig.STROKE_WIDTH;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import game.Cell;

public class BoardManager {

    private final FrameLayout[][] cellContainers = new FrameLayout[3][3];
    private final TextView[][] symbolViews = new TextView[3][3];
    private final Cell[][] cells = new Cell[3][3];
    private Context context;
    private ShapeOverlayView overlayView;



    public interface CellClickListener {
        void onCellClick(int row, int col);
    }

    public void createBoard(Context context, GridLayout grid, CellClickListener listener) {
        this.context = context;
        grid.removeAllViews();

        setupOverlay(grid);

        int cellSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 85, context.getResources().getDisplayMetrics());

        int margin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c] = new Cell();
                FrameLayout container = new FrameLayout(context);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(margin, margin, margin, margin);
                container.setLayoutParams(params);

                applyInitialStyle(container);
                TextView tv = styleText(context);

                final int row = r;
                final int col = c;
                container.setOnClickListener(v -> listener.onCellClick(row, col));

                container.addView(tv);
                cellContainers[r][c] = container;
                symbolViews[r][c] = tv;
                grid.addView(container);
            }
        }
    }

    private void setupOverlay(GridLayout grid) {
        ViewGroup parent = (ViewGroup) grid.getRootView().findViewById(android.R.id.content);
        if (parent == null) {
            parent = (ViewGroup) grid.getParent();
            while (parent.getParent() instanceof ViewGroup && ((ViewGroup) parent.getParent()).getId() != android.view.View.NO_ID) {
                parent = (ViewGroup) parent.getParent();
            }
        }

        if (overlayView != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }

        overlayView = new ShapeOverlayView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        parent.addView(overlayView, params);
        overlayView.bringToFront();
    }

    @NonNull
    private static TextView styleText(Context context) {
        TextView tv = new TextView(context);
        tv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(40f);
        tv.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        return tv;
    }

    private void applyInitialStyle(FrameLayout container) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(16f);
        gd.setColor(Color.parseColor("#1E1E24"));
        gd.setStroke(2, Color.parseColor("#33FFFFFF"));
        container.setBackground(gd);
        container.setAlpha(1.0f);
        container.setTranslationX(0f);
    }

    public void updateCellVisual(int r, int c, boolean isX) {
        TextView tv = symbolViews[r][c];
        tv.setText(cells[r][c].getVisualSymbol());

        int color = Color.parseColor(isX ? "#FF4444" : "#00FFFF");
        tv.setTextColor(color);
        tv.setShadowLayer(20, 0, 0, color);

        tv.setScaleX(0f);
        tv.setScaleY(0f);
        tv.animate().scaleX(1f).scaleY(1f).setDuration(250).start();

        updateCellGhostState(r, c);
    }

    public int applyTriangleEffect() {
        List<PointF> points = new ArrayList<>();
        points.add(getCellCenter(0, 1));
        points.add(getCellCenter(2, 2));
        points.add(getCellCenter(2, 0));
        points.add(getCellCenter(0, 1));

        overlayView.drawShape(points, COLOR_TRIANGLE);

        long duration = 600;
        long step = duration / 3;

        int affected = 0;
        affected += affectCellNow(0, 1);
        affected += affectCellNow(2, 2);
        affected += affectCellNow(2, 0);

        scheduleAffectCellAnimation(0, 1, step);
        scheduleAffectCellAnimation(2, 2, step * 2);
        scheduleAffectCellAnimation(2, 0, step * 3);
        return affected;
    }

    public int applySquareEffect() {
        List<PointF> points = new ArrayList<>();
        points.add(getCellCenter(0, 0));
        points.add(getCellCenter(0, 2));
        points.add(getCellCenter(2, 2));
        points.add(getCellCenter(2, 0));
        points.add(getCellCenter(0, 0));

        overlayView.drawShape(points, COLOR_SQUARE);

        int affected = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (r == 1 && c == 1) continue;
                affected += affectCellNow(r, c);
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (r == 1 && c == 1) continue;
                    updateCellVisualAfterSkill(r, c);
                }
            }
        }, 400);
        return affected;
    }

    private void scheduleAffectCellAnimation(int r, int c, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> updateCellVisualAfterSkill(r, c), delay);
    }

    private int affectCellNow(int r, int c) {
        if (cells[r][c].isEmpty() || cells[r][c].isGhost()) {
            return 0;
        }
        cells[r][c].turnIntoGhost();
        return 1;
    }

    private void updateCellGhostState(int r, int c) {
        FrameLayout container = cellContainers[r][c];
        TextView tv = symbolViews[r][c];

        if (cells[r][c].isGhost()) {
            container.animate().alpha(0.3f).setDuration(300).start();
            tv.setShadowLayer(0, 0, 0, 0);
        } else {
            container.setAlpha(1.0f);
        }
    }

    private void updateCellVisualAfterSkill(int r, int c) {
        TextView tv = symbolViews[r][c];
        FrameLayout container = cellContainers[r][c];
        Cell logic = cells[r][c];

        tv.setText(logic.getVisualSymbol());

        if (logic.isGhost()) {
            container.setAlpha(0.5f);
            tv.setShadowLayer(0, 0, 0, 0);
            tv.setTextColor(Color.parseColor("#606060"));

            GradientDrawable gd = (GradientDrawable) container.getBackground();
            gd.setStroke(1, Color.parseColor("#303030"));
            gd.setColor(COLOR_DEAD);

            container.animate()
                    .translationX(5).setDuration(50)
                    .withEndAction(() -> container.animate().translationX(0).setDuration(50).start())
                    .start();
        } else {
            container.setAlpha(1.0f);
        }
    }

    public void resetBoard() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c].reset();
                symbolViews[r][c].setText("");
                symbolViews[r][c].setShadowLayer(0, 0, 0, 0);
                symbolViews[r][c].animate().cancel();
                symbolViews[r][c].setScaleX(1f);
                symbolViews[r][c].setScaleY(1f);
                applyInitialStyle(cellContainers[r][c]);
            }
        }
        overlayView.clear();
    }

    public Cell getCellLogic(int r, int c) {
        return cells[r][c];
    }

  private PointF getCellCenter(int r, int c) {
        FrameLayout cell = cellContainers[r][c];

        int[] cellLocation = new int[2];
        cell.getLocationOnScreen(cellLocation);

        int[] overlayLocation = new int[2];
        overlayView.getLocationOnScreen(overlayLocation);

        float x = cellLocation[0] - overlayLocation[0] + (cell.getWidth() / 2f);
        float y = cellLocation[1] - overlayLocation[1] + (cell.getHeight() / 2f);

        return new PointF(x, y);
    }

    private static class ShapeOverlayView extends View {
        private final Paint paint;
        private final Path path;
        private final Path drawingPath;
        private PathMeasure pathMeasure;
        private float pathLength;
        private ValueAnimator animator;

        public ShapeOverlayView(Context context) {
            super(context);
            paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(STROKE_WIDTH);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setAntiAlias(true);
            paint.setPathEffect(new CornerPathEffect(20f));

            path = new Path();
            drawingPath = new Path();
        }

        public void drawShape(List<PointF> points, int color) {
            if (points.isEmpty()) return;

            if (animator != null) animator.cancel();
            this.animate().cancel();

            drawingPath.reset();
            this.setAlpha(1f);

            paint.setColor(color);
            paint.setShadowLayer(15, 0, 0, color);

            path.reset();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) {
                path.lineTo(points.get(i).x, points.get(i).y);
            }

            pathMeasure = new PathMeasure(path, false);
            pathLength = pathMeasure.getLength();

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                float distance = val * pathLength;
                drawingPath.reset();
                pathMeasure.getSegment(0f, distance, drawingPath, true);
                invalidate();
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ShapeOverlayView.this.animate()
                            .alpha(0f)
                            .setDuration(400)
                            .setStartDelay(300)
                            .withEndAction(() -> {
                                drawingPath.reset();
                                ShapeOverlayView.this.setAlpha(1f);
                                invalidate();
                            })
                            .start();
                }
            });

            animator.start();
        }

        public void clear() {
            if (animator != null) animator.cancel();
            this.animate().cancel();
            drawingPath.reset();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(drawingPath, paint);
        }
    }
}