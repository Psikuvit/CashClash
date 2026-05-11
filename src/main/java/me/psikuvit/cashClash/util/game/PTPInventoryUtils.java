package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

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
     *
     * @param president The president player
     * @param savedInventories Map used to store original inventory contents
     */
    public static void giveBuffSelectionItems(Player president, Map<UUID, ItemStack[]> savedInventories) {
        UUID presUuid = president.getUniqueId();
        savedInventories.put(presUuid, president.getInventory().getContents().clone());

        SchedulerUtils.runTask(() -> {
            Messages.debug("[PTP] Saving inventory for president: " + president.getName());

            president.getInventory().clear();
            Messages.debug("[PTP] Cleared inventory for: " + president.getName());

            president.getInventory().setItem(1, PresidentialBuffSelectionUtils.createStrengthBuffItem());
            president.getInventory().setItem(3, PresidentialBuffSelectionUtils.createSpeedBuffItem());
            president.getInventory().setItem(5, PresidentialBuffSelectionUtils.createResistanceBuffItem());
            president.getInventory().setItem(7, PresidentialBuffSelectionUtils.createExtraHeartsBuffItem());

            president.updateInventory();
            Messages.send(president, "gamemode-ptp.buff-selection-prompt");
        });
    }

    /**
     * Restore a player's inventory from the saved selection snapshot.
     *
     * @param player The player to restore
     * @param savedInventories Map containing saved inventories
     */
    public static void restoreInventory(Player player, Map<UUID, ItemStack[]> savedInventories) {
        UUID uuid = player.getUniqueId();
        if (!savedInventories.containsKey(uuid)) {
            return;
        }

        ItemStack[] savedContents = savedInventories.get(uuid);
        player.getInventory().clear();
        SchedulerUtils.runTaskAsync(() -> {
            player.getInventory().setContents(savedContents);
            Messages.debug("[PTP] Restored inventory for: " + player.getName());
            savedInventories.remove(uuid);
            player.updateInventory();
        });
    }
}

