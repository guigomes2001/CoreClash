package com.example.coreclash.libgdx;

import com.example.coreclash.core.domain.MatchStatePort;

import java.util.ArrayList;
import java.util.List;

import enums.DomainGameMode;
import game.GameState;
import manager.GameManager;
import util.NullUtil;

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
        return NullUtil.isNull(src) ? new ArrayList<>() : src;
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
        String mode = gameState.getGameMode().equalsIgnoreCase(DomainGameMode.RANKED.getValue()) ? "RANK" : "CASUAL";
        String turn = gameState.isXTurn() ? "X" : "O";
        return "Core Clash • " + mode + " • TURN " + turn;
    }
}
