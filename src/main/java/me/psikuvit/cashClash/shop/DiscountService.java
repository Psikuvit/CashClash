package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.player.PurchaseRecord.ArmorSlot;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.items.ItemSelectionUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Trade-in discounts: an item that replaces the player's gear is cheaper by what that gear cost.
 * Only shop-bought gear counts (identified by its shop tag, so starter gear never does), and only
 * the gear the purchase actually takes - the piece worn in the item's armor slot, or the weapon it
 * replaces. A diamond piece is worth its iron price plus its diamond price, since the shop only
 * sells diamond as an upgrade over iron.
 */
public final class DiscountService {

    private static final int DIAMOND_RANK = ItemSelectionUtils.rankMaterial(Material.DIAMOND_HELMET);

    private DiscountService() {
        throw new AssertionError("Nope.");
    }

    /**
     * What this player pays for {@code quantity} of an item after its trade-in discount.
     */
    public static long getFinalPrice(Player player, Purchasable item, int quantity) {
        return Math.max(0, item.getPrice() * Math.max(1, quantity) - getDiscount(player, item));
    }

    /**
     * What this player pays for a whole armor set after its trade-in discount.
     */
    public static long getSetFinalPrice(Player player, CustomArmorItem.ArmorSet set) {
        return Math.max(0, set.getTotalPrice() - getSetDiscount(player, set));
    }

    /**
     * The trade-in discount on a single item for this player, or 0 if none applies. Armor sets
     * are discounted piece by piece through {@link #getSetDiscount} instead.
     */
    public static long getDiscount(Player player, Purchasable item) {
        return switch (item) {
            case CustomArmorItem armor -> afterFee(customArmorDiscount(player, armor));
            case MythicItem mythic -> mythicDiscount(player, mythic);
            case WeaponItem weapon when weapon == WeaponItem.CASH_BLASTER -> afterFee(bowTradeInValue(player));
            case WeaponItem weapon when weapon == WeaponItem.SOUL_KATANA -> afterFee(soulKatanaTradeInValue(player));
            default -> 0;
        };
    }

    /**
     * The trade-in discount on a whole armor set: each piece is discounted by the iron piece worn
     * in its slot, or by a diamond one only when the set piece itself is diamond or better. An
     * iron-tier piece (the Dragon helmet and boots) doesn't take a diamond piece - buying the set
     * puts that back in the inventory ({@link #keptOverSetPiece}) - so it's no discount either.
     */
    public static long getSetDiscount(Player player, CustomArmorItem.ArmorSet set) {
        long total = 0;
        for (CustomArmorItem piece : set.getPieces()) {
            boolean diamondCounts = ItemSelectionUtils.rankMaterial(piece.getMaterial()) >= DIAMOND_RANK;
            total += wornArmorTradeInValue(player, piece, diamondCounts);
        }
        return afterFee(total);
    }

    /**
     * Whether shop armor worn in a set piece's slot is better than the piece, so buying the set
     * keeps it (back in the inventory) rather than trading it in - a diamond helmet under the
     * Dragon set's iron helmet.
     */
    public static boolean keptOverSetPiece(ItemStack worn, CustomArmorItem piece) {
        return ItemSelectionUtils.rankMaterial(worn.getType()) > ItemSelectionUtils.rankMaterial(piece.getMaterial());
    }

    /**
     * Trade-ins only count for {@code trade-ins.discount-percent} of what the gear cost (90: a
     * $20,000 piece takes $18,000 off). Mythics skip this and get the full value.
     */
    private static long afterFee(long tradeInValue) {
        return Math.round(tradeInValue * CashClashPlugin.getInstance().getShopConfig().getTradeInDiscountPercent() / 100.0);
    }

    /**
     * The best of the two given shop-bought weapons the player is carrying, or null if neither.
     */
    public static WeaponItem bestOwnedWeapon(Player player, WeaponItem iron, WeaponItem diamond) {
        WeaponItem best = null;
        for (ItemStack is : player.getInventory().getContents()) {
            WeaponItem weapon = PDCDetection.getWeapon(is);
            if (weapon == diamond) return diamond;
            if (weapon == iron) best = iron;
        }
        return best;
    }

    private static long customArmorDiscount(Player player, CustomArmorItem armor) {
        if (armor.isPartOfSet()) return 0;
        return switch (armor) {
            case GUARDIANS_VEST -> wornArmorTradeInValue(player, armor, true);
            case TECTONIC_CAP, INVESTORS_HELMET, INVESTORS_CHESTPLATE, INVESTORS_LEGGINGS, INVESTORS_BOOTS ->
                    wornArmorTradeInValue(player, armor, false);
            default -> 0;
        };
    }

    private static long mythicDiscount(Player player, MythicItem mythic) {
        return switch (mythic) {
            case CARLS_BATTLEAXE -> weaponTradeInValue(player, WeaponItem.IRON_AXE, WeaponItem.DIAMOND_AXE);
            case ELECTRIC_EEL_SWORD, WARDEN_GLOVES -> weaponTradeInValue(player, WeaponItem.IRON_SWORD, WeaponItem.DIAMOND_SWORD);
            case WIND_BOW -> bowTradeInValue(player);
            case BLOODWRENCH_CROSSBOW, BLAZEBITE_CROSSBOWS -> crossbowSlot(player) >= 0 ? UtilityItem.CROSSBOW.getPrice() : 0;
            default -> 0;
        };
    }

    /**
     * A slot holding a shop-bought crossbow, or -1 if the player has none. However many they
     * carry, a crossbow mythic only takes - and is discounted by - one of them.
     */
    public static int crossbowSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (PDCDetection.getUtility(player.getInventory().getItem(slot)) == UtilityItem.CROSSBOW) return slot;
        }
        return -1;
    }

    /**
     * What the shop-bought iron (or, if {@code diamondCounts}, diamond) piece worn in
     * {@code piece}'s armor slot is worth as a trade-in.
     */
    private static long wornArmorTradeInValue(Player player, CustomArmorItem piece, boolean diamondCounts) {
        ArmorSlot slot = ItemUtils.getArmorSlot(piece.getMaterial());
        if (slot == null) return 0;
        if (!(PDCDetection.getPurchasable(ItemUtils.getCurrentArmorInSlot(player, slot)) instanceof ArmorItem worn)) return 0;

        ArmorItem iron = ironArmor(slot);
        ArmorItem diamond = diamondArmor(slot);
        if (worn == iron) return iron.getPrice();
        if (worn == diamond && diamondCounts) return iron.getPrice() + diamond.getPrice();
        return 0;
    }

    private static long weaponTradeInValue(Player player, WeaponItem iron, WeaponItem diamond) {
        WeaponItem best = bestOwnedWeapon(player, iron, diamond);
        if (best == diamond) return iron.getPrice() + diamond.getPrice();
        if (best == iron) return iron.getPrice();
        return 0;
    }

    /**
     * The Soul Katana is an iron-tier blade, so buying it takes a shop-bought iron sword - and,
     * with {@code trade-ins.soul-katana-diamond-sword} on, a diamond one too
     * ({@link #soulKatanaTakesDiamondSword}). Whichever it takes is the trade-in.
     */
    private static long soulKatanaTradeInValue(Player player) {
        if (soulKatanaTakesDiamondSword()) return weaponTradeInValue(player, WeaponItem.IRON_SWORD, WeaponItem.DIAMOND_SWORD);
        for (ItemStack is : player.getInventory().getContents()) {
            if (PDCDetection.getWeapon(is) == WeaponItem.IRON_SWORD) return WeaponItem.IRON_SWORD.getPrice();
        }
        return 0;
    }

    /**
     * Whether buying the Soul Katana takes a diamond sword as well as an iron one.
     */
    public static boolean soulKatanaTakesDiamondSword() {
        return CashClashPlugin.getInstance().getShopConfig().isSoulKatanaDiamondSwordTradeIn();
    }

    private static long bowTradeInValue(Player player) {
        for (ItemStack is : player.getInventory().getContents()) {
            if (PDCDetection.getUtility(is) == UtilityItem.BOW) return UtilityItem.BOW.getPrice();
        }
        return 0;
    }

    private static ArmorItem ironArmor(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> ArmorItem.IRON_HELMET;
            case CHESTPLATE -> ArmorItem.IRON_CHESTPLATE;
            case LEGGINGS -> ArmorItem.IRON_LEGGINGS;
            case BOOTS -> ArmorItem.IRON_BOOTS;
        };
    }

    private static ArmorItem diamondArmor(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> ArmorItem.DIAMOND_HELMET;
            case CHESTPLATE -> ArmorItem.DIAMOND_CHESTPLATE;
            case LEGGINGS -> ArmorItem.DIAMOND_LEGGINGS;
            case BOOTS -> ArmorItem.DIAMOND_BOOTS;
        };
    }
}
