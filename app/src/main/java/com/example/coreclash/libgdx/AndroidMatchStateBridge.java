package com.example.coreclash.libgdx;

import com.example.coreclash.core.domain.MatchStatePort;

import java.util.Collections;
import java.util.List;

/**
 * Bridge inicial Android -> game-core (LibGDX).
 *
 * Próximo passo: conectar GameManager/GameState reais.
 */
public class AndroidMatchStateBridge implements MatchStatePort {
    @Override
    public List<int[]> availableMoves() {
        return Collections.emptyList();
    }

    @Override
    public boolean isMatchOver() {
        return false;
    }

    @Override
    public boolean isXTurn() {
        return true;
    }

    @Override
    public String statusLabel() {
        return "CORE CLASH";
    }
}
