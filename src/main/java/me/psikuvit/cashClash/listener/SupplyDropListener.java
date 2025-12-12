package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class SupplyDropListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.EMERALD) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER)) return;

        Integer amount = pdc.get(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER);
        if (amount == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        CashClashPlayer ccp = session.getCashClashPlayer(p.getUniqueId());
        if (ccp == null) return;

        event.setCancelled(true);
        int left = current.getAmount() - 1;
        if (left > 0) {
            current.setAmount(left);
            event.setCurrentItem(current);
        } else {
            event.setCurrentItem(null);
        }

        ccp.addCoins(amount);
        Messages.send(p, "<gold>+$" + String.format("%,d", amount) + " from supply drop!</gold>");
        SoundUtils.play(p, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();

        if (held.getType() != Material.EMERALD) return;

        ItemMeta meta = held.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER)) return;

        Integer amount = pdc.get(Keys.SUPPLY_DROP_AMOUNT, PersistentDataType.INTEGER);
        if (amount == null) return;

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;
        CashClashPlayer ccp = session.getCashClashPlayer(p.getUniqueId());
        if (ccp == null) return;

        // consume one from hand
        int left = held.getAmount() - 1;
        if (left > 0) {
            held.setAmount(left);
            p.getInventory().setItemInMainHand(held);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        ccp.addCoins(amount);
        Messages.send(p, "<gold>+$" + String.format("%,d", amount) + " from supply drop!</gold>");
        SoundUtils.play(p, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        event.setCancelled(true);
    }
}
