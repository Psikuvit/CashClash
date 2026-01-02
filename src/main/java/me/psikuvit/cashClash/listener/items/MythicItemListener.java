package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
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

    private final MythicItemManager manager;

    // Track particle tasks for Sandstormer
    private final Map<UUID, BukkitTask> sandstormerParticleTasks;

    public MythicItemListener() {
        this.manager = MythicItemManager.getInstance();
        this.sandstormerParticleTasks = new ConcurrentHashMap<>();
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        MythicItem mythic = PDCDetection.getMythic(item);
        if (mythic == null) return;

        Action action = event.getAction();

        if (!action.isRightClick()) return;

        // Block mythic abilities during shopping phase
        if (isInShoppingPhase(player)) {
            event.setCancelled(true);
            Messages.send(player, "<red>You cannot use mythic abilities during the shopping phase!</red>");
            return;
        }

        Messages.debug(player, "Interact: " + mythic.name() + " action=" + action);

        switch (mythic) {
            case COIN_CLEAVER -> {
                Messages.debug(player, "COIN_CLEAVER -> grenade");
                event.setCancelled(true);
                manager.useCoinCleaverGrenade(player);

            }
            case WIND_BOW -> {
                // Sneak + right click for boost, regular right click for drawing bow
                if (player.isSneaking()) {
                    Messages.debug(player, "WIND_BOW -> boost");
                    event.setCancelled(true);
                    manager.useWindBowBoost(player);
                }
            }
            case ELECTRIC_EEL_SWORD -> {
                Messages.debug(player, "ELECTRIC_EEL -> teleport");
                event.setCancelled(true);
                manager.useElectricEelTeleport(player);

            }
            case WARDEN_GLOVES -> {
                Messages.debug(player, "WARDEN_GLOVES -> shockwave");
                event.setCancelled(true);
                manager.useWardenShockwave(player);

            }
            case BLAZEBITE_CROSSBOWS -> {
                // Sneak + right click to toggle mode
                if (player.isSneaking()) {
                    Messages.debug(player, "BLAZEBITE -> toggle mode");
                    event.setCancelled(true);
                    manager.toggleBlazebiteMode(player);
                }
            }
            case BLOODWRENCH_CROSSBOW -> {
                // Track charge start for supercharged shot when starting to draw
                Messages.debug(player, "BLOODWRENCH_CROSSBOW -> charge");
                manager.startSandstormerCharge(player);
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

            Messages.debug(attacker, "Damage: " + mythic.name() + " -> " + victim.getName());

            switch (mythic) {
                case COIN_CLEAVER -> {
                    // +25% damage if victim has more coins
                    double newDamage = manager.handleCoinCleaverDamage(attacker, victim, event.getDamage());
                    event.setDamage(newDamage);
                }
                case CARLS_BATTLEAXE -> {
                    // Check if attack is fully charged (0.9+ = fully charged)
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        Messages.debug(attacker, "CARLS charged hit");
                        manager.handleCarlsChargedAttack(attacker);

                        // Check for critical hit (must be falling and not on ground)
                        if (attacker.getFallDistance() > 0 && !attacker.isOnGround()) {
                            Messages.debug(attacker, "CARLS crit launch");
                            manager.handleCarlsCriticalHit(attacker, victim);
                        }
                    }
                }
                case ELECTRIC_EEL_SWORD -> {
                    // Chain damage on fully charged hits
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        Messages.debug(attacker, "ELECTRIC_EEL chain");
                        manager.handleElectricEelChain(attacker, victim);
                    }
                }
                case WARDEN_GLOVES -> {
                    Messages.debug(attacker, "WARDEN melee");
                    manager.useWardenMelee(attacker, victim);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        MythicItem mythic = PDCDetection.getMythic(bow);
        if (mythic == null) return;

        Messages.debug(player, "Bow shoot: " + mythic.name());

        switch (mythic) {
            case BLOODWRENCH_CROSSBOW -> {
                if (!manager.handleSandstormerShot(player)) {
                    Messages.debug(player, "BLOODWRENCH_CROSSBOW shot blocked");
                    event.setCancelled(true);
                }
            }
            case BLAZEBITE_CROSSBOWS -> {
                if (!manager.handleBlazebiteShot(player)) {
                    Messages.debug(player, "BLAZEBITE shot blocked");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.isCancelled()) return;
        if (event.getEntity() instanceof Arrow arrow) {
            if (!(arrow.getShooter() instanceof Player shooter)) return;

            ItemStack bow = shooter.getInventory().getItemInMainHand();
            MythicItem mythic = PDCDetection.getMythic(bow);

            if (mythic == MythicItem.WIND_BOW && event.getHitEntity() instanceof Player victim) {
                Messages.debug(shooter, "WIND_BOW hit " + victim.getName());
                manager.handleWindBowHit(shooter, victim);
            }

            if (mythic == MythicItem.BLOODWRENCH_CROSSBOW && event.getHitEntity() instanceof Player victim) {
                if (manager.isSandstormerSupercharged(shooter)) {
                    Messages.debug(shooter, "BLOODWRENCH_CROSSBOW supercharged hit " + victim.getName());
                    manager.fireSuperchargedSandstormer(shooter, victim);
                }
            }

            if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
                Messages.debug(shooter, "BLAZEBITE hit");
                manager.handleBlazebiteHit(shooter, event.getHitEntity(),
                    event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation());
            }
        } else if (event.getEntity() instanceof Trident trident) {
            if (!(trident.getShooter() instanceof Player shooter)) return;
            if (!(event.getHitEntity() instanceof LivingEntity victim)) return;

            Messages.debug(shooter, "TRIDENT hit " + shooter.getName());

            MythicItem mythic = PDCDetection.getMythic(trident.getItemStack());

            if (mythic == MythicItem.GOBLIN_SPEAR) {
                manager.handleGoblinSpearHit(shooter, victim);
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
        if (mythic == MythicItem.BLOODWRENCH_CROSSBOW) {
            // Start ambient particle task - sand particles when held
            BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
                if (!player.isOnline()) {
                    BukkitTask t = sandstormerParticleTasks.remove(uuid);
                    if (t != null) t.cancel();
                    return;
                }

                // Check if still holding Sandstormer
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (PDCDetection.getMythic(currentItem) == MythicItem.BLOODWRENCH_CROSSBOW) {
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

    /**
     * Checks if the player is currently in a shopping phase.
     */
    private boolean isInShoppingPhase(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null && session.getState().isShopping();
    }
}
