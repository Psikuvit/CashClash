package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.PlayerSelectorGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Tablet of Hacking: opens the enemy-inventory viewer. Charges a 2000-coin fee,
 * checked once when opening the selector and once more when a target is picked
 * (the first check is a soft gate so the coins aren't deducted until a real
 * selection happens). Stateless.
 */
public class TabletOfHackingHandler extends CustomItemHandler {

    private static final long COST = 2000L;

    public TabletOfHackingHandler(CustomItemManager manager) {
        super(manager);
    }

    public void useTabletOfHacking(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug("TABLET", "Player " + player.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        Team enemyTeam = session.getOpposingTeam(playerTeam);
        if (enemyTeam == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null || ccp.getCoins() < COST) {
            Messages.send(player, "customitem.tablet-insufficient-coins");
            return;
        }

        PlayerSelectorGUI.openTabletOfHacking(player, enemyTeam.getPlayers());
    }

    // Called when a player selects an enemy in the PlayerSelector for Tablet of Hacking
    public void handleTabletOfHackingSelection(Player viewer, Player target) {
        GameSession session = GameManager.getInstance().getPlayerSession(viewer);
        if (session == null) {
            Messages.debug("TABLET", "Player " + viewer.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        CashClashPlayer ccp = session.getCashClashPlayer(viewer.getUniqueId());
        if (ccp == null || ccp.getCoins() < COST) {
            Messages.send(viewer, "customitem.tablet-insufficient-coins");
            return;
        }
        ccp.deductCoins(COST);
        viewer.openInventory(target.getInventory());
        Messages.send(viewer, "customitem.tablet-viewing-inventory", "player_name", target.getName());
        SoundUtils.play(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    @Override
    public void cleanup() {
    }
}
