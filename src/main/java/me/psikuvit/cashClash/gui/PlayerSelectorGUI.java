package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.AbstractGui;
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
 * Extends AbstractGui for consistent GUI implementation.
 */
public class PlayerSelectorGUI extends AbstractGui {

    private static final String GUI_ID = "player_selector";
    private final Collection<UUID> enemyUuids;

    public PlayerSelectorGUI(Player viewer, Collection<UUID> enemyUuids) {
        super(GUI_ID, viewer);
        this.enemyUuids = enemyUuids;
        setTitle("<gold><bold>Select Enemy to Hack</bold></gold>");
        setRows(1);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Open the tablet of hacking GUI showing enemy players to hack.
     */
    public static void openTabletOfHacking(Player player, Collection<UUID> enemyUuids) {
        new PlayerSelectorGUI(player, enemyUuids).open();
    }

    @Override
    protected void build() {
        int slot = 1;
        for (UUID uuid : enemyUuids) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            setButton(slot++, createPlayerHeadButton(target));
        }

        setCloseButton(8);
    }

    /**
     * Create a clickable player head button that shows enemy inventory.
     */
    private GuiButton createPlayerHeadButton(Player target) {
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

