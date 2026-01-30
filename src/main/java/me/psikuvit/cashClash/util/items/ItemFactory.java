package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Centralized factory for creating all types of items in the CashClash plugin.
 * Provides a single entry point for item creation, improving maintainability and scalability.
 * 
 * <p>This factory separates concerns into two main categories:
 * <ul>
 *   <li><b>Gameplay Items</b> - Items used in-game (tagged items, custom items, armor)</li>
 *   <li><b>GUI Items</b> - Items used for shop displays and UI elements</li>
 * </ul>
 * 
 * <p>Usage example:
 * <pre>{@code
 * ItemFactory factory = ItemFactory.getInstance();
 * 
 * // Create a gameplay item
 * ItemStack sword = factory.createGameplayItem(WeaponItem.IRON_SWORD);
 * 
 * // Create a GUI display item
 * ItemStack shopItem = factory.createGuiItem(player, WeaponItem.IRON_SWORD);
 * }</pre>
 */
public final class ItemFactory {
    
    private static ItemFactory instance;
    
    private final GameplayItemFactory gameplayFactory;
    private final GuiItemFactory guiFactory;
    
    private ItemFactory() {
        this.gameplayFactory = new GameplayItemFactory();
        this.guiFactory = new GuiItemFactory();
    }
    
    /**
     * Gets the singleton instance of ItemFactory.
     * 
     * @return The ItemFactory instance
     */
    public static ItemFactory getInstance() {
        if (instance == null) {
            instance = new ItemFactory();
        }
        return instance;
    }
    
    // ==================== GAMEPLAY ITEMS ====================
    
    /**
     * Creates a tagged gameplay item from a Purchasable.
     * This is the standard method for creating items that will be used in-game.
     * The item will have proper PDC tags for tracking and refund purposes.
     * 
     * @param purchasable The purchasable item definition
     * @return The created ItemStack with proper tags, or null if purchasable is null
     */
    public ItemStack createGameplayItem(Purchasable purchasable) {
        return gameplayFactory.createTaggedItem(purchasable);
    }
    
    /**
     * Creates a custom item (grenades, bounce pads, etc.) with owner tracking.
     * 
     * @param customItem The custom item type
     * @param owner The player who owns this item
     * @return The created custom item with owner tag
     */
    public ItemStack createCustomItem(CustomItem customItem, Player owner) {
        return gameplayFactory.createCustomItem(customItem, owner);
    }
    
    /**
     * Creates and equips a custom armor piece for a player.
     * 
     * @param player The player to equip the armor to
     * @param armor The custom armor item
     */
    public void createAndEquipCustomArmor(Player player, CustomArmorItem armor) {
        gameplayFactory.createAndEquipCustomArmor(player, armor);
    }
    
    // ==================== GUI ITEMS ====================
    
    /**
     * Creates a shop item display for GUI.
     * 
     * @param player The player viewing the shop
     * @param item The purchasable item
     * @return The GUI display ItemStack
     */
    public ItemStack createGuiItem(Player player, Purchasable item) {
        return guiFactory.createShopItem(player, item);
    }
    
    /**
     * Creates a shop item display with specified quantity.
     * 
     * @param player The player viewing the shop
     * @param item The purchasable item
     * @param quantity The display quantity
     * @return The GUI display ItemStack
     */
    public ItemStack createGuiItem(Player player, Purchasable item, int quantity) {
        return guiFactory.createShopItem(player, item, quantity);
    }
    
    /**
     * Creates an upgradable item display (for armor/weapons with tiers).
     * 
     * @param item The purchasable item
     * @param maxed Whether the item is at max tier
     * @return The GUI display ItemStack
     */
    public ItemStack createUpgradableGuiItem(Purchasable item, boolean maxed) {
        return guiFactory.createUpgradableItem(item, maxed);
    }
    
    /**
     * Creates a locked diamond item display (for early-round restrictions).
     * 
     * @param item The diamond armor item
     * @param currentRound The current game round
     * @return The locked item display
     */
    public ItemStack createLockedDiamondGuiItem(Purchasable item, int currentRound) {
        return guiFactory.createLockedDiamondItem(item, currentRound);
    }
    
    /**
     * Creates armor set piece displays for GUI.
     * 
     * @param set The armor set
     * @param player The player viewing the shop
     * @return Array of ItemStacks for each piece in the set
     */
    public ItemStack[] createArmorSetGuiItems(CustomArmorItem.ArmorSet set, Player player) {
        return guiFactory.createArmorSetPieces(set, player);
    }
    
    // ==================== FACTORY ACCESS ====================
    
    /**
     * Gets the gameplay item factory for advanced usage.
     * 
     * @return The GameplayItemFactory instance
     */
    public GameplayItemFactory getGameplayFactory() {
        return gameplayFactory;
    }
    
    /**
     * Gets the GUI item factory for advanced usage.
     * 
     * @return The GuiItemFactory instance
     */
    public GuiItemFactory getGuiFactory() {
        return guiFactory;
    }
}
