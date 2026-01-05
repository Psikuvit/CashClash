package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.logging.Level;

/**
 * Consolidated listener for all damage-related events.
 * Handles: game phase protection, bonus tracking, custom armor effects, mythic item effects.
 */
public class DamageListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();

    // ==================== ENTITY DAMAGE (General) ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onDamageLobbyProtection(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        try {
            GameSession damagerSession = GameManager.getInstance().getPlayerSession(damager);
            GameSession victimSession = GameManager.getInstance().getPlayerSession(victim);

            // Allow if both players are in the same game session
            if (damagerSession != null && damagerSession.equals(victimSession)) return;

            // Cancel PvP outside games
            event.setCancelled(true);
        } catch (Exception e) {
            logError("onDamageLobbyProtection", e);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamageGamePhase(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            GameSession session = GameManager.getInstance().getPlayerSession(player);
            if (session == null) return;

            // Cancel all damage during waiting and shopping phases
            GameState state = session.getState();
            if (state == GameState.WAITING || state.isShopping()) {
                event.setCancelled(true);
                Messages.debug(player, "GAME", "Damage cancelled due to state: " + state);
                return;
            }

            RoundData currentRound = session.getCurrentRoundData();
            if (currentRound == null) return;

            CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
            if (ccPlayer == null) return;

            currentRound.setLastDamageTime(player.getUniqueId(), System.currentTimeMillis());

            // Track damage received
            if (event.getDamage() > 0) {
                currentRound.addDamage(player.getUniqueId(), event.getFinalDamage());
            }

            // Update health tracking for close call bonus
            double healthAfter = Math.max(0, player.getHealth() - event.getFinalDamage());
            ccPlayer.updateLowestHealth(healthAfter);

            // Notify BonusManager of low health state
            BonusManager bonusManager = session.getBonusManager();
            if (bonusManager != null && healthAfter <= 2.0 && healthAfter > 0) {
                bonusManager.onReachLowHealth(player.getUniqueId());
            }
        } catch (Exception e) {
            logError("onDamageGamePhase", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageArmorEffects(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            GameSession session = GameManager.getInstance().getPlayerSession(player);
            if (session == null) return;

            double healthAfter = Math.max(0, player.getHealth() - event.getFinalDamage());

            // Dragon Set: immune to explosions
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                if (armorManager.isDragonSetImmuneToExplosion(player)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Guardian's Vest: resistance when low health
            armorManager.onPlayerDamaged(player, healthAfter);

            // Deathmauler: track damage for absorption
            armorManager.onDeathmaulerDamageTaken(player);
        } catch (Exception e) {
            logError("onDamageArmorEffects", e);
        }
    }

    // ==================== ENTITY DAMAGE BY ENTITY (PvP) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntityItems(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;

        try {
            ItemStack item = attacker.getInventory().getItemInMainHand();
            if (!item.hasItemMeta()) return;

            CustomItem customType = PDCDetection.getCustomItem(item);
            if (customType == CustomItem.BAG_OF_POTATOES) {
                customItemManager.handleBagOfPotatoesHit(attacker, item);
            }
        } catch (Exception e) {
            logError("onDamageByEntityItems", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntityMythic(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        try {
            ItemStack item = attacker.getInventory().getItemInMainHand();

            MythicItem mythic = PDCDetection.getMythic(item);
            if (mythic == null) return;

            switch (mythic) {
                case COIN_CLEAVER -> {
                    double newDamage = mythicManager.handleCoinCleaverDamage(attacker, victim, event.getDamage());
                    event.setDamage(newDamage);
                }
                case CARLS_BATTLEAXE -> {
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        mythicManager.handleCarlsChargedAttack(attacker);
                        if (attacker.getFallDistance() > 0 && !attacker.isOnGround()) {
                            mythicManager.handleCarlsCriticalHit(attacker, victim);
                        }
                    }
                }
                case ELECTRIC_EEL_SWORD -> {
                    if (attacker.getAttackCooldown() >= 0.9f) {
                        mythicManager.handleElectricEelChain(attacker, victim);
                    }
                }
                case WARDEN_GLOVES -> mythicManager.useWardenMelee(attacker, victim);
                default -> { /* No special handling */ }
            }
        } catch (Exception e) {
            logError("onDamageByEntityMythic", e);
        }
    }

    /**
     * Handle Coin Cleaver knockback immunity.
     * If the victim is holding Coin Cleaver in main or offhand, cancel knockback.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageKnockbackImmunity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        try {
            // Check if victim has Coin Cleaver (sturdy feet - no knockback)
            if (mythicManager.hasCoinCleaverNoKnockback(victim)) {
                // Store current velocity and restore it after a tick to cancel knockback
                Vector currentVelocity = victim.getVelocity().clone();
                SchedulerUtils.runTaskLater(() -> victim.setVelocity(currentVelocity), 1L);
            }
        } catch (Exception e) {
            logError("onDamageKnockbackImmunity", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntityArmor(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        try {
            GameSession session = GameManager.getInstance().getPlayerSession(attacker);
            if (session == null) return;

            // Handle all attack-based armor effects
            armorManager.onPlayerAttack(attacker, target);

            // Investor's Set: bonus damage in rounds 4/5
            double damageMultiplier = armorManager.getInvestorMeleeDamageMultiplier(attacker, session.getCurrentRound());
            if (damageMultiplier > 1.0) {
                event.setDamage(event.getDamage() * damageMultiplier);
            }
        } catch (Exception e) {
            logError("onDamageByEntityArmor", e);
        }
    }

    // ==================== HEALTH REGAIN ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            GameSession session = GameManager.getInstance().getPlayerSession(player);
            if (session == null) return;

            CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
            if (ccPlayer == null) return;

            double healthBefore = player.getHealth();

            AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            double healthAfter = Math.min(player.getHealth() + event.getAmount(), maxHealth);

            ccPlayer.updateLowestHealth(healthAfter);

            BonusManager bonusManager = session.getBonusManager();
            if (bonusManager != null) {
                if (healthBefore <= 2.0 && healthAfter > 2.0) {
                    bonusManager.onHealFromLowHealth(player.getUniqueId());
                } else if (healthAfter <= 2.0) {
                    bonusManager.onDropBackToLowHealth(player.getUniqueId());
                }
            }
        } catch (Exception e) {
            logError("onPlayerHeal", e);
        }
    }

    // ==================== UTILITY ====================

    private void logError(String method, Exception e) {
        CashClashPlugin.getInstance().getLogger().log(
            Level.WARNING,
            "Error in DamageListener." + method + ": " + e.getMessage(),
            e
        );
    }
}

