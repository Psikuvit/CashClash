package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting which kit layout to edit.
 * Shows all available kits and opens the layout editor when clicked.
 */
public class LayoutKitSelectorGUI extends AbstractGui {

    private static final String GUI_ID = "layout_kit_selector";

    public LayoutKitSelectorGUI(Player viewer) {
        super(GUI_ID, viewer);
        setTitle("<gold><bold>Select Kit to Edit Layout</bold></gold>");
        setRows(4);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Open the layout kit selector GUI for a player.
     */
    public static void open(Player player) {
        new LayoutKitSelectorGUI(player).open();
    }

    @Override
    protected void build() {
        // Add kit buttons in a 3x4 grid (slots 10-12, 19-21, 28-30, 37)
        Kit[] kits = Kit.values();
        int[] slots = {10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25};

        for (int i = 0; i < kits.length && i < slots.length; i++) {
            Kit kit = kits[i];
            setButton(slots[i], createKitButton(kit));
        }
        setItem(13, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        setItem(22, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        // Close button
        setCloseButton(31);
    }

    private GuiButton createKitButton(Kit kit) {
        Material material = getKitMaterial(kit);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Messages.parse("<yellow><bold>" + kit.getDisplayName() + "</bold></yellow>"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Messages.parse("<gray>Click to edit the layout</gray>"));
        lore.add(Messages.parse("<gray>for the " + kit.getDisplayName() + " kit.</gray>"));
        lore.add(Component.empty());
        lore.add(Messages.parse("<green>Click to customize!</green>"));

        meta.lore(lore);
        item.setItemMeta(meta);

        return GuiButton.of(item).onClick(p -> startLayoutEditing(p, kit));
    }

    private void startLayoutEditing(Player player, Kit kit) {
        player.closeInventory();
        LayoutManager.getInstance().startEditing(player, kit);
    }

    /**
     * Get a representative material for each kit.
     */
    private Material getKitMaterial(Kit kit) {
        return switch (kit) {
            case ARCHER -> Material.BOW;
            case HEALER -> Material.SPLASH_POTION;
            case TANK -> Material.IRON_CHESTPLATE;
            case SCOUT -> Material.CROSSBOW;
            case LUMBERJACK -> Material.STONE_AXE;
            case PYROMANIAC -> Material.LAVA_BUCKET;
            case GHOST -> Material.FEATHER;
            case FIGHTER -> Material.IRON_SWORD;
            case SPIDER -> Material.COBWEB;
            case BOMBER -> Material.TNT;
        };
    }
}

