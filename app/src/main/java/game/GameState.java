package game;

import enums.DomainGameMode;

public class GameState {

    private static final int TRIANGLE_UNLOCK_MOVE = 3;
    private static final int SQUARE_UNLOCK_MOVE = 4;

    private boolean xTurn = true;
    private int moveCount = 0;
    private int ghostCount = 0;
    private boolean triangleExists = true;
    private boolean squareExists = true;
    private Boolean triangleOwnerIsX = null;
    private Boolean squareOwnerIsX = null;

    private String gameMode = String.valueOf(DomainGameMode.CASUAL);
    private int totalWins = 0;
    private int winStreak = 0;
    private int bestWinStreak = 0;
    int timeoutStreakX;
    int timeoutStreakO;

    public boolean isXTurn() {
        return xTurn;
    }

    public void nextTurn() {
        xTurn = !xTurn;
    }

    public boolean canUseTriangle() {
        if (!triangleExists || moveCount < TRIANGLE_UNLOCK_MOVE) return false;
        return triangleOwnerIsX == null || triangleOwnerIsX == xTurn;
    }

    public boolean canUseSquare() {
        if (!squareExists || moveCount < SQUARE_UNLOCK_MOVE) {
            return false;
        }
        return squareOwnerIsX == null || squareOwnerIsX == xTurn;
    }

    public void triggerTriangleUsed() {
        triangleExists = false;
        if (squareExists) {
            squareOwnerIsX = !xTurn;
        }
    }

    public void triggerSquareUsed() {
        squareExists = false;
        if (triangleExists) {
            triangleOwnerIsX = !xTurn;
        }
    }

    public void addMove() {
        moveCount++;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public void addGhosts(int amount) {
        ghostCount += Math.max(0, amount);
    }

    public int getGhostCount() {
        return ghostCount;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public int getWinStreak() {
        return winStreak;
    }

    public void setXTurn(boolean xTurn) {
        this.xTurn = xTurn;
    }

    public void registerWin() {
        totalWins++;
        winStreak++;
        bestWinStreak = Math.max(bestWinStreak, winStreak);
    }

    public void registerLossOrDraw() {
        winStreak = 0;
    }

    public void reset() {
        xTurn = true;
        moveCount = 0;
        ghostCount = 0;
        triangleExists = true;
        squareExists = true;
        triangleOwnerIsX = null;
        squareOwnerIsX = null;
        timeoutStreakX = 0;
        timeoutStreakO = 0;
    }

    public boolean registerTimeout(boolean xTimedOut) {
        if (xTimedOut) {
            timeoutStreakX++;
        } else {
            timeoutStreakO++;
        }
        return (xTimedOut ? timeoutStreakX : timeoutStreakO) >= 3;
    }

    public void resetTimeoutStreak(boolean xSide) {
        if (xSide) {
            timeoutStreakX = 0;
        } else {
            timeoutStreakO = 0;
        }
    }
}
