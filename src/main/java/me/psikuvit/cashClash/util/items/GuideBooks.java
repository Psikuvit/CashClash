package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * The gamemode guide books ({@code guide-books.*} in items.yml): a read-only written book drawn
 * as a book and quill, explaining one gamemode. Handed out when a game starts and by
 * {@code /guidebook}; dropping one throws it away.
 */
public final class GuideBooks {

    private GuideBooks() {
        throw new AssertionError("Nope.");
    }

    /**
     * The items.yml key for a gamemode's guide, e.g. {@code capture-the-flag}.
     */
    public static String configKey(GamemodeType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static ItemStack create(GamemodeType type) {
        ItemsConfig cfg = CashClashPlugin.getInstance().getItemsConfig();
        String key = configKey(type);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        PDCSetter tags = PDCSetter.of(book);
        BookMeta meta = (BookMeta) tags.meta();
        meta.title(Component.text(cfg.getGuideBookTitle(key)));
        meta.author(Component.text("Cash Clash"));
        meta.addPages(cfg.getGuideBookPages(key).stream().map(Messages::parse).toArray(Component[]::new));
        meta.displayName(Messages.parse(cfg.getGuideBookName(key)));
        meta.setItemModel(NamespacedKey.minecraft("writable_book"));
        tags.set(Keys.GUIDE_BOOK, PersistentDataType.BYTE, (byte) 1).apply();
        return book;
    }

    public static boolean isGuideBook(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.GUIDE_BOOK, PersistentDataType.BYTE);
    }

    /**
     * Gives the player the guide for {@code type} in the {@code guide-books.slot} hotbar slot (the
     * first free slot if that one's taken), replacing any guide book they already carry.
     */
    public static void give(Player player, GamemodeType type) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isGuideBook(inventory.getItem(slot))) inventory.setItem(slot, null);
        }

        ItemStack book = create(type);
        int slot = CashClashPlugin.getInstance().getItemsConfig().getGuideBookSlot();
        ItemStack current = slot >= 0 && slot < 9 ? inventory.getItem(slot) : null;
        if (slot >= 0 && slot < 9 && (current == null || current.getType().isAir())) {
            inventory.setItem(slot, book);
        } else {
            ItemUtils.returnItemToInventoryOrDrop(player, book);
        }
    }
}
