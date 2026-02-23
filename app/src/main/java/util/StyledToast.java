package util;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.coreclash.R;

public final class StyledToast {

    private StyledToast() {}

    public static void show(@NonNull Context context, @NonNull String message) {
        show(context, message, context.getString(R.string.fa_bolt), 0xFF6EE7FF);
    }

    public static void show(@NonNull Context context, @NonNull String message, @NonNull String iconGlyph, int iconColor) {
        View content = LayoutInflater.from(context).inflate(R.layout.toast_system_message, null, false);
        TextView icon = content.findViewById(R.id.txtToastIcon);
        TextView text = content.findViewById(R.id.txtToastMessage);
        icon.setText(iconGlyph);
        icon.setTextColor(iconColor);
        text.setText(message);

        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(content);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 24);
        toast.setMargin(0f, 0f);
        toast.show();
    }
}
