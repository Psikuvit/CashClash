package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.lobby.QueueManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

import java.util.List;

/**
 * The arena villager's menu: a single lodestone compass in the middle that joins the queue. The
 * map is picked at random when the game starts, so there's no arena to choose.
 */
public class ArenaSelectionGUI extends AbstractGui {

    private static final String GUI_ID = "arena_selection";
    private static final int JOIN_SLOT = 13;

    public ArenaSelectionGUI(Player viewer) {
        super(GUI_ID, viewer);
        setTitle(CashClashPlugin.getInstance().getMessagesConfig().getMessage("queue.selector-title"));
        setRows(3);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Static convenience method to open the arena GUI.
     */
    public static void openArenaGUI(Player player) {
        new ArenaSelectionGUI(player).open();
    }

    @Override
    protected void build() {
        QueueManager queueManager = CashClashPlugin.getInstance().getQueueManager();

        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestone(viewer.getLocation());
        meta.setLodestoneTracked(false);
        meta.displayName(Messages.parse(CashClashPlugin.getInstance().getMessagesConfig().getMessage("queue.join-button-name")));
        meta.lore(List.of(Messages.parse(CashClashPlugin.getInstance().getMessagesConfig().getMessage("queue.join-button-lore",
                "queued", String.valueOf(queueManager.getQueuedCount())))));
        compass.setItemMeta(meta);

        setButton(JOIN_SLOT, GuiButton.of(compass).onClick(player -> {
            player.closeInventory();
            queueManager.join(player);
        }));
    }
}
