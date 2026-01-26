package manager;

import enums.Symmetries;
import game.GameState;

public class SymmetryManager {

    private GameState gameState;
    private BoardManager boardManager;

    public SymmetryManager(GameState gameState, BoardManager boardManager) {
        this.gameState = gameState;
        this.boardManager = boardManager;
    }

    public boolean activate(Symmetries symmetry) {
        if (symmetry == Symmetries.SQUARE) {
            if (!gameState.canUseSquare()) {
                return false;
            }
            boardManager.applySquareEffect();
            gameState.triggerSquareUsed();
            return true;
        }

        if (symmetry == Symmetries.TRIANGLE) {
            if (!gameState.canUseTriangle()) {
                return false;
            }
            boardManager.applyTriangleEffect();
            gameState.triggerTriangleUsed();
            return true;
        }

        return false;
    }
}