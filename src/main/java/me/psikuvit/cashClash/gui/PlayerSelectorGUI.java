package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.GuiBuilder;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * GUI for selecting players (used by Tablet of Hacking).
 * Uses the GuiBuilder system for cleaner implementation.
 */
public class PlayerSelectorGUI {

    private static final String GUI_ID = "player_selector";

    /**
     * Open the tablet of hacking GUI showing enemy players to hack.
     */
    public static void openTabletOfHacking(Player player, Collection<UUID> enemyUuids) {
        GuiBuilder builder = GuiBuilder.create(GUI_ID)
                .title("<gold><bold>Select Enemy to Hack</bold></gold>")
                .rows(1)
                .fill(Material.GRAY_STAINED_GLASS_PANE);

        int slot = 1;
        for (UUID uuid : enemyUuids) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            builder.button(slot++, createPlayerHeadButton(target));
        }

        builder.closeButton(8);
        builder.open(player);
    }

    /**
     * Create a clickable player head button that shows enemy inventory.
     */
    private static GuiButton createPlayerHeadButton(Player target) {
        ItemStack skull = GuiItemUtils.createPlayerHead(target,
                "<yellow>" + target.getName() + "</yellow>",
                List.of(
                "<gray>Click to view inventory</gray>",
                "<dark_gray>Cost: <gold>$2,000</gold></dark_gray>"
        ));

        return GuiButton.of(skull).onClick(clicker -> {
            clicker.closeInventory();
            CustomItemManager.getInstance().handleTabletOfHackingSelection(clicker, target);
        });
    }
}

