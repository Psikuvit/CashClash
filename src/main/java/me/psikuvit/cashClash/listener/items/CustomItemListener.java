package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.shop.items.CustomItemType;
import me.psikuvit.cashClash.manager.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

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
                // Right-click toggles invisibility on/off
                if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    manager.handleInvisCloakRightClick(player, item);
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
        if (type == null) return;

        if (!(event.getRightClicked() instanceof Player target)) return;

        switch (type) {
            case MEDIC_POUCH -> {
                event.setCancelled(true);
                manager.useMedicPouchAlly(player, target, item);
            }
            case RESPAWN_ANCHOR -> {
                event.setCancelled(true);
                manager.useRespawnAnchor(player, target, item);
            }
        }
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
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Handle Cash Blaster hits
        if (!(event.getHitEntity() instanceof Player)) return;

        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player attacker)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        CustomItemType type = manager.getCustomItemType(item);
        if (type == CustomItemType.CASH_BLASTER) {
            manager.handleCashBlasterHit(attacker);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check block player is standing on
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();

        if (blockBelow.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            if (manager.isBouncePad(blockBelow)) {
                manager.handleBouncePad(player, blockBelow);
            }
        }
    }
}

