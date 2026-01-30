package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.player.PurchaseRecord.ArmorSlot;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shop category GUI for armor.
 */
public class ArmorCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_armor";

    // Standard armor column slots
    private static final int STANDARD_ARMOR_HELMET_SLOT = 10;
    private static final int STANDARD_ARMOR_CHESTPLATE_SLOT = 19;
    private static final int STANDARD_ARMOR_LEGGINGS_SLOT = 28;
    private static final int STANDARD_ARMOR_BOOTS_SLOT = 37;

    // Investor's set column slots
    private static final int INVESTORS_HELMET_SLOT = 11;
    private static final int INVESTORS_CHESTPLATE_SLOT = 20;
    private static final int INVESTORS_LEGGINGS_SLOT = 29;
    private static final int INVESTORS_BOOTS_SLOT = 38;

    // Separator column base slot
    private static final int SEPARATOR_COLUMN_BASE_SLOT = 12;

    // Deathmauler set slots
    private static final int DEATHMAULER_BARRIER_LEFT_SLOT = 13;
    private static final int DEATHMAULER_PIECE_1_SLOT = 14;
    private static final int DEATHMAULER_PIECE_2_SLOT = 15;
    private static final int DEATHMAULER_BARRIER_RIGHT_SLOT = 16;

    // Dragon set slots
    private static final int DRAGON_PIECE_1_SLOT = 22;
    private static final int DRAGON_PIECE_2_SLOT = 23;
    private static final int DRAGON_BARRIER_SLOT = 24;
    private static final int DRAGON_PIECE_3_SLOT = 25;

    // Flamebringer set slots
    private static final int FLAMEBRINGER_BARRIER_LEFT_SLOT = 31;
    private static final int FLAMEBRINGER_PIECE_1_SLOT = 33;
    private static final int FLAMEBRINGER_PIECE_2_SLOT = 34;
    private static final int FLAMEBRINGER_BARRIER_RIGHT_SLOT = 32;

    // Individual custom armor slots
    private static final int MAGIC_HELMET_SLOT = 40;
    private static final int GUARDIANS_VEST_SLOT = 41;
    private static final int TAX_EVASION_PANTS_SLOT = 42;
    private static final int BUNNY_SHOES_SLOT = 43;

    public ArmorCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.ARMOR);
    }

    @Override
    protected void populateItems() {
        GameSession session = getSession();
        int currentRound = session != null ? session.getCurrentRound() : 1;
        int diamondPiecesOwned = countDiamondPieces();
        boolean canBuyDiamond = canBuyDiamondPiece(currentRound, diamondPiecesOwned);

        populateStandardArmorColumn(canBuyDiamond, currentRound);
        populateInvestorsColumn();
        populateSeparatorColumn();
        populateDeathmaulerSet();
        populateDragonSet();
        populateFlamebringerSet();
        populateIndividualCustomArmor();
    }

    // ==================== DIAMOND LIMIT LOGIC ====================

    /**
     * Counts the number of diamond armor pieces the player currently owns.
     * Checks both inventory and equipped armor.
     *
     * @return The count of diamond armor pieces (0-4)
     */
    private int countDiamondPieces() {
        int count = 0;
        if (hasItem(Material.DIAMOND_HELMET)) count++;
        if (hasItem(Material.DIAMOND_CHESTPLATE)) count++;
        if (hasItem(Material.DIAMOND_LEGGINGS)) count++;
        if (hasItem(Material.DIAMOND_BOOTS)) count++;
        return count;
    }

    /**
     * Determines if the player can purchase a diamond armor piece this round.
     * Diamond armor is limited in early rounds to prevent overpowered early-game builds.
     *
     * @param currentRound      The current game round
     * @param diamondPiecesOwned The number of diamond pieces the player already owns
     * @return true if diamond armor can be purchased, false if the limit is reached
     */
    private boolean canBuyDiamondPiece(int currentRound, int diamondPiecesOwned) {
        ConfigManager cfg = ConfigManager.getInstance();
        return !(currentRound < cfg.getDiamondUnlockRound()
                && diamondPiecesOwned >= cfg.getMaxDiamondPiecesEarly());
    }

    // ==================== STANDARD ARMOR COLUMN ====================

    /**
     * Populates the standard armor column (iron → diamond progression).
     * Each slot shows the appropriate upgrade state based on what the player owns.
     */
    private void populateStandardArmorColumn(boolean canBuyDiamond, int currentRound) {
        placeProgressiveArmorButton(STANDARD_ARMOR_HELMET_SLOT,
                ArmorItem.IRON_HELMET, ArmorItem.DIAMOND_HELMET, canBuyDiamond, currentRound);
        placeProgressiveArmorButton(STANDARD_ARMOR_CHESTPLATE_SLOT,
                ArmorItem.IRON_CHESTPLATE, ArmorItem.DIAMOND_CHESTPLATE, canBuyDiamond, currentRound);
        placeProgressiveArmorButton(STANDARD_ARMOR_LEGGINGS_SLOT,
                ArmorItem.IRON_LEGGINGS, ArmorItem.DIAMOND_LEGGINGS, canBuyDiamond, currentRound);
        placeProgressiveArmorButton(STANDARD_ARMOR_BOOTS_SLOT,
                ArmorItem.IRON_BOOTS, ArmorItem.DIAMOND_BOOTS, canBuyDiamond, currentRound);
    }

    /**
     * Places a progressive armor button that handles iron → diamond upgrades.
     * Shows locked state if diamond limit is reached, or upgrade button if available.
     *
     * @param slot                The GUI slot to place the button
     * @param ironItem            The iron tier armor item
     * @param diamondItem         The diamond tier armor item
     * @param canBuyDiamond       Whether diamond can be purchased (not limited)
     * @param currentRound        The current game round (for locked item display)
     */
    private void placeProgressiveArmorButton(int slot, ArmorItem ironItem, ArmorItem diamondItem,
                                             boolean canBuyDiamond, int currentRound) {
        boolean hasIron = hasItem(ironItem.getMaterial());
        boolean hasDiamond = hasItem(diamondItem.getMaterial());

        if (hasDiamond) {
            setButton(slot, createPurchasableButtonMaxed(diamondItem, true));
        } else if (hasIron) {
            if (!canBuyDiamond) {
                ItemStack locked = ItemFactory.getInstance().createLockedDiamondGuiItem(diamondItem, currentRound);
                setButton(slot, GuiButton.of(locked));
            } else {
                setButton(slot, createPurchasableButtonMaxed(diamondItem, false));
            }
        } else {
            setButton(slot, createPurchasableButtonMaxed(ironItem, false));
        }
    }

    // ==================== INVESTOR'S SET COLUMN ====================

    /**
     * Populates the Investor's set column with individual purchasable pieces.
     */
    private void populateInvestorsColumn() {
        setButton(INVESTORS_HELMET_SLOT, createCustomArmorButton(CustomArmorItem.INVESTORS_HELMET));
        setButton(INVESTORS_CHESTPLATE_SLOT, createCustomArmorButton(CustomArmorItem.INVESTORS_CHESTPLATE));
        setButton(INVESTORS_LEGGINGS_SLOT, createCustomArmorButton(CustomArmorItem.INVESTORS_LEGGINGS));
        setButton(INVESTORS_BOOTS_SLOT, createCustomArmorButton(CustomArmorItem.INVESTORS_BOOTS));
    }

    // ==================== SEPARATOR COLUMN ====================

    /**
     * Populates the separator column with light blue glass panes.
     */
    private void populateSeparatorColumn() {
        for (int i = 0; i < 4; i++) {
            setItem(SEPARATOR_COLUMN_BASE_SLOT + i * 9, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }
    }

    // ==================== ARMOR SET PLACEMENT ====================

    /**
     * Populates the Deathmauler set section.
     * Deathmauler set consists of 2 pieces (chestplate + leggings).
     */
    private void populateDeathmaulerSet() {
        ItemStack[] pieces = ItemFactory.getInstance().createArmorSetGuiItems(CustomArmorItem.ArmorSet.DEATHMAULER, viewer);
        int[] pieceSlots = {DEATHMAULER_PIECE_1_SLOT, DEATHMAULER_PIECE_2_SLOT};
        int[] barrierSlots = {DEATHMAULER_BARRIER_LEFT_SLOT, DEATHMAULER_BARRIER_RIGHT_SLOT};

        placeArmorSetPiecesWithBarriers(CustomArmorItem.ArmorSet.DEATHMAULER, pieces, pieceSlots, barrierSlots);
    }

    /**
     * Populates the Dragon set section.
     * Dragon set consists of 3 pieces (helmet + chestplate + boots).
     */
    private void populateDragonSet() {
        ItemStack[] pieces = ItemFactory.getInstance().createArmorSetGuiItems(CustomArmorItem.ArmorSet.DRAGON, viewer);
        int[] pieceSlots = {DRAGON_PIECE_1_SLOT, DRAGON_PIECE_2_SLOT, DRAGON_PIECE_3_SLOT};
        int[] barrierSlots = {DRAGON_BARRIER_SLOT};

        placeArmorSetPiecesWithBarriers(CustomArmorItem.ArmorSet.DRAGON, pieces, pieceSlots, barrierSlots);
    }

    /**
     * Populates the Flamebringer set section.
     * Flamebringer set consists of 2 pieces (leggings + boots).
     */
    private void populateFlamebringerSet() {
        ItemStack[] pieces = ItemFactory.getInstance().createArmorSetGuiItems(CustomArmorItem.ArmorSet.FLAMEBRINGER, viewer);
        int[] pieceSlots = {FLAMEBRINGER_PIECE_1_SLOT, FLAMEBRINGER_PIECE_2_SLOT};
        int[] barrierSlots = {FLAMEBRINGER_BARRIER_LEFT_SLOT, FLAMEBRINGER_BARRIER_RIGHT_SLOT};

        placeArmorSetPiecesWithBarriers(CustomArmorItem.ArmorSet.FLAMEBRINGER, pieces, pieceSlots, barrierSlots);
    }

    /**
     * Places armor set pieces with barriers in empty slots.
     * For each piece slot: if a piece exists, place a button; otherwise, place a barrier.
     * All barrier slots are always filled with barriers.
     *
     * @param armorSet    The armor set being placed
     * @param pieces      Array of ItemStacks for the set pieces (may be shorter than pieceSlots)
     * @param pieceSlots  Array of slot indices where pieces should be placed
     * @param barrierSlots Array of slot indices where barriers should be placed
     */
    private void placeArmorSetPiecesWithBarriers(CustomArmorItem.ArmorSet armorSet, ItemStack[] pieces,
                                                  int[] pieceSlots, int[] barrierSlots) {
        // Place barriers in all barrier slots
        for (int barrierSlot : barrierSlots) {
            setItem(barrierSlot, ItemStack.of(Material.BARRIER));
        }

        // Place pieces or barriers in piece slots
        for (int i = 0; i < pieceSlots.length; i++) {
            if (i < pieces.length && pieces[i] != null) {
                setButton(pieceSlots[i], createArmorSetButton(pieces[i], armorSet));
            } else {
                setItem(pieceSlots[i], ItemStack.of(Material.BARRIER));
            }
        }
    }

    // ==================== INDIVIDUAL CUSTOM ARMOR ====================

    /**
     * Populates individual custom armor pieces that can be purchased separately.
     */
    private void populateIndividualCustomArmor() {
        setButton(MAGIC_HELMET_SLOT, createCustomArmorButton(CustomArmorItem.MAGIC_HELMET));
        setButton(GUARDIANS_VEST_SLOT, createCustomArmorButton(CustomArmorItem.GUARDIANS_VEST));
        setButton(TAX_EVASION_PANTS_SLOT, createCustomArmorButton(CustomArmorItem.TAX_EVASION_PANTS));
        setButton(BUNNY_SHOES_SLOT, createCustomArmorButton(CustomArmorItem.BUNNY_SHOES));
    }

    // ==================== BUTTON CREATION ====================

    /**
     * Creates a button for a custom armor item that can be purchased individually.
     */
    private GuiButton createCustomArmorButton(CustomArmorItem item) {
        ItemStack itemStack = ItemFactory.getInstance().createGuiItem(viewer, item);
        return GuiButton.of(itemStack).onClick((p, clickType) -> handlePurchasableClick(item, clickType));
    }

    /**
     * Creates a button for an armor set piece that triggers set purchase.
     */
    private GuiButton createArmorSetButton(ItemStack itemStack, CustomArmorItem.ArmorSet set) {
        return GuiButton.of(itemStack).onClick(p -> handleArmorSetPurchase(p, set));
    }

    // ==================== ARMOR SET PURCHASE HANDLING ====================

    /**
     * Handles the purchase of a complete armor set.
     * Validates game state, affordability, replaces existing armor, and creates purchase record.
     *
     * @param player   The player purchasing the set
     * @param armorSet The armor set to purchase
     */
    public void handleArmorSetPurchase(Player player, CustomArmorItem.ArmorSet armorSet) {
        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        GameSession session = ensureInGame(player);
        if (session == null) return;

        long totalPrice = armorSet.getTotalPrice();
        if (!ensureCanAffordSet(player, totalPrice)) return;

        applySetPurchase(player, session, armorSet, totalPrice, ccp);
    }

    /**
     * Ensures the player is in a game session.
     * Sends error message and closes inventory if not in game.
     *
     * @param player The player to check
     * @return The game session if valid, null otherwise
     */
    private GameSession ensureInGame(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(player, "<red>You must be in a game to shop.</red>");
            player.closeInventory();
        }
        return session;
    }

    /**
     * Ensures the player can afford the armor set.
     * Sends error message and plays sound if not affordable.
     *
     * @param player     The player to check
     * @param totalPrice The total price of the set
     * @return true if affordable, false otherwise
     */
    private boolean ensureCanAffordSet(Player player, long totalPrice) {
        if (ShopService.getInstance().canAfford(player, totalPrice)) {
            return true;
        }
        Messages.send(player, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
        SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return false;
    }

    /**
     * Applies the armor set purchase: deducts coins, replaces armor, creates purchase record, and sends success message.
     *
     * @param player     The player making the purchase
     * @param session    The game session
     * @param armorSet   The armor set being purchased
     * @param totalPrice The total price of the set
     * @param ccp        The CashClashPlayer instance
     */
    private void applySetPurchase(Player player, GameSession session, CustomArmorItem.ArmorSet armorSet,
                                   long totalPrice, CashClashPlayer ccp) {
        ShopService.getInstance().deductCoins(player, totalPrice);
        int round = session.getCurrentRound();

        List<CustomArmorItem> setPieces = armorSet.getPieces();
        Map<ArmorSlot, ItemStack> replacedSetItems = collectAndReplaceSetPieces(player, setPieces);

        // Create a single set purchase record with all replaced items
        ccp.addPurchase(new PurchaseRecord(setPieces.getFirst(), totalPrice, round, replacedSetItems, setPieces));

        sendSetPurchaseSuccessMessage(player, armorSet, totalPrice);
    }

    /**
     * Collects and replaces armor pieces for the set purchase.
     * Handles refund logic: custom armor goes to inventory, purchased vanilla armor is tracked but not returned.
     *
     * @param player    The player whose armor is being replaced
     * @param setPieces The list of armor pieces in the set
     * @return A map of replaced armor slots to their original items (for refund tracking)
     */
    private Map<ArmorSlot, ItemStack> collectAndReplaceSetPieces(Player player, List<CustomArmorItem> setPieces) {
        Map<ArmorSlot, ItemStack> replacedSetItems = new EnumMap<>(ArmorSlot.class);

        for (CustomArmorItem piece : setPieces) {
            ArmorSlot slot = getArmorSlot(piece.getMaterial());
            if (slot == null) continue; // Skip if slot cannot be determined
            
            ItemStack currentArmor = getCurrentArmorInSlot(player, slot);

            if (currentArmor != null && currentArmor.getType() != Material.AIR) {
                // Create a deep clone for tracking (preserves all metadata, enchantments, etc.)
                ItemStack clonedArmor = currentArmor.clone();
                
                // Check if it's custom armor (purchased item with CustomArmorItem tag)
                if (PDCDetection.isCustomArmorItem(currentArmor)) {
                    // Custom armor: store clone for refund tracking, return original to inventory
                    replacedSetItems.put(slot, clonedArmor);
                    returnReplacedItemToInventory(player, currentArmor.clone());
                } else if (PDCDetection.getAnyShopTag(currentArmor) != null) {
                    // Purchased vanilla armor (iron/diamond): track for refund but don't return to inventory
                    // The refund system will directly equip this from the map
                    replacedSetItems.put(slot, clonedArmor);
                }
            }

            // Equip the set piece (this replaces whatever was in the slot)
            ItemFactory.getInstance().createAndEquipCustomArmor(player, piece);
        }

        return replacedSetItems;
    }

    /**
     * Sends the success message and plays sound for a successful armor set purchase.
     *
     * @param player     The player who made the purchase
     * @param armorSet   The armor set that was purchased
     * @param totalPrice The total price paid
     */
    private void sendSetPurchaseSuccessMessage(Player player, CustomArmorItem.ArmorSet armorSet, long totalPrice) {
        Messages.send(player, "");
        Messages.send(player, "<green><bold>✓ SET PURCHASED</bold></green>");
        Messages.send(player, "<yellow>" + armorSet.getDisplayName() + "</yellow>");
        Messages.send(player, "<dark_gray>-$" + String.format("%,d", totalPrice) + "</dark_gray>");
        Messages.send(player, "");
        SoundUtils.play(player, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
    }

    // ==================== ARMOR SLOT UTILITIES ====================

    /**
     * Gets the armor slot enum for a given material.
     * Returns null if the material is not an armor piece.
     *
     * @param material The material to check
     * @return The corresponding ArmorSlot, or null if not armor
     */
    private ArmorSlot getArmorSlot(Material material) {
        if (material == null) return null;
        String matName = material.name();
        if (matName.endsWith("HELMET")) return ArmorSlot.HELMET;
        if (matName.endsWith("CHESTPLATE")) return ArmorSlot.CHESTPLATE;
        if (matName.endsWith("LEGGINGS")) return ArmorSlot.LEGGINGS;
        if (matName.endsWith("BOOTS")) return ArmorSlot.BOOTS;
        return null;
    }

    /**
     * Gets the current armor item in a specific slot.
     * Returns null if the slot is null or if no armor is equipped in that slot.
     *
     * @param player The player whose armor to check
     * @param slot   The armor slot to check (may be null)
     * @return The ItemStack in that slot, or null if empty/invalid
     */
    private ItemStack getCurrentArmorInSlot(Player player, ArmorSlot slot) {
        if (slot == null) return null;
        PlayerInventory inv = player.getInventory();
        return switch (slot) {
            case HELMET -> inv.getHelmet();
            case CHESTPLATE -> inv.getChestplate();
            case LEGGINGS -> inv.getLeggings();
            case BOOTS -> inv.getBoots();
        };
    }

    /**
     * Returns a replaced item to the player's inventory, or drops it if inventory is full.
     *
     * @param player The player receiving the item
     * @param item   The item to return (may be null or air)
     */
    private void returnReplacedItemToInventory(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

}