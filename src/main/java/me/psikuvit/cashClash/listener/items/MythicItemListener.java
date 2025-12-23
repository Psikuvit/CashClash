package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.manager.MythicItemManager;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all Mythic item event interactions.
 * Delegates business logic to MythicItemManager.
 */
public class MythicItemListener implements Listener {

    private final MythicItemManager manager = MythicItemManager.getInstance();

    // Track particle tasks for Sandstormer
    private final Map<UUID, BukkitTask> sandstormerParticleTasks = new ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        MythicItem mythic = PDCDetection.getMythic(item);
        if (mythic == null) return;

        Action action = event.getAction();
        boolean isRightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        switch (mythic) {
            case COIN_CLEAVER -> {
                if (isRightClick) {
                    event.setCancelled(true);
                    manager.useCoinCleaverGrenade(player);
                }
            }
            case WIND_BOW -> {
                // Sneak + right click for boost, regular right click for drawing bow
                if (isRightClick && player.isSneaking()) {
                    event.setCancelled(true);
                    manager.useWindBowBoost(player);
                }
            }
            case ELECTRIC_EEL_SWORD -> {
                if (isRightClick) {
                    event.setCancelled(true);
                    manager.useElectricEelTeleport(player);
                }
            }
            case GOBLIN_SPEAR -> {
                if (isRightClick) {
                    event.setCancelled(true);
                    manager.throwGoblinSpear(player);
                }
            }
            case WARDEN_GLOVES -> {
                if (isRightClick) {
                    event.setCancelled(true);
                    manager.useWardenShockwave(player);
                }
            }
            case BLAZEBITE_CROSSBOWS -> {
                // Sneak + right click to toggle mode
                if (isRightClick && player.isSneaking()) {
                    event.setCancelled(true);
                    manager.toggleBlazebiteMode(player);
                }
            }
            case SANDSTORMER -> {
                // Track charge start for supercharged shot when starting to draw
                if (isRightClick) {
                    manager.startSandstormerCharge(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Handle direct melee damage from player
        if (event.getDamager() instanceof Player attacker) {
            ItemStack item = attacker.getInventory().getItemInMainHand();
            MythicItem mythic = PDCDetection.getMythic(item);
            if (mythic == null) return;

            switch (mythic) {
                case COIN_CLEAVER -> {
                    // +25% damage if victim has more coins
                    double newDamage = manager.handleCoinCleaverDamage(attacker, victim, event.getDamage());
                    event.setDamage(newDamage);
                }
                case CARLS_BATTLEAXE -> {
                    // Check if attack is fully charged (0.9+ = fully charged)
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        manager.handleCarlsChargedAttack(attacker);

                        // Check for critical hit (must be falling and not on ground)
                        if (attacker.getFallDistance() > 0 && !attacker.isOnGround()) {
                            manager.handleCarlsCriticalHit(attacker, victim);
                        }
                    }
                }
                case ELECTRIC_EEL_SWORD -> {
                    // Chain damage on fully charged hits
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        manager.handleElectricEelChain(attacker, victim);
                    }
                }
                case WARDEN_GLOVES -> // Apply knockback II on melee
                        manager.useWardenMelee(attacker, victim);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        MythicItem mythic = PDCDetection.getMythic(bow);
        if (mythic == null) return;

        switch (mythic) {
            case SANDSTORMER -> {
                if (!manager.handleSandstormerShot(player)) {
                    event.setCancelled(true);
                }
            }
            case BLAZEBITE_CROSSBOWS -> {
                if (!manager.handleBlazebiteShot(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Handle Arrow projectiles (Wind Bow, Sandstormer, BlazeBite)
        if (event.getEntity() instanceof Arrow arrow) {
            if (!(arrow.getShooter() instanceof Player shooter)) return;

            ItemStack bow = shooter.getInventory().getItemInMainHand();
            MythicItem mythic = PDCDetection.getMythic(bow);

            if (mythic == MythicItem.WIND_BOW && event.getHitEntity() instanceof Player victim) {
                manager.handleWindBowHit(shooter, victim);
            }

            if (mythic == MythicItem.SANDSTORMER && event.getHitEntity() instanceof Player victim) {
                if (manager.isSandstormerSupercharged(shooter)) {
                    manager.fireSuperchargedSandstormer(shooter, victim);
                }
            }

            if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
                manager.handleBlazebiteHit(shooter, event.getHitEntity(),
                    event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation());
            }
        }

        // Handle Trident projectiles (Goblin Spear)
        if (event.getEntity() instanceof Trident trident) {
            if (!(trident.getShooter() instanceof Player shooter)) return;

            String tag = PDCDetection.readTag(trident.getItemStack(), Keys.ITEM_ID);

            if ("GOBLIN_SPEAR".equals(tag) && event.getHitEntity() instanceof Player victim) {
                manager.handleGoblinSpearHit(shooter, victim, trident);
            }
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cancel existing particle task
        BukkitTask existingTask = sandstormerParticleTasks.remove(uuid);
        if (existingTask != null) {
            existingTask.cancel();
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null) return;

        MythicItem mythic = PDCDetection.getMythic(newItem);
        if (mythic == MythicItem.SANDSTORMER) {
            // Start ambient particle task - sand particles when held
            BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
                if (!player.isOnline()) {
                    BukkitTask t = sandstormerParticleTasks.remove(uuid);
                    if (t != null) t.cancel();
                    return;
                }

                // Check if still holding Sandstormer
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (PDCDetection.getMythic(currentItem) == MythicItem.SANDSTORMER) {
                    manager.spawnSandstormerParticles(player);
                }
            }, 10L, 10L);

            sandstormerParticleTasks.put(uuid, task);
        }
    }

    /**
     * Handle Coin Cleaver no-knockback passive.
     * This runs at MONITOR priority to apply after damage is calculated.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        if (manager.hasCoinCleaverNoKnockback(victim)) {
            Bukkit.getScheduler().runTask(CashClashPlugin.getInstance(), () -> {
                if (victim.isOnline()) {
                    victim.setVelocity(victim.getVelocity().setX(0).setZ(0));
                }
            });
        }
    }
}
