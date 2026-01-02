package me.psikuvit.cashClash.gui.builder;

import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract base class for all GUIs in the plugin.
 * Provides a fluent API for building and managing GUIs.
 */
public abstract class AbstractGui {

    protected final String guiId;
    protected final Player viewer;
    protected final Map<Integer, GuiButton> buttons;
    protected Component title;
    protected int rows;
    protected Material fillMaterial;
    protected Material borderMaterial;
    protected boolean allowPlayerInventoryClick;
    protected AbstractGui parentGui;

    /**
     * Create a new GUI for a specific player.
     *
     * @param guiId  Unique identifier for this GUI type
     * @param viewer The player viewing this GUI
     */
    protected AbstractGui(String guiId, Player viewer) {
        this.guiId = guiId;
        this.viewer = viewer;
        this.buttons = new HashMap<>();
        this.title = Component.text("GUI");
        this.rows = 3;
        this.fillMaterial = null;
        this.borderMaterial = null;
        this.allowPlayerInventoryClick = false;
        this.parentGui = null;
    }

    /**
     * Set the title of this GUI.
     */
    protected void setTitle(String title) {
        this.title = Messages.parse(title);
    }

    /**
     * Set the title of this GUI using a Component.
     */
    protected void setTitle(Component title) {
        this.title = title;
    }

    /**
     * Set the number of rows (1-6).
     */
    protected void setRows(int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
    }

    /**
     * Set the fill material for empty slots.
     */
    protected void setFillMaterial(Material material) {
        this.fillMaterial = material;
    }

    /**
     * Set the border material.
     */
    protected void setBorderMaterial(Material material) {
        this.borderMaterial = material;
    }

    /**
     * Allow clicks in the player's inventory.
     */
    protected void setAllowPlayerInventoryClick(boolean allow) {
        this.allowPlayerInventoryClick = allow;
    }

    /**
     * Set the parent GUI (for back navigation).
     */
    public void setParentGui(AbstractGui parent) {
        this.parentGui = parent;
    }

    /**
     * Add a button at a specific slot.
     */
    protected void setButton(int slot, GuiButton button) {
        buttons.put(slot, button);
    }

    /**
     * Add a button with an ItemStack and click handler.
     */
    protected void setButton(int slot, ItemStack item, Consumer<Player> onClick) {
        buttons.put(slot, GuiButton.of(item).onClick(onClick));
    }

    /**
     * Add a display item (no click action).
     */
    protected void setItem(int slot, ItemStack item) {
        buttons.put(slot, GuiButton.of(item));
    }

    /**
     * Add a close button at a specific slot.
     */
    protected void setCloseButton(int slot) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<red>Close</red>"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        buttons.put(slot, GuiButton.of(item).onClick(p -> p.closeInventory()));
    }

    /**
     * Add a back button that navigates to parent GUI or closes.
     */
    protected void setBackButton(int slot) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<gray>← Back</gray>"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        buttons.put(slot, GuiButton.of(item).onClick(p -> {
            if (parentGui != null) {
                parentGui.open();
            } else {
                p.closeInventory();
            }
        }));
    }

    /**
     * Add a back button that opens a specific GUI.
     */
    protected void setBackButton(int slot, Consumer<Player> openPreviousGui) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<gray>← Back</gray>"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        buttons.put(slot, GuiButton.of(item).onClick(openPreviousGui));
    }

    /**
     * Create a background pane item.
     */
    protected static ItemStack createPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Build and configure this GUI.
     * Subclasses should override this to add their specific content.
     */
    protected abstract void build();

    /**
     * Open this GUI for the viewer.
     */
    public void open() {
        // Clear previous buttons and rebuild
        buttons.clear();
        build();

        // Create the GuiBuilder and configure it
        GuiBuilder builder = GuiBuilder.create(guiId)
                .title(title)
                .rows(rows)
                .allowPlayerInventory(allowPlayerInventoryClick);

        if (fillMaterial != null) {
            builder.fill(fillMaterial);
        }

        if (borderMaterial != null) {
            builder.border(borderMaterial);
        }

        // Add all buttons
        for (Map.Entry<Integer, GuiButton> entry : buttons.entrySet()) {
            builder.button(entry.getKey(), entry.getValue());
        }

        builder.open(viewer);
    }

    /**
     * Get the viewer of this GUI.
     */
    public Player getViewer() {
        return viewer;
    }

    /**
     * Get the GUI ID.
     */
    public String getGuiId() {
        return guiId;
    }

    /**
     * Open a sub-GUI with this GUI as parent.
     */
    protected void openSubGui(AbstractGui subGui) {
        subGui.setParentGui(this);
        subGui.open();
    }
}

