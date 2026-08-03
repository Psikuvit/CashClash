package me.psikuvit.cashClash.util.game.ptp;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.PDCSetter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
     * Create a buff selection item with appropriate display and metadata
     *
     * @param name The item name (e.g., "Strength Potion")
     * @param benefit The buff benefit description
     * @return The created ItemStack
     */
    public static ItemStack createBuffSelectionItem(String name, String benefit) {
        ItemStack item = new ItemStack(Material.POTION);

        PDCSetter tags = PDCSetter.of(item);
        tags.meta().displayName(Messages.parse("<yellow>" + name + "</yellow>"));
        tags.meta().lore(List.of(
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
        return createBuffSelectionItem("Strength Potion",
                "<gold>Strength I - Deal more damage</gold>");
    }

    /**
     * Create a speed potion buff selection item
     *
     * @return The created ItemStack
     */
    public static ItemStack createSpeedBuffItem() {
        return createBuffSelectionItem("Speed Potion",
                "<gold>Speed I - Move faster</gold>");
    }

    /**
     * Create a resistance potion buff selection item
     *
     * @return The created ItemStack
     */
    public static ItemStack createResistanceBuffItem() {
        return createBuffSelectionItem("Resistance Potion",
                "<gold>Resistance I - Take less damage</gold>");
    }

    /**
     * Create extra hearts potion buff selection item
     *
     * @return The created ItemStack
     */
    public static ItemStack createExtraHeartsBuffItem() {
        return createBuffSelectionItem("Health Potion",
                "<gold>Extra Hearts - Gain +2 max heart</gold>");
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

