package me.psikuvit.cashClash.gui.categories;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopService;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shop category GUI for armor.
 */
public class ArmorCategoryGui extends AbstractShopCategoryGui {

    private static final String GUI_ID = "shop_armor";

    public ArmorCategoryGui(Player viewer) {
        super(GUI_ID, viewer, ShopCategory.ARMOR);
    }

    @Override
    protected void populateItems() {
        GameSession session = getSession();
        int currentRound = session != null ? session.getCurrentRound() : 1;

        // Count diamond armor pieces
        int diamondCount = 0;
        if (hasItem(Material.DIAMOND_HELMET)) diamondCount++;
        if (hasItem(Material.DIAMOND_CHESTPLATE)) diamondCount++;
        if (hasItem(Material.DIAMOND_LEGGINGS)) diamondCount++;
        if (hasItem(Material.DIAMOND_BOOTS)) diamondCount++;

        ConfigManager cfg = ConfigManager.getInstance();
        boolean diamondLimitReached = currentRound < cfg.getDiamondUnlockRound()
                && diamondCount >= cfg.getMaxDiamondPiecesEarly();

        // Standard armor column
        placeArmorButton(10, ArmorItem.IRON_HELMET, ArmorItem.DIAMOND_HELMET, diamondLimitReached, currentRound);
        placeArmorButton(19, ArmorItem.IRON_CHESTPLATE, ArmorItem.DIAMOND_CHESTPLATE, diamondLimitReached, currentRound);
        placeArmorButton(28, ArmorItem.IRON_LEGGINGS, ArmorItem.DIAMOND_LEGGINGS, diamondLimitReached, currentRound);
        placeArmorButton(37, ArmorItem.IRON_BOOTS, ArmorItem.DIAMOND_BOOTS, diamondLimitReached, currentRound);

        // Investor's set column
        setButton(11, createCustomArmorButton(CustomArmorItem.INVESTORS_HELMET));
        setButton(20, createCustomArmorButton(CustomArmorItem.INVESTORS_CHESTPLATE));
        setButton(29, createCustomArmorButton(CustomArmorItem.INVESTORS_LEGGINGS));
        setButton(38, createCustomArmorButton(CustomArmorItem.INVESTORS_BOOTS));

        // Separator column
        for (int i = 0; i < 4; i++) {
            setItem(12 + i * 9, createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        // Deathmauler set
        ItemStack[] deathmaulerPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.DEATHMAULER, viewer);
        setItem(13, ItemStack.of(Material.BARRIER));
        if (deathmaulerPieces.length > 0) {
            setButton(14, createArmorSetButton(deathmaulerPieces[0], CustomArmorItem.ArmorSet.DEATHMAULER));
        }
        if (deathmaulerPieces.length > 1) {
            setButton(15, createArmorSetButton(deathmaulerPieces[1], CustomArmorItem.ArmorSet.DEATHMAULER));
        }
        setItem(16, ItemStack.of(Material.BARRIER));

        // Dragon set
        ItemStack[] dragonPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.DRAGON, viewer);
        if (dragonPieces.length > 0) {
            setButton(22, createArmorSetButton(dragonPieces[0], CustomArmorItem.ArmorSet.DRAGON));
        }
        if (dragonPieces.length > 1) {
            setButton(23, createArmorSetButton(dragonPieces[1], CustomArmorItem.ArmorSet.DRAGON));
        }
        setItem(24, ItemStack.of(Material.BARRIER));
        if (dragonPieces.length > 2) {
            setButton(25, createArmorSetButton(dragonPieces[2], CustomArmorItem.ArmorSet.DRAGON));
        }

        // Flamebringer set
        ItemStack[] flamebringerPieces = GuiItemUtils.createArmorSetPieces(CustomArmorItem.ArmorSet.FLAMEBRINGER, viewer);
        setItem(31, ItemStack.of(Material.BARRIER));
        if (flamebringerPieces.length > 0) {
            setButton(32, createArmorSetButton(flamebringerPieces[0], CustomArmorItem.ArmorSet.FLAMEBRINGER));
        }
        if (flamebringerPieces.length > 1) {
            setButton(33, createArmorSetButton(flamebringerPieces[1], CustomArmorItem.ArmorSet.FLAMEBRINGER));
        }
        setItem(34, ItemStack.of(Material.BARRIER));

        // Individual custom armor pieces
        setButton(40, createCustomArmorButton(CustomArmorItem.MAGIC_HELMET));
        setButton(41, createCustomArmorButton(CustomArmorItem.BUNNY_SHOES));
        setButton(42, createCustomArmorButton(CustomArmorItem.GUARDIANS_VEST));
        setButton(43, createCustomArmorButton(CustomArmorItem.TAX_EVASION_PANTS));
    }

    private void placeArmorButton(int slot, ArmorItem ironItem, ArmorItem diamondItem,
                                   boolean diamondLimitReached, int currentRound) {
        boolean hasIron = hasItem(ironItem.getMaterial());
        boolean hasDiamond = hasItem(diamondItem.getMaterial());

        if (hasDiamond) {
            setButton(slot, createPurchasableButton(diamondItem, true));
        } else if (hasIron) {
            if (diamondLimitReached) {
                ItemStack locked = GuiItemUtils.createLockedDiamondItem(diamondItem, currentRound);
                setButton(slot, GuiButton.of(locked));
            } else {
                setButton(slot, createPurchasableButton(diamondItem, false));
            }
        } else {
            setButton(slot, createPurchasableButton(ironItem, false));
        }
    }

    private GuiButton createCustomArmorButton(CustomArmorItem item) {
        ItemStack itemStack = GuiItemUtils.createShopItem(viewer, item);
        return GuiButton.of(itemStack).onClick((p, clickType) -> handlePurchasableClick(item, clickType));
    }

    private GuiButton createArmorSetButton(ItemStack itemStack, CustomArmorItem.ArmorSet set) {
        return GuiButton.of(itemStack).onClick(p -> handleArmorSetPurchase(set));
    }

    private void handleArmorSetPurchase(CustomArmorItem.ArmorSet armorSet) {
        GameSession sess = getSession();
        if (sess == null) {
            Messages.send(viewer, "<red>You must be in a game to shop.</red>");
            viewer.closeInventory();
            return;
        }

        CashClashPlayer ccp = getCashClashPlayer();
        if (ccp == null) return;

        long totalPrice = armorSet.getTotalPrice();

        if (!ShopService.getInstance().canAfford(viewer, totalPrice)) {
            Messages.send(viewer, "<red>Not enough coins! (Cost: $" + String.format("%,d", totalPrice) + ")</red>");
            SoundUtils.play(viewer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ShopService.getInstance().purchase(viewer, totalPrice);

        int round = sess.getCurrentRound();
        for (CustomArmorItem piece : armorSet.getPieces()) {
            ItemStack replacedItem = ItemUtils.giveCustomArmorSet(viewer, piece);
            ccp.addPurchase(new PurchaseRecord(piece, 1, piece.getPrice(), replacedItem, round));
        }

        Messages.send(viewer, "");
        Messages.send(viewer, "<green><bold>✓ SET PURCHASED</bold></green>");
        Messages.send(viewer, "<yellow>" + armorSet.getDisplayName() + "</yellow>");
        Messages.send(viewer, "<dark_gray>-$" + String.format("%,d", totalPrice) + "</dark_gray>");
        Messages.send(viewer, "");
        SoundUtils.play(viewer, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);

        refresh();
    }
}

