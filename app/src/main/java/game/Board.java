package game;

public class Board {

    private char[][] board = new char[3][3];

    Board() {
        reset();
    }

    public void reset() {
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                board[i][j] = ' ';
            }
        }
    }

    public boolean setCell(int row, int colummn, char symbol) {
        if(board[row][colummn] == ' ') {
            board[row][colummn] = symbol;
            return true;
        }
        return false;
    }

    public char getCell(int row, int column) {
        return board[row][column];
    }

    public char[][] getBoard() {
        return board;
    }
}