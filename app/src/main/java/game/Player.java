package game;

import enums.Symmetries;

public class Player {

    private char symmbol;
    private int triangles = 1;
    private int squares = 1;

    public Player(char symmbol) {
        this.symmbol = symmbol;
    }

    public char getSymmbol() {
        return symmbol;
    }

    public boolean hasSymmetry(Symmetries symmetry) {
        return symmetry == Symmetries.TRIANGLE ? triangles > 0 : squares > 0;
    }

    public void useSymmetry(Symmetries symmetry) {
        if(symmetry == Symmetries.TRIANGLE) {
            triangles--;
        } else {
            squares--;
        }
    }

    public void addSymmetry(Symmetries symmetry) {
        if(symmetry == Symmetries.TRIANGLE) {
            triangles++;
        } else {
            squares++;
        }
    }
}
