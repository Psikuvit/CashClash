package me.psikuvit.cashClash.util.game.ptp;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.PDCSetter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;

/**
 * Utility class for creating and managing presidential buff selection items.
 * Handles the creation of interactive buff selection potions for presidents.
 */
public class PresidentialBuffSelectionUtils {

    private PresidentialBuffSelectionUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Create a buff selection item with appropriate display and metadata. The item's base potion
     * type is always set to match what the buff actually does (e.g. Strength -> a real Strength
     * bottle, Resistance -> Turtle Master, Extra Hearts -> Healing) - not just the display name -
     * so the bottle color/vanilla tooltip line up with the buff for every buff, not just the text.
     *
     * @param name The item name (e.g., "Strength Potion")
     * @param benefit The buff benefit description
     * @param potionType The base potion type matching what this buff actually applies
     * @return The created ItemStack
     */
    public static ItemStack createBuffSelectionItem(String name, String benefit, PotionType potionType) {
        ItemStack item = new ItemStack(Material.POTION);

        PDCSetter tags = PDCSetter.of(item);
        PotionMeta potionMeta = (PotionMeta) tags.meta();
        potionMeta.setBasePotionType(potionType);
        potionMeta.displayName(Messages.parse("<yellow>" + name + "</yellow>"));
        potionMeta.lore(List.of(
                Messages.parse(benefit),
                Component.empty(),
                Messages.parse("<gray>Right-click to select</gray>"),
                Messages.parse("<gray>or deselect</gray>")
        ));
        // Mark as buff selection potion (undrinkable)
        tags.set(Keys.BUFF_SELECTION_POTION, PersistentDataType.BYTE, (byte) 1);
        tags.apply();

        try {
            item.unsetData(DataComponentTypes.CONSUMABLE); // Remove default consumable behavior
        } catch (Exception e) {
            Messages.debug("[PTP] Warning: Could not unset consumable data: " + e.getMessage());
        }

        return item;
    }

    /**
     * Create a strength potion buff selection item
     *
     * @return The created ItemStack
     */
    public static ItemStack createStrengthBuffItem() {
        return createBuffSelectionItem("Strength Potion", description("strength-buff-description"), PotionType.STRENGTH);
    }

    /**
     * Create a speed potion buff selection item
     *
     * @return The created ItemStack
     */
    public static ItemStack createSpeedBuffItem() {
        return createBuffSelectionItem("Speed Potion", description("speed-buff-description"), PotionType.SWIFTNESS);
    }

    /**
     * Create a resistance potion buff selection item - looks like and describes what it actually
     * applies (Turtle Master: Resistance + Slowness), not plain Resistance.
     *
     * @return The created ItemStack
     */
    public static ItemStack createResistanceBuffItem() {
        return createBuffSelectionItem("Resistance Potion", description("resistance-buff-description"), PotionType.TURTLE_MASTER);
    }

    /**
     * Create extra hearts potion buff selection item - looks like and describes what it actually
     * applies (a one-time instant heal), not a permanent max-health increase.
     *
     * @return The created ItemStack
     */
    public static ItemStack createExtraHeartsBuffItem() {
        return createBuffSelectionItem("Health Potion", description("health-buff-description"), PotionType.HEALING);
    }

    private static String description(String key) {
        return CashClashPlugin.getInstance().getMessagesConfig().getRaw("gamemode-ptp." + key);
    }

    /**
     * Get the buff type from inventory slot
     *
     * @param slot The inventory slot (1, 3, 5, or 7)
     * @return The buff name, or null if invalid slot
     */
    public static String getBuffFromSlot(int slot) {
        return switch (slot) {
            case 1 -> "Strength";
            case 3 -> "Speed";
            case 5 -> "Resistance";
            case 7 -> "Extra Hearts";
            default -> null;
        };
    }
}

