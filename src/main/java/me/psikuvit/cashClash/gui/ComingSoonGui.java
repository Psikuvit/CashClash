package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.gui.builder.GuiBuilder;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Placeholder GUI opened by a "Coming Soon" mannequin - one un-interactable paper in the middle
 * with the feature's name, a barrier to close it, and the same glass border every shop GUI uses.
 */
public class ComingSoonGui {

    private ComingSoonGui() {
        throw new AssertionError("Utility class");
    }

    public static void open(Player player) {
        GuiButton paper = GuiButton.of(Material.PAPER, Messages.parse("<yellow>Coming Soon!</yellow>"));
        GuiButton close = GuiButton.of(Material.BARRIER, Messages.parse("<red>Close</red>"))
                .onClick((Player p) -> p.closeInventory());

        GuiBuilder.create("coming_soon")
                .title("<dark_gray>Coming Soon</dark_gray>")
                .rows(3)
                .border(Material.GRAY_STAINED_GLASS_PANE)
                .button(13, paper)
                .button(22, close)
                .open(player);
    }
}
