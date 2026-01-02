package me.psikuvit.cashClash.gui.builder;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a clickable button in a GUI.
 * Supports different actions for different click types.
 */
public class GuiButton {

    private final ItemStack item;
    private Consumer<Player> onClick;
    private BiConsumer<Player, ClickType> onClickWithType;
    private boolean cancelClick;

    /**
     * Private constructor - initializes all attributes.
     */
    private GuiButton(ItemStack item) {
        this.item = item;
        this.onClick = null;
        this.onClickWithType = null;
        this.cancelClick = true;
    }

    /**
     * Create a button from an existing ItemStack.
     */
    public static GuiButton of(ItemStack item) {
        return new GuiButton(item.clone());
    }

    /**
     * Create a button with a material.
     */
    public static GuiButton of(Material material) {
        return new GuiButton(new ItemStack(material));
    }

    /**
     * Create a button with a material and display name.
     */
    public static GuiButton of(Material material, Component displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        item.setItemMeta(meta);
        return new GuiButton(item);
    }

    /**
     * Create a button with material, display name, and lore.
     */
    public static GuiButton of(Material material, Component displayName, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        item.setItemMeta(meta);
        return new GuiButton(item);
    }

    /**
     * Set the display name of the button.
     */
    public GuiButton name(Component name) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return this;
    }

    /**
     * Set the lore of the button.
     */
    public GuiButton lore(List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore);
        item.setItemMeta(meta);
        return this;
    }

    /**
     * Set the amount of the item.
     */
    public GuiButton amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Set a simple click action (any click type).
     */
    public GuiButton onClick(Consumer<Player> action) {
        this.onClick = action;
        return this;
    }

    /**
     * Set a click action that also receives the click type.
     */
    public GuiButton onClick(BiConsumer<Player, ClickType> action) {
        this.onClickWithType = action;
        return this;
    }

    /**
     * Set whether the click should be cancelled.
     * Default is true.
     */
    public GuiButton cancelClick(boolean cancel) {
        this.cancelClick = cancel;
        return this;
    }

    /**
     * Execute the click action.
     */
    public void executeClick(Player player, ClickType clickType) {
        if (onClickWithType != null) {
            onClickWithType.accept(player, clickType);
        } else if (onClick != null) {
            onClick.accept(player);
        }
    }

    /**
     * Check if this button has any click action.
     */
    public boolean hasAction() {
        return onClick != null || onClickWithType != null;
    }

    /**
     * Get the ItemStack representation.
     */
    public ItemStack getItem() {
        return item;
    }

    /**
     * Check if click should be cancelled.
     */
    public boolean shouldCancelClick() {
        return cancelClick;
    }
}
