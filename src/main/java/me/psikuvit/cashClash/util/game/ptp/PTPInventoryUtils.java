package me.psikuvit.cashClash.util.game.ptp;

import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.entity.Player;

/**
 * Utility class for Protect the President inventory handling.
 * Extracts buff selection inventory setup and restoration.
 */
public class PTPInventoryUtils {

    private PTPInventoryUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Save a president's current inventory and replace it with the buff selection items.
     * The snapshot lives on the president's own {@link CashClashPlayer} wrapper (see
     * {@link CashClashPlayer#stashFullInventory()}) rather than a map kept here, so there's one
     * canonical place a player's stuff can be "put away" instead of every caller keeping its own.
     *
     * @param president The president player
     */
    public static void giveBuffSelectionItems(Player president) {
        CashClashPlayer.stashFullInventory(president);

        SchedulerUtils.runTask(() -> {
            Messages.debug("[PTP] Giving buff selection items to president: " + president.getName());

            president.getInventory().setItem(1, PresidentialBuffSelectionUtils.createStrengthBuffItem());
            president.getInventory().setItem(3, PresidentialBuffSelectionUtils.createSpeedBuffItem());
            president.getInventory().setItem(5, PresidentialBuffSelectionUtils.createResistanceBuffItem());
            president.getInventory().setItem(7, PresidentialBuffSelectionUtils.createExtraHeartsBuffItem());

            president.updateInventory();
            Messages.send(president, "gamemode-ptp.buff-selection-prompt");
        });
    }

    /**
     * Restore a player's inventory from the snapshot {@link #giveBuffSelectionItems} stashed on
     * their {@link CashClashPlayer} wrapper. No-op if nothing is stashed.
     *
     * @param player The player to restore
     */
    public static void restoreInventory(Player player) {
        CashClashPlayer.restoreFullInventory(player);
        player.updateInventory();
        Messages.debug("[PTP] Restored inventory for: " + player.getName());
    }
}
