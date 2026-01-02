package me.psikuvit.cashClash.gui.builder;

import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for creating GUIs with a fluent API.
 * Supports buttons with click handlers, borders, and dynamic content.
 */
public class GuiBuilder {

    private final String guiId;
    private final Map<Integer, GuiButton> buttons;
    private Component title;
    private int rows;
    private Material fillMaterial;
    private Material borderMaterial;
    private boolean allowPlayerInventoryClick;

    /**
     * Private constructor - use create() factory method.
     */
    private GuiBuilder(String guiId) {
        this.guiId = guiId;
        this.buttons = new HashMap<>();
        this.title = Component.text("GUI");
        this.rows = 3;
        this.fillMaterial = null;
        this.borderMaterial = null;
        this.allowPlayerInventoryClick = false;
    }

    /**
     * Create a new GUI builder with a unique ID.
     */
    public static GuiBuilder create(String guiId) {
        return new GuiBuilder(guiId);
    }

    /**
     * Set the title of the GUI.
     */
    public GuiBuilder title(String title) {
        this.title = Messages.parse(title);
        return this;
    }

    /**
     * Set the title of the GUI using a Component.
     */
    public GuiBuilder title(Component title) {
        this.title = title;
        return this;
    }

    /**
     * Set the number of rows (1-6).
     */
    public GuiBuilder rows(int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
        return this;
    }

    /**
     * Set the size by slot count (must be multiple of 9).
     */
    public GuiBuilder size(int size) {
        this.rows = Math.max(1, Math.min(6, size / 9));
        return this;
    }

    /**
     * Add a button at a specific slot.
     */
    public GuiBuilder button(int slot, GuiButton button) {
        buttons.put(slot, button);
        return this;
    }

    /**
     * Add a button at a specific slot with an ItemStack and click handler.
     */
    public GuiBuilder button(int slot, ItemStack item, Consumer<Player> onClick) {
        buttons.put(slot, GuiButton.of(item).onClick(onClick));
        return this;
    }

    /**
     * Add a button at a specific slot with material and click handler.
     */
    public GuiBuilder button(int slot, Material material, Component name, Consumer<Player> onClick) {
        buttons.put(slot, GuiButton.of(material, name).onClick(onClick));
        return this;
    }

    /**
     * Add a display item (no click action).
     */
    public GuiBuilder item(int slot, ItemStack item) {
        buttons.put(slot, GuiButton.of(item));
        return this;
    }

    /**
     * Add a display item at a specific slot.
     */
    public GuiBuilder item(int slot, Material material, Component name) {
        buttons.put(slot, GuiButton.of(material, name));
        return this;
    }

    /**
     * Add buttons at multiple slots.
     */
    public GuiBuilder buttons(int[] slots, GuiButton button) {
        for (int slot : slots) {
            buttons.put(slot, button);
        }
        return this;
    }

    /**
     * Fill empty slots with a material.
     */
    public GuiBuilder fill(Material material) {
        this.fillMaterial = material;
        return this;
    }

    /**
     * Add a border around the GUI.
     */
    public GuiBuilder border(Material material) {
        this.borderMaterial = material;
        return this;
    }

    /**
     * Allow player inventory clicks.
     */
    public GuiBuilder allowPlayerInventory(boolean allow) {
        this.allowPlayerInventoryClick = allow;
        return this;
    }

    /**
     * Add a close button at the specified slot.
     */
    public GuiBuilder closeButton(int slot) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<red>Close</red>"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        buttons.put(slot, GuiButton.of(item).onClick(p -> p.closeInventory()));
        return this;
    }

    /**
     * Add a back button at the specified slot that opens another GUI.
     */
    public GuiBuilder backButton(int slot, Consumer<Player> openPreviousGui) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<gray>← Back</gray>"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        buttons.put(slot, GuiButton.of(item).onClick(openPreviousGui));
        return this;
    }

    /**
     * Add items in a row starting at a slot.
     */
    public GuiBuilder row(int startSlot, GuiButton... rowButtons) {
        for (int i = 0; i < rowButtons.length && startSlot + i < rows * 9; i++) {
            buttons.put(startSlot + i, rowButtons[i]);
        }
        return this;
    }

    /**
     * Add items in a column starting at a slot.
     */
    public GuiBuilder column(int startSlot, GuiButton... columnButtons) {
        for (int i = 0; i < columnButtons.length && startSlot + (i * 9) < rows * 9; i++) {
            buttons.put(startSlot + (i * 9), columnButtons[i]);
        }
        return this;
    }

    /**
     * Build the GUI and open it for a player.
     */
    public void open(Player player) {
        build().open(player);
    }

    /**
     * Build the GUI.
     */
    public BuiltGui build() {
        GuiHolder holder = new GuiHolder(guiId);
        holder.setAllowPlayerInventoryClick(allowPlayerInventoryClick);

        int size = rows * 9;
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        // Apply fill material
        if (fillMaterial != null) {
            ItemStack fillItem = createFillItem(fillMaterial);
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, fillItem);
            }
        }

        // Apply border
        if (borderMaterial != null) {
            ItemStack borderItem = createFillItem(borderMaterial);
            applyBorder(inventory, borderItem, size);
        }

        // Place buttons
        for (Map.Entry<Integer, GuiButton> entry : buttons.entrySet()) {
            int slot = entry.getKey();
            GuiButton button = entry.getValue();
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, button.getItem());
                holder.registerButton(slot, button);
            }
        }

        return new BuiltGui(inventory, holder);
    }

    private ItemStack createFillItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void applyBorder(Inventory inventory, ItemStack borderItem, int size) {
        int rows = size / 9;

        // Top row
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
        }

        // Bottom row
        for (int i = (rows - 1) * 9; i < size; i++) {
            inventory.setItem(i, borderItem);
        }

        // Left and right columns
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, borderItem);
            inventory.setItem(row * 9 + 8, borderItem);
        }
    }

    /**
     * Helper to create a simple pane button with no action.
     */
    public static GuiButton pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return GuiButton.of(item);
    }

    /**
     * Helper to create a navigation button.
     */
    public static GuiButton navButton(Material material, String name, Consumer<Player> onClick) {
        return GuiButton.of(material, Messages.parse(name)).onClick(onClick);
    }
}
