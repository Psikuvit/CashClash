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
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Consolidated listener for all damage-related events.
 * Handles: game phase protection, respawn protection, bonus tracking, custom armor effects, mythic item effects.
 *
 * Event Processing Order:
 * 1. HIGH Priority: Main damage handling (protection, armor effects, tracking)
 * 2. HIGH Priority: PvP damage handling (custom items, mythic items, combat modifiers)
 * 3. MONITOR Priority: Post-damage effects (knockback immunity)
 * 4. NORMAL Priority: Health regain tracking
 */
public class DamageListener implements Listener {

    private static final double STRENGTH_NERF_MULTIPLIER = 0.5;
    private static final double POWER_NERF_MULTIPLIER = 0.2;
    private static final int MAX_POWER_LEVEL_REGULAR_BOW = 2;
    private static final double LEGENDARY_CROSSBOW_DAMAGE_BOOST = 1.3;

    private final GameManager gameManager;
    private final CustomArmorManager armorManager;
    private final CustomItemManager customItemManager;
    private final MythicItemManager mythicManager;

    public DamageListener() {
        this.gameManager = GameManager.getInstance();
        this.armorManager = CustomArmorManager.getInstance();
        this.customItemManager = CustomItemManager.getInstance();
        this.mythicManager = MythicItemManager.getInstance();
    }

    // ==================== MAIN DAMAGE HANDLER (EntityDamageEvent) ====================

    /**
     * Handles all damage events with high priority.
     * Processes: game phase protection, armor effects, and damage tracking.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        try {
            // 1. Check game phase protection (waiting/shopping)
            if (handleGamePhaseProtection(event, player)) {
                return;
            }

            // 2. Handle custom armor defensive effects
            handleArmorDefenseEffects(event, player);

            // 3. Track damage for bonus calculations
            trackDamageForBonuses(event, player);

        } catch (Exception e) {
            Messages.debug("DAMAGE", "Error handling damage for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== MAIN PVP DAMAGE HANDLER (EntityDamageByEntityEvent) ====================

    /**
     * Handles player vs player damage with high priority.
     * Processes: lobby protection, respawn protection, invisibility, attacker effects, combat modifiers.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            Player attacker = resolveAttacker(event);
            Player victim = event.getEntity() instanceof Player p ? p : null;

            // 1. Lobby protection - prevent PvP outside game sessions
            if (handleLobbyProtection(event, attacker)) {
                return;
            }

            // 2. Respawn protection - protect newly spawned players
            if (handleRespawnProtection(event, attacker, victim)) {
                return;
            }

            // 3. Handle invisibility cloak removal (damage dealt or taken)
            if (attacker != null && victim != null) {
                handleInvisibilityRemoval(attacker, victim);
            }

            // 4. Process attacker-side effects (mythic items, custom items, combat modifiers)
            if (attacker != null && victim != null) {
                handleAttackerEffects(event, attacker, victim);
            }

            // 5. Process attacker's armor-based effects
            if (attacker != null && victim != null) {
                handleAttackerArmorEffects(event, attacker, victim);
            }

        } catch (Exception e) {
            Messages.debug("DAMAGE", "Error handling PvP damage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Resolves the actual attacker from a damage event.
     * Handles both direct player damage and projectile damage (arrows, tridents, etc.)
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ==================== KNOCKBACK IMMUNITY (Monitor Priority) ====================

    /**
     * Handles knockback immunity for Coin Cleaver mythic item.
     * Uses MONITOR priority to run after damage is processed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageKnockbackImmunity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        try {
            // Check if victim has Coin Cleaver (sturdy feet - no knockback)
            if (mythicManager.hasCoinCleaverNoKnockback(victim)) {
                Vector currentVelocity = victim.getVelocity().clone();
                SchedulerUtils.runTaskLater(() -> victim.setVelocity(currentVelocity), 1L);
            }
        } catch (Exception e) {
            Messages.debug("DAMAGE", "Error applying knockback immunity: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== HEALTH REGAIN ====================

    /**
     * Handles health regain tracking for bonus calculations.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        try {
            handleHealthRegain(event, player);
        } catch (Exception e) {
            Messages.debug("DAMAGE", "Error handling health regain: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== PROTECTION HANDLERS ====================

    /**
     * Handle lobby protection - cancel PvP outside game sessions.
     * @return true if damage was cancelled
     */
    private boolean handleLobbyProtection(EntityDamageByEntityEvent event, Player attacker) {
        if (attacker == null) {
            return false;
        }

        GameSession attackerSession = gameManager.getPlayerSession(attacker);
        if (attackerSession != null) {
            return false;
        }

        // Cancel PvP outside games
        event.setCancelled(true);
        return true;
    }

    /**
     * Handle respawn protection - cancel damage to protected players.
     * @return true if damage was cancelled
     */
    private boolean handleRespawnProtection(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (victim == null || attacker == null) {
            return false;
        }

        GameSession session = gameManager.getPlayerSession(victim);
        if (session == null) {
            return false;
        }

        CashClashPlayer ccp = session.getCashClashPlayer(victim.getUniqueId());
        if (ccp != null && ccp.isRespawnProtected()) {
            event.setCancelled(true);
            Messages.debug(victim, "DAMAGE", "Damage cancelled due to respawn protection");
            return true;
        }
        return false;
    }

    /**
     * Handle game phase protection - cancel damage during waiting/shopping.
     * @return true if damage was cancelled
     */
    private boolean handleGamePhaseProtection(EntityDamageEvent event, Player player) {
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            return false;
        }

        GameState state = session.getState();
        if (state == GameState.WAITING || state == GameState.SHOPPING) {
            event.setCancelled(true);
            Messages.debug(player, "DAMAGE", "Damage cancelled due to state: " + state);
            return true;
        }
        return false;
    }


    // ==================== ARMOR EFFECTS ====================

    /**
     * Handle custom armor defensive effects.
     */
    private void handleArmorDefenseEffects(EntityDamageEvent event, Player player) {
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            return;
        }

        double healthAfter = Math.max(0, player.getHealth() - event.getFinalDamage());
        EntityDamageEvent.DamageCause cause = event.getCause();

        // Dragon Set: immune to explosions
        if (isExplosionDamage(cause) && armorManager.hasDragonSet(player)) {
            event.setCancelled(true);
            return;
        }

        // Guardian's Vest: resistance when low health
        armorManager.onPlayerDamaged(player, healthAfter);

        // Deathmauler: track damage for absorption
        armorManager.onDeathmaulerDamageTaken(player);

        // Magic Helmet: activate on first melee damage
        if (isMeleeDamage(event)) {
            armorManager.onMagicHelmetMeleeDamage(player);
        }
    }

    /**
     * Handle armor-based attack effects.
     */
    private void handleAttackerArmorEffects(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        GameSession session = gameManager.getPlayerSession(attacker);
        if (session == null) {
            return;
        }

        // Handle all attack-based armor effects
        armorManager.onPlayerAttack(attacker, victim);

        // Investor's Set: bonus damage in rounds 4/5
        double damageMultiplier = armorManager.getInvestorMeleeDamageMultiplier(attacker, session.getCurrentRound());
        if (damageMultiplier > 1.0) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }
    }

    /**
     * Check if damage is from an explosion.
     */
    private boolean isExplosionDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
               cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
    }

    /**
     * Check if damage is from melee attack.
     */
    private boolean isMeleeDamage(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent damageByEntity &&
               damageByEntity.getDamager() instanceof Player;
    }


    // ==================== DAMAGE TRACKING & BONUSES ====================

    /**
     * Track damage for bonus calculations.
     */
    private void trackDamageForBonuses(EntityDamageEvent event, Player player) {
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            return;
        }

        RoundData currentRound = session.getCurrentRoundData();
        if (currentRound == null) {
            return;
        }

        CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (ccPlayer == null) {
            return;
        }

        // Update last damage time
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
     * Handle health regain tracking for bonuses.
     */
    private void handleHealthRegain(EntityRegainHealthEvent event, Player player) {
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) {
            return;
        }

        CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (ccPlayer == null) {
            return;
        }

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


    // ==================== ATTACKER EFFECTS ====================

    /**
     * Handle invisibility cloak removal when dealing or taking damage.
     */
    private void handleInvisibilityRemoval(Player attacker, Player victim) {
        // Remove invisibility from attacker when they deal damage
        if (customItemManager.isInvisActive(attacker.getUniqueId())) {
            customItemManager.toggleInvisCloak(attacker, false);
            Messages.send(attacker, "<red>Invisibility lost - you dealt damage!</red>");
        }

        // Remove invisibility from victim when they take damage
        if (customItemManager.isInvisActive(victim.getUniqueId())) {
            customItemManager.toggleInvisCloak(victim, false);
            Messages.send(victim, "<red>Invisibility lost - you took damage!</red>");
        }
    }

    /**
     * Handle attacker-side effects (mythic items, custom items, combat modifiers).
     */
    private void handleAttackerEffects(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Apply custom item effects
        handleCustomItemEffects(attacker, weapon);

        // Apply mythic item effects
        handleMythicItemEffects(event, attacker, victim, weapon);

        // Apply combat modifiers (strength/power nerfs)
        applyCombatModifiers(event, attacker, weapon);
    }

    /**
     * Handle custom item effects (e.g., Bag of Potatoes).
     */
    private void handleCustomItemEffects(Player attacker, ItemStack weapon) {
        if (!weapon.hasItemMeta()) {
            return;
        }

        CustomItem customType = PDCDetection.getCustomItem(weapon);
        if (customType == CustomItem.BAG_OF_POTATOES) {
            customItemManager.handleBagOfPotatoesHit(attacker, weapon);
        }
    }

    /**
     * Handle mythic item effects (legendary weapons and abilities).
     */
    private void handleMythicItemEffects(EntityDamageByEntityEvent event, Player attacker, Player victim, ItemStack weapon) {
        if (!weapon.hasItemMeta()) {
            return;
        }

        MythicItem mythic = PDCDetection.getMythic(weapon);
        if (mythic == null) {
            return;
        }

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

            case BLOODWRENCH_CROSSBOW, BLAZEBITE_CROSSBOWS -> {
                // Legendary crossbows: +30% damage boost
                if (event.getDamager() instanceof Projectile) {
                    double currentDamage = event.getDamage();
                    double boostedDamage = currentDamage * LEGENDARY_CROSSBOW_DAMAGE_BOOST;
                    event.setDamage(boostedDamage);
                    Messages.debug(attacker, "LEGENDARY_CROSSBOW: Damage boosted from " + currentDamage + " to " + boostedDamage);
                }
            }

            default -> { /* No special handling */ }
        }
    }

    /**
     * Apply combat modifiers (strength nerf, power enchantment nerf/cap).
     */
    private void applyCombatModifiers(EntityDamageByEntityEvent event, Player attacker, ItemStack weapon) {
        // Nerf strength potion effect by 50%
        applyStrengthNerf(event, attacker);

        // Nerf/cap power enchantment on bows
        applyPowerNerf(event, attacker, weapon);
    }

    /**
     * Apply strength potion nerf (50% damage reduction).
     */
    private void applyStrengthNerf(EntityDamageByEntityEvent event, Player attacker) {
        if (!attacker.hasPotionEffect(PotionEffectType.STRENGTH)) {
            return;
        }

        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        if (strength == null || strength.getAmplifier() < 0) {
            return;
        }

        // Strength adds (level + 1) * 3 damage
        // Reduce the bonus by 50%
        double currentDamage = event.getDamage();
        double strengthBonus = (strength.getAmplifier() + 1) * 3.0;
        double nerfedStrengthBonus = strengthBonus * STRENGTH_NERF_MULTIPLIER;
        double damageReduction = strengthBonus - nerfedStrengthBonus;
        double newDamage = Math.max(0, currentDamage - damageReduction);

        event.setDamage(newDamage);
        Messages.debug(attacker, "STRENGTH_NERF: Reduced damage from " + currentDamage + " to " + newDamage +
                      " (strength level " + (strength.getAmplifier() + 1) + ")");
    }

    /**
     * Apply power enchantment nerf (50% reduction) and cap (max level 2 for non-legendary bows).
     */
    private void applyPowerNerf(EntityDamageByEntityEvent event, Player attacker, ItemStack weapon) {
        Material weaponType = weapon.getType();
        if (weaponType != Material.BOW && weaponType != Material.CROSSBOW) {
            return;
        }

        if (!weapon.containsEnchantment(Enchantment.POWER)) {
            return;
        }

        int powerLevel = weapon.getEnchantmentLevel(Enchantment.POWER);
        if (powerLevel <= 0) {
            return;
        }

        // Check if it's a legendary bow (Wind Bow has Power 3)
        MythicItem mythic = PDCDetection.getMythic(weapon);
        boolean isLegendary = mythic == MythicItem.WIND_BOW;

        double currentDamage = event.getDamage();

        // Power formula: damage = base * (1 + level * 0.5)
        double originalMultiplier = 1.0 + (powerLevel * 0.5);
        double baseDamage = currentDamage / originalMultiplier;

        // Cap power at 2 for regular bows (legendary exception)
        if (!isLegendary && powerLevel > MAX_POWER_LEVEL_REGULAR_BOW) {
            double cappedMultiplier = 1.0 + (MAX_POWER_LEVEL_REGULAR_BOW * 0.5);
            double cappedDamage = baseDamage * cappedMultiplier;
            event.setDamage(cappedDamage);
            Messages.debug(attacker, "POWER_CAP: Reduced from power " + powerLevel + " to power " + MAX_POWER_LEVEL_REGULAR_BOW +
                          " (damage " + currentDamage + " -> " + cappedDamage + ")");
        } else {
            // Nerf power by 50%: reduce the power bonus multiplier by half
            // Nerfed: damage = base * (1 + level * 0.25)
            double nerfedMultiplier = 1.0 + (powerLevel * (0.5 * POWER_NERF_MULTIPLIER));
            double nerfedDamage = baseDamage * nerfedMultiplier;
            event.setDamage(nerfedDamage);
            Messages.debug(attacker, "POWER_NERF: Reduced damage from " + currentDamage + " to " + nerfedDamage +
                          " (power level " + powerLevel + ", legendary: " + isLegendary + ")");
        }
    }
}
