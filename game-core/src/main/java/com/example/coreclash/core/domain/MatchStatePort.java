package com.example.coreclash.core.domain;

import java.util.List;

/**
 * Porta de estado do jogo para render/sistemas visuais.
 * Implementação ficará no host Android (ponte para GameManager/GameState existentes).
 */
public interface MatchStatePort {
    List<int[]> availableMoves();
    boolean isMatchOver();
    boolean isXTurn();
    String statusLabel();
}
