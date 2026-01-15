package me.psikuvit.cashClash.listener;

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

/**
 * Consolidated listener for all damage-related events.
 * Handles: game phase protection, respawn protection, bonus tracking, custom armor effects, mythic item effects.
 */
public class DamageListener implements Listener {

    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();
    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final MythicItemManager mythicManager = MythicItemManager.getInstance();

    // ==================== MAIN DAMAGE HANDLER (EntityDamageEvent) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            // Check game phase protection first
            if (handleGamePhaseProtection(event, player)) return;

            // Handle armor effects
            handleArmorEffects(event, player);

            // Track damage for bonuses
            trackDamageForBonuses(event, player);
        } catch (Exception e) {
            Messages.debug("GAME", "Error handling damage: " + e.getMessage());
        }
    }

    // ==================== MAIN PVP DAMAGE HANDLER (EntityDamageByEntityEvent) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        try {
            // Lobby protection - cancel PvP outside games
            if (handleLobbyProtection(event)) return;

            // Respawn protection - cancel damage to respawn-protected players
            if (handleRespawnProtection(event)) return;

            // Handle attacker-side effects (mythic items, custom items)
            if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player) {
                handleAttackerEffects(event, attacker);
            }

            // Handle armor-based attack effects
            if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player target) {
                handleAttackerArmorEffects(event, attacker, target);
            }
        } catch (Exception e) {
            Messages.debug("GAME", "Error handling damage: " + e.getMessage());
        }
    }

    // ==================== KNOCKBACK IMMUNITY (Monitor Priority) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageKnockbackImmunity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        try {
            // Check if victim has Coin Cleaver (sturdy feet - no knockback)
            if (mythicManager.hasCoinCleaverNoKnockback(victim)) {
                Vector currentVelocity = victim.getVelocity().clone();
                SchedulerUtils.runTaskLater(() -> victim.setVelocity(currentVelocity), 1L);
            }
        } catch (Exception e) {
            Messages.debug("GAME", "Error checking Coin Cleaver knockback immunity: " + e.getMessage());
        }
    }

    // ==================== HEALTH REGAIN ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            handleHealthRegain(event, player);
        } catch (Exception e) {
            Messages.debug("GAME", "Error handling health regain: " + e.getMessage());
        }
    }

    // ==================== DELEGATE METHODS ====================

    /**
     * Handle lobby protection - cancel PvP outside game sessions.
     * @return true if damage was cancelled
     */
    private boolean handleLobbyProtection(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return false;

        GameSession damagerSession = GameManager.getInstance().getPlayerSession(damager);
        if (damagerSession != null) return false;

        // Cancel PvP outside games
        event.setCancelled(true);
        return true;
    }

    /**
     * Handle respawn protection - cancel damage to protected players.
     * @return true if damage was cancelled
     */
    private boolean handleRespawnProtection(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return false;
        if (!(event.getDamager() instanceof Player)) return false;

        GameSession session = GameManager.getInstance().getPlayerSession(victim);
        if (session == null) return false;

        CashClashPlayer ccp = session.getCashClashPlayer(victim.getUniqueId());
        if (ccp != null && ccp.isRespawnProtected()) {
            event.setCancelled(true);
            Messages.debug(victim, "GAME", "Damage cancelled due to respawn protection");
            return true;
        }
        return false;
    }

    /**
     * Handle game phase protection - cancel damage during waiting/shopping.
     * @return true if damage was cancelled
     */
    private boolean handleGamePhaseProtection(EntityDamageEvent event, Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return false;

        GameState state = session.getState();
        if (state == GameState.WAITING || state.isShopping()) {
            event.setCancelled(true);
            Messages.debug(player, "GAME", "Damage cancelled due to state: " + state);
            return true;
        }
        return false;
    }

    /**
     * Handle custom armor effects on damage taken.
     */
    private void handleArmorEffects(EntityDamageEvent event, Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        double healthAfter = Math.max(0, player.getHealth() - event.getFinalDamage());

        // Dragon Set: immune to explosions
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (armorManager.hasDragonSet(player)) {
                event.setCancelled(true);
                return;
            }
        }

        // Guardian's Vest: resistance when low health
        armorManager.onPlayerDamaged(player, healthAfter);

        // Deathmauler: track damage for absorption
        armorManager.onDeathmaulerDamageTaken(player);
    }

    /**
     * Track damage for bonus calculations.
     */
    private void trackDamageForBonuses(EntityDamageEvent event, Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

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
    }

    /**
     * Handle attacker-side effects (mythic items, custom items).
     */
    private void handleAttackerEffects(EntityDamageByEntityEvent event, Player attacker) {
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;

        // Check custom items
        CustomItem customType = PDCDetection.getCustomItem(item);
        if (customType == CustomItem.BAG_OF_POTATOES) {
            customItemManager.handleBagOfPotatoesHit(attacker, item);
        }

        // Check mythic items
        MythicItem mythic = PDCDetection.getMythic(item);
        if (mythic == null) return;

        switch (mythic) {
            case COIN_CLEAVER -> {
                double newDamage = mythicManager.handleCoinCleaverDamage(attacker, victim, event.getDamage(), event.isCritical());
                event.setDamage(newDamage);
            }
            case CARLS_BATTLEAXE -> {
                if (event.isCritical()) {
                    mythicManager.handleCarlsCriticalHit(attacker, victim);
                }
            }
            case ELECTRIC_EEL_SWORD -> {
                if (event.isCritical()) {
                    mythicManager.handleElectricEelChain(attacker, victim);
                }
            }
            case WARDEN_GLOVES -> mythicManager.useWardenPunch(attacker, victim);
            default -> { /* No special handling */ }
        }
    }

    /**
     * Handle armor-based attack effects.
     */
    private void handleAttackerArmorEffects(EntityDamageByEntityEvent event, Player attacker, Player target) {
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        // Handle all attack-based armor effects
        armorManager.onPlayerAttack(attacker, target);

        // Investor's Set: bonus damage in rounds 4/5
        double damageMultiplier = armorManager.getInvestorMeleeDamageMultiplier(attacker, session.getCurrentRound());
        if (damageMultiplier > 1.0) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }
    }

    /**
     * Handle health regain tracking for bonuses.
     */
    private void handleHealthRegain(EntityRegainHealthEvent event, Player player) {
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
    }
}
