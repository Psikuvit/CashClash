package me.psikuvit.cashClash.player;

import me.psikuvit.cashClash.shop.EnchantEntry;
import org.bukkit.inventory.ItemStack;

/**
 * Record of a rune (enchant) purchase, tracked separately from {@link PurchaseRecord} since
 * {@link EnchantEntry} isn't a {@link me.psikuvit.cashClash.shop.items.Purchasable} - its price
 * depends on the level being bought, and refunding one has to restore the previous level's rune
 * item (including its {@code RUNE_LINK} tag) rather than a plain inventory item or equip slot.
 *
 * @param enchant The rune type purchased
 * @param newLevel The level bought
 * @param previousLevel The level owned before this purchase (0 if none)
 * @param price The price paid for this level
 * @param previousRune The previous level's rune item, to restore on refund (null if none owned)
 * @param round The round in which the purchase was made
 */
public record RunePurchaseRecord(
        EnchantEntry enchant,
        int newLevel,
        int previousLevel,
        long price,
        ItemStack previousRune,
        int round
) {
}
