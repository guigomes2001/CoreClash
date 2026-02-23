package manager;

import java.util.ArrayList;
import java.util.List;

import game.Cell;
import game.GameState;

public class GameManager {

    public record WinInfo(int r1, int c1, int r3, int c3) { }

    private final BoardManager board;
    private final GameState state;
    private boolean isGameOver = false;
    private WinInfo lastWin;
    private final List<WinInfo> lastWins = new ArrayList<>();

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

        if (isBoardFull()) {
            isGameOver = true;
            state.registerLossOrDraw();
            return false;
        }

        state.nextTurn();
        return false;
    }

    public WinInfo getLastWin() {
        return lastWin;
    }

    public List<WinInfo> getLastWins() {
        return new ArrayList<>(lastWins);
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public boolean isBoardFull() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Cell cell = board.getCellLogic(r, c);
                if (cell.isEmpty() || cell.isGhost()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkWinner() {
        lastWins.clear();

        for (int i = 0; i < 3; i++) {
            if (checkLine(i, 0, i, 1, i, 2)) {
                lastWins.add(new WinInfo(i, 0, i, 2));
            }
        }
        for (int i = 0; i < 3; i++) {
            if (checkLine(0, i, 1, i, 2, i)) {
                lastWins.add(new WinInfo(0, i, 2, i));
            }
        }
        if (checkLine(0, 0, 1, 1, 2, 2)) {
            lastWins.add(new WinInfo(0, 0, 2, 2));
        }
        if (checkLine(0, 2, 1, 1, 2, 0)) {
            lastWins.add(new WinInfo(0, 2, 2, 0));
        }

        if (!lastWins.isEmpty()) {
            lastWin = lastWins.get(0);
            return true;
        }

        lastWin = null;
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

    public boolean useTriangle() {
        boolean tutorialOverride = state.isTutorialSkillOverride();
        boolean canApply = board.canApplyTriangleEffect();
        if (isGameOver || !state.canUseTriangle() || (!tutorialOverride && !canApply)) {
            return false;
        }
        int affected = canApply ? board.applyTriangleEffect() : 0;
        state.addGhosts(affected);
        state.triggerTriangleUsed();
        state.nextTurn();
        return true;
    }

    public boolean useSquare() {
        boolean tutorialOverride = state.isTutorialSkillOverride();
        boolean canApply = board.canApplySquareEffect();
        if (isGameOver || !state.canUseSquare() || (!tutorialOverride && !canApply)) {
            return false;
        }
        int affected = canApply ? board.applySquareEffect() : 0;
        state.addGhosts(affected);
        state.triggerSquareUsed();
        state.nextTurn();
        return true;
    }

    public void resetGame() {
        isGameOver = false;
        lastWin = null;
        lastWins.clear();
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

    public int getTotalWins() {
        return state.getTotalWins();
    }

    public int getWinStreak() {
        return state.getWinStreak();
    }

    public List<int[]> getAvailableMoves() {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Cell cell = board.getCellLogic(r, c);
                if (cell.isEmpty() || cell.isGhost()) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        return moves;
    }

    public int[] getCenterIfAvailable() {
        Cell center = board.getCellLogic(1, 1);
        if (center.isEmpty() || center.isGhost()) {
            return new int[]{1, 1};
        }
        return null;
    }

    public int[] findWinningMoveFor(String symbol) {
        for (int[] move : getAvailableMoves()) {
            int r = move[0];
            int c = move[1];
            Cell cell = board.getCellLogic(r, c);
            boolean wasGhost = cell.isGhost();
            String oldVisual = cell.getVisualSymbol();
            cell.setSymbol(symbol);
            boolean win = checkWinnerBySymbol(symbol);
            if (!oldVisual.isEmpty() && wasGhost) {
                cell.setSymbol(oldVisual);
                cell.turnIntoGhost();
            } else {
                cell.reset();
            }
            if (win) {
                return move;
            }
        }
        return null;
    }

    public int[] findBestMoveForO() {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;

        for (int[] move : getAvailableMoves()) {
            Cell cell = board.getCellLogic(move[0], move[1]);
            boolean wasGhost = cell.isGhost();
            String oldVisual = cell.getVisualSymbol();

            cell.setSymbol("O");
            int score = minimax(false, 0);

            if (!oldVisual.isEmpty() && wasGhost) {
                cell.setSymbol(oldVisual);
                cell.turnIntoGhost();
            } else {
                cell.reset();
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    public boolean canUseTriangleNow() {
        return !isGameOver && state.canUseTriangle() && (state.isTutorialSkillOverride() || board.canApplyTriangleEffect());
    }

    public boolean canUseSquareNow() {
        return !isGameOver && state.canUseSquare() && (state.isTutorialSkillOverride() || board.canApplySquareEffect());
    }

    private int minimax(boolean maximizing, int depth) {
        if (checkWinnerBySymbol("O")) return 10 - depth;
        if (checkWinnerBySymbol("X")) return depth - 10;
        if (getAvailableMoves().isEmpty()) return 0;

        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (int[] move : getAvailableMoves()) {
            Cell cell = board.getCellLogic(move[0], move[1]);
            cell.setSymbol(maximizing ? "O" : "X");
            int score = minimax(!maximizing, depth + 1);
            cell.reset();
            best = maximizing ? Math.max(best, score) : Math.min(best, score);
        }

        return best;
    }

    private boolean checkWinnerBySymbol(String symbol) {
        for (int i = 0; i < 3; i++) {
            if (lineOwnedBy(symbol, i, 0, i, 1, i, 2)) return true;
            if (lineOwnedBy(symbol, 0, i, 1, i, 2, i)) return true;
        }
        return lineOwnedBy(symbol, 0, 0, 1, 1, 2, 2) || lineOwnedBy(symbol, 0, 2, 1, 1, 2, 0);
    }

    private boolean lineOwnedBy(String symbol, int r1, int c1, int r2, int c2, int r3, int c3) {
        Cell a = board.getCellLogic(r1, c1);
        Cell b = board.getCellLogic(r2, c2);
        Cell c = board.getCellLogic(r3, c3);
        return !a.isGhost() && !b.isGhost() && !c.isGhost()
                && symbol.equals(a.getVisualSymbol())
                && symbol.equals(b.getVisualSymbol())
                && symbol.equals(c.getVisualSymbol());
    }
}
