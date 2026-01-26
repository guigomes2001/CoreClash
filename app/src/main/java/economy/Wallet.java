package economy;

public class Wallet {

    private int matrices = 0;

    public int getMatrices() {
        return matrices;
    }

    public void addMatrices(int amount) {
        matrices += amount;
    }

    public boolean spendMatrices(int amount) {
        if (matrices >= amount) {
            matrices -= amount;
            return true;
        }
        return false;
    }
}
