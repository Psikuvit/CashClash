package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.items.CustomItemType;
import me.psikuvit.cashClash.manager.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all custom item event interactions.
 * Delegates business logic to CustomItemManager.
 */
public class CustomItemListener implements Listener {

    private final CustomItemManager manager = CustomItemManager.getInstance();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        CustomItemType type = manager.getCustomItemType(item);
        if (type == null) return;

        Action action = event.getAction();

        switch (type) {
            case GRENADE -> {
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    manager.throwGrenade(player, item, false);
                }
            }
            case SMOKE_CLOUD_GRENADE -> {
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    manager.throwGrenade(player, item, true);
                }
            }
            case MEDIC_POUCH -> {
                if (action == Action.RIGHT_CLICK_AIR) {
                    event.setCancelled(true);
                    manager.useMedicPouchSelf(player, item);
                }
            }
            case TABLET_OF_HACKING -> {
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    manager.useTabletOfHacking(player, item);
                }
            }
            case INVIS_CLOAK -> {
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    manager.toggleInvisCloak(player, true);
                } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                    manager.toggleInvisCloak(player, false);
                }
            }
            case BOUNCE_PAD -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    manager.placeBouncePad(player, item, event.getClickedBlock());
                }
            }
            case BOOMBOX -> {
                if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    manager.placeBoombox(player, item, event.getClickedBlock());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        CustomItemType type = manager.getCustomItemType(item);
        if (type != CustomItemType.MEDIC_POUCH) return;

        if (!(event.getRightClicked() instanceof Player target)) return;

        event.setCancelled(true);
        manager.useMedicPouchAlly(player, target, item);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        CustomItemType type = manager.getCustomItemType(item);
        if (type == null) return;

        switch (type) {
            case BAG_OF_POTATOES -> manager.handleBagOfPotatoesHit(attacker, item);
            case CASH_BLASTER -> manager.handleCashBlasterHit(attacker);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();

        Location below = loc.clone().subtract(0, 1, 0);
        Block blockBelow = below.getBlock();

        if (blockBelow.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            Location padLoc = blockBelow.getLocation();
            if (manager.isBouncePad(padLoc)) {
                manager.handleBouncePad(player, padLoc);
            }
        }
    }
}

