package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gui.PlayerSelectorGUI;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Tablet of Hacking: opens the enemy-inventory viewer. Charges the configured coin fee,
 * checked once when opening the selector and once more when a target is picked
 * (the first check is a soft gate so the coins aren't deducted until a real
 * selection happens). Stateless.
 */
public class TabletOfHackingHandler extends CustomItemHandler {

    public TabletOfHackingHandler(CustomItemManager manager) {
        super(manager);
    }

    public void useTabletOfHacking(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) {
            Messages.debug("TABLET", "Player " + player.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam == null) return;

        Team enemyTeam = session.getOpposingTeam(playerTeam);
        if (enemyTeam == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null || ccp.getCoins() < cfg.getTabletOfHackingCost()) {
            Messages.send(player, "customitem.tablet-insufficient-coins");
            return;
        }

        PlayerSelectorGUI.openTabletOfHacking(player, enemyTeam.getPlayers());
    }

    // Called when a player selects an enemy in the PlayerSelector for Tablet of Hacking
    public void handleTabletOfHackingSelection(Player viewer, Player target) {
        CashClashPlayer ccp = CashClashPlayer.from(viewer);
        if (ccp == null) {
            Messages.debug("TABLET", "Player " + viewer.getName() + " tried to use Tablet of Hacking outside of a game.");
            return;
        }
        if (ccp.getCoins() < cfg.getTabletOfHackingCost()) {
            Messages.send(viewer, "customitem.tablet-insufficient-coins");
            return;
        }
        ccp.deductCoins(cfg.getTabletOfHackingCost());
        viewer.openInventory(target.getInventory());
        Messages.send(viewer, "customitem.tablet-viewing-inventory", "player_name", target.getName());
        SoundUtils.play(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    @Override
    public void cleanup() {
    }
}
