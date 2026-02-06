package game;

public class GameState {
    private boolean xTurn = true;
    private int moveCount = 0;
    private int ghostCount = 0;
    private boolean triangleExists = true;
    private boolean squareExists = true;
    private Boolean triangleOwnerIsX = null;
    private Boolean squareOwnerIsX = null;

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
        if (!squareExists) return false;
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
