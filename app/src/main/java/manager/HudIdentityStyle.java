package manager;

import android.graphics.Color;

import androidx.annotation.NonNull;

/**
 * Configura o estilo visual da identidade dos jogadores no HUD de turno.
 *
 * Mantido em classe própria para facilitar futura customização por skins/temas
 * (símbolos, cores, prefixos e outros ajustes visuais).
 */
public class HudIdentityStyle {

    public final int xSymbolColor;
    public final int oSymbolColor;
    public final float symbolRelativeSize;
    public final String localPipePrefix;

    public HudIdentityStyle(int xSymbolColor, int oSymbolColor, float symbolRelativeSize, @NonNull String localPipePrefix) {
        this.xSymbolColor = xSymbolColor;
        this.oSymbolColor = oSymbolColor;
        this.symbolRelativeSize = symbolRelativeSize;
        this.localPipePrefix = localPipePrefix;
    }

    @NonNull
    public static HudIdentityStyle defaults() {
        return new HudIdentityStyle(
                Color.parseColor("#FB7185"),
                Color.parseColor("#22D3EE"),
                1.18f,
                "│ "
        );
    }
}
