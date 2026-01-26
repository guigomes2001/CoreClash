package game;

public class Cell {

    private String symbol = "";
    private String ghostSymbol = "";
    private boolean ghost = false;

    public boolean isEmpty() {
        return symbol.isEmpty();
    }

    public boolean isGhost() {
        return ghost;
    }

    public String getVisualSymbol() {
        return ghost ? ghostSymbol : symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        this.ghost = false;
        this.ghostSymbol = "";
    }

    public void turnIntoGhost() {
        this.ghostSymbol = symbol;
        this.symbol = "";
        this.ghost = true;
    }

    public void reset() {
        symbol = "";
        ghostSymbol = "";
        ghost = false;
    }
}