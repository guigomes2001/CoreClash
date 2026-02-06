package game;

public class GameState {

    public enum GameMode {
        CASUAL,
        RANKED
    }

    public enum SymbolSkin {
        CLASSIC,
        NEON,
        GLITCH
    }

    private static final int SQUARE_UNLOCK_MOVE = 4;

    private boolean xTurn = true;
    private int moveCount = 0;
    private int ghostCount = 0;
    private boolean triangleExists = true;
    private boolean squareExists = true;
    private Boolean triangleOwnerIsX = null;
    private Boolean squareOwnerIsX = null;

    private GameMode gameMode = GameMode.CASUAL;
    private SymbolSkin symbolSkin = SymbolSkin.CLASSIC;
    private int rankedPoints = 0;
    private int totalWins = 0;
    private int winStreak = 0;
    private int bestWinStreak = 0;

    public boolean isXTurn() {
        return xTurn;
    }

    public void nextTurn() {
        xTurn = !xTurn;
    }

    public boolean canUseTriangle() {
        if (!triangleExists) return false;
        return triangleOwnerIsX == null || triangleOwnerIsX == xTurn;
    }

    public boolean canUseSquare() {
        if (!squareExists || moveCount < SQUARE_UNLOCK_MOVE) {
            return false;
        }
        return squareOwnerIsX == null || squareOwnerIsX == xTurn;
    }

    public int getSquareUnlockMove() {
        return SQUARE_UNLOCK_MOVE;
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

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public SymbolSkin getSymbolSkin() {
        return symbolSkin;
    }

    public void setSymbolSkin(SymbolSkin symbolSkin) {
        this.symbolSkin = symbolSkin;
    }

    public int getRankedPoints() {
        return rankedPoints;
    }

    public int getTotalWins() {
        return totalWins;
    }

    public int getWinStreak() {
        return winStreak;
    }

    public int getBestWinStreak() {
        return bestWinStreak;
    }

    public String getRankLabel() {
        if (rankedPoints >= 120) return "CORE MASTER";
        if (rankedPoints >= 80) return "CORE ELITE";
        if (rankedPoints >= 40) return "CORE VETERAN";
        if (rankedPoints >= 10) return "CORE RECRUIT";
        return "CORE ROOKIE";
    }

    public void registerWin() {
        totalWins++;
        winStreak++;
        bestWinStreak = Math.max(bestWinStreak, winStreak);

        if (gameMode == GameMode.RANKED) {
            int base = 10;
            int moveBonus = Math.max(0, 9 - moveCount);
            int ghostPenalty = Math.max(0, ghostCount / 2);
            rankedPoints += Math.max(3, base + moveBonus - ghostPenalty);
        }
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
    }
}
