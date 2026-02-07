package com.example.coreclash.libgdx;

import com.example.coreclash.core.domain.MatchStatePort;

import java.util.ArrayList;
import java.util.List;

import game.GameState;
import manager.GameManager;

/**
 * Ponte Android -> game-core (LibGDX).
 * Mantém a leitura de estado desacoplada para renderização futura no módulo core.
 */
public class AndroidMatchStateBridge implements MatchStatePort {

    private final GameManager gameManager;
    private final GameState gameState;

    public AndroidMatchStateBridge(GameManager gameManager, GameState gameState) {
        this.gameManager = gameManager;
        this.gameState = gameState;
    }

    @Override
    public List<int[]> availableMoves() {
        List<int[]> src = gameManager.getAvailableMoves();
        return src == null ? new ArrayList<>() : src;
    }

    @Override
    public boolean isMatchOver() {
        return gameManager.isGameOver();
    }

    @Override
    public boolean isXTurn() {
        return gameState.isXTurn();
    }

    @Override
    public String statusLabel() {
        String mode = gameState.getGameMode() == GameState.GameMode.RANKED ? "RANK" : "CASUAL";
        String turn = gameState.isXTurn() ? "X" : "O";
        return "CC • " + mode + " • TURN " + turn;
    }
}
