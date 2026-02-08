package manager;

import game.Cell;

public class BoardManager {

    private final Cell[][] cells = new Cell[3][3];
    private String symbolStyle = "CLASSIC";

    public BoardManager() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c] = new Cell();
            }
        }
    }

    public String[][] getMatrix() {
        String[][] matrix = new String[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (cells[r][c].isEmpty()) {
                    matrix[r][c] = "";
                    continue;
                }
                String symbol = cells[r][c].getVisualSymbol();
                String mapped = mapSymbol(symbol);
                matrix[r][c] = cells[r][c].isGhost() ? "GHOST_" + mapped : mapped;
            }
        }
        return matrix;
    }

    private String mapSymbol(String symbol) {
        if ("X".equals(symbol)) {
            if ("RUNE".equals(symbolStyle)) return "✦";
            if ("FUTURE".equals(symbolStyle)) return "✕";
            return "X";
        }
        if ("O".equals(symbol)) {
            if ("RUNE".equals(symbolStyle)) return "◉";
            if ("FUTURE".equals(symbolStyle)) return "⬡";
            return "O";
        }
        return symbol;
    }

    public void updateCellVisual(int r, int c, boolean isX) {
        // Agora o visual é atualizado via binding.gameBoardView.updateBoard() na Activity
        // mas mantemos o método se quiser disparar efeitos sonoros aqui.
    }

    public void resetBoard() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c].reset();
            }
        }
    }

    public Cell getCellLogic(int r, int c) {
        return cells[r][c];
    }

    public int applyTriangleEffect() {
        int affected = 0;
        affected += affectCellNow(0, 1);
        affected += affectCellNow(2, 0);
        affected += affectCellNow(2, 2);
        return affected;
    }

    public int applySquareEffect() {
        int affected = 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (r == 1 && c == 1) continue;
                affected += affectCellNow(r, c);
            }
        }
        return affected;
    }

    private int affectCellNow(int r, int c) {
        if (cells[r][c].isEmpty() || cells[r][c].isGhost()) {
            return 0;
        }
        cells[r][c].turnIntoGhost();
        return 1;
    }

    public void setSymbolStyle(String style) {
        this.symbolStyle = style;
    }
}