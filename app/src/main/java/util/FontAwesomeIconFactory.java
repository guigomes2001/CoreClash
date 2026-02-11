package util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.example.coreclash.R;

public final class FontAwesomeIconFactory {

    private static Typeface cachedTypeface;

    private FontAwesomeIconFactory() {
    }

    public static void applyTopIcon(
            @NonNull Button button,
            @NonNull String glyph,
            int iconSizeDp,
            @ColorInt int iconColor,
            int iconBottomPaddingDp
    ) {
        Drawable icon = createDrawable(button.getContext(), glyph, iconSizeDp, iconColor);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon, null, null);
        int paddingPx = dp(button.getContext(), iconBottomPaddingDp);
        button.setCompoundDrawablePadding(paddingPx);
    }

    @NonNull
    public static Drawable createDrawable(
            @NonNull Context context,
            @NonNull String glyph,
            int iconSizeDp,
            @ColorInt int iconColor
    ) {
        int sizePx = dp(context, iconSizeDp);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(getTypeface(context));
        paint.setColor(iconColor);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sizePx * 0.9f);

        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float baseline = (sizePx - fontMetrics.bottom - fontMetrics.top) / 2f;

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(glyph, sizePx / 2f, baseline, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    @NonNull
    private static Typeface getTypeface(@NonNull Context context) {
        if (cachedTypeface == null) {
            cachedTypeface = ResourcesCompat.getFont(context, R.font.fa_solid_900);
            if (cachedTypeface == null) {
                cachedTypeface = Typeface.DEFAULT_BOLD;
            }
        }
        return cachedTypeface;
    }

    private static int dp(@NonNull Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
