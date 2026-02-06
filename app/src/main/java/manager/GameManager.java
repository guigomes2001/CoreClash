package manager;

import game.Cell;
import game.GameState;

public class GameManager {

    public record WinInfo(int r1, int c1, int r3, int c3) { }

    private final BoardManager board;
    private final GameState state;
    private boolean isGameOver = false;
    private WinInfo lastWin;

    public GameManager(BoardManager board, GameState state) {
        this.board = board;
        this.state = state;
    }

    public boolean play(int row, int col) {
        if (isGameOver) {
            return false;
        }

        Cell cellLogic = board.getCellLogic(row, col);
        if (!cellLogic.isEmpty() && !cellLogic.isGhost()) {
            return false;
        }

        state.addMove();
        String symbol = state.isXTurn() ? "X" : "O";
        cellLogic.setSymbol(symbol);
        board.updateCellVisual(row, col, state.isXTurn());

        if (checkWinner()) {
            isGameOver = true;
            state.registerWin();
            return true;
        }

        state.nextTurn();
        return false;
    }

    public WinInfo getLastWin() {
        return lastWin;
    }

    private boolean checkWinner() {
        for (int i = 0; i < 3; i++) {
            if (checkLine(i, 0, i, 1, i, 2)) {
                lastWin = new WinInfo(i, 0, i, 2);
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (checkLine(0, i, 1, i, 2, i)) {
                lastWin = new WinInfo(0, i, 2, i);
                return true;
            }
        }
        if (checkLine(0, 0, 1, 1, 2, 2)) {
            lastWin = new WinInfo(0, 0, 2, 2);
            return true;
        }
        if (checkLine(0, 2, 1, 1, 2, 0)) {
            lastWin = new WinInfo(0, 2, 2, 0);
            return true;
        }
        return false;
    }

    private boolean checkLine(int r1, int c1, int r2, int c2, int r3, int c3) {
        Cell cell1 = board.getCellLogic(r1, c1);
        Cell cell2 = board.getCellLogic(r2, c2);
        Cell cell3 = board.getCellLogic(r3, c3);

        if (cell1.isGhost() || cell1.isEmpty() || cell2.isGhost() || cell2.isEmpty() || cell3.isGhost() || cell3.isEmpty()) {
            return false;
        }

        String s1 = cell1.getVisualSymbol();
        return s1.equals(cell2.getVisualSymbol()) && s1.equals(cell3.getVisualSymbol());
    }

    public void useTriangle() {
        if (isGameOver || !state.canUseTriangle()) {
            return;
        }
        int affected = board.applyTriangleEffect();
        state.addGhosts(affected);
        state.triggerTriangleUsed();
    }

    public void useSquare() {
        if (isGameOver || !state.canUseSquare()) {
            return;
        }
        int affected = board.applySquareEffect();
        state.addGhosts(affected);
        state.triggerSquareUsed();
    }

    public void resetGame() {
        isGameOver = false;
        lastWin = null;
        board.resetBoard();
        state.reset();
    }

    public String getCurrentPlayerSymbol() {
        return state.isXTurn() ? "X" : "O";
    }

    public int getFinalMoves() {
        return state.getMoveCount();
    }

    public int getFinalGhosts() {
        return state.getGhostCount();
    }
}
