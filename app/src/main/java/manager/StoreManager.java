package manager;

import economy.Wallet;
import game.Player;
import enums.Symmetries;

public class StoreManager {

    private Wallet wallet;
    private Player player;

    public StoreManager(Wallet wallet, Player player) {
        this.wallet = wallet;
        this.player = player;
    }

    public boolean buySymmetry(Symmetries symmetry) {
        if(wallet.spendMatrices(1)) {
            player.addSymmetry(symmetry);
            return true;
        }
        return false;
    }
}
