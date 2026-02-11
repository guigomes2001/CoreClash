package manager;

import android.graphics.Color;

import androidx.annotation.NonNull;

public class HudIdentityStyle {

    public final int xSymbolColor;
    public final int oSymbolColor;
    public final float symbolRelativeSize;
    public final int localMarkerColor;
    public final String localPipePrefix;
    public final String nameSymbolSeparator;

    public HudIdentityStyle(
            int xSymbolColor,
            int oSymbolColor,
            float symbolRelativeSize,
            int localMarkerColor,
            @NonNull String localPipePrefix,
            @NonNull String nameSymbolSeparator
    ) {
        this.xSymbolColor = xSymbolColor;
        this.oSymbolColor = oSymbolColor;
        this.symbolRelativeSize = symbolRelativeSize;
        this.localMarkerColor = localMarkerColor;
        this.localPipePrefix = localPipePrefix;
        this.nameSymbolSeparator = nameSymbolSeparator;
    }

    @NonNull
    public static HudIdentityStyle defaults() {
        return new HudIdentityStyle(
                Color.parseColor("#FB7185"),
                Color.parseColor("#22D3EE"),
                1.22f,
                Color.parseColor("#C7D2FE"),
                "│ ",
                "  ·  "
        );
    }
}
