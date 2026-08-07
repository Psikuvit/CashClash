package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.armor.BullseyePantsHandler;
import me.psikuvit.cashClash.manager.items.armor.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.armor.DeathmaulerSetHandler;
import me.psikuvit.cashClash.manager.items.armor.DragonSetHandler;
import me.psikuvit.cashClash.manager.items.armor.FlamebringerSetHandler;
import me.psikuvit.cashClash.manager.items.armor.GuardianVestHandler;
import me.psikuvit.cashClash.manager.items.armor.InvestorSetHandler;
import me.psikuvit.cashClash.manager.items.armor.TectonicCapHandler;
import me.psikuvit.cashClash.manager.items.custom.BagOfPotatoesHandler;
import me.psikuvit.cashClash.manager.items.custom.BloomingRoseHandler;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.manager.items.custom.HuntersMarkHandler;
import me.psikuvit.cashClash.manager.items.custom.IceFanHandler;
import me.psikuvit.cashClash.manager.items.custom.InvisCloakHandler;
import me.psikuvit.cashClash.manager.items.custom.OverdriveHandler;
import me.psikuvit.cashClash.manager.items.custom.TotemOfHauntingHandler;
import me.psikuvit.cashClash.manager.items.mythic.AlchemistWandHandler;
import me.psikuvit.cashClash.manager.items.mythic.CarlsBattleaxeHandler;
import me.psikuvit.cashClash.manager.items.mythic.ElectricEelHandler;
import me.psikuvit.cashClash.manager.items.mythic.GoblinSpearHandler;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
import me.psikuvit.cashClash.manager.items.mythic.WardenGlovesHandler;
import me.psikuvit.cashClash.manager.items.weapon.SoulKatanaHandler;
import me.psikuvit.cashClash.manager.items.weapon.WeaponItemManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

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
    private final WeaponItemManager weaponItemManager;

    public DamageListener() {
        this.gameManager = GameManager.getInstance();
        this.armorManager = CustomArmorManager.getInstance();
        this.customItemManager = CustomItemManager.getInstance();
        this.mythicManager = MythicItemManager.getInstance();
        this.weaponItemManager = WeaponItemManager.getInstance();
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
            // 0. Totem of Haunting - brief invincibility window after triggering
            if (customItemManager.getHandler(TotemOfHauntingHandler.class).isTotemInvincible(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // 0b. Overdrive Potion - total invincibility while active
            if (customItemManager.getHandler(OverdriveHandler.class).isOverdriveInvincible(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // 0c. Dragon Rush teammate rush - brief invincibility window
            if (armorManager.getHandler(DragonSetHandler.class).isDragonRushInvincible(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // 1. Check game phase protection (waiting/shopping)
            if (handleGamePhaseProtection(event, player)) {
                return;
            }

            // 1b. Hunter's Mark - marked players take extra damage (base + per missing heart)
            double vulnerability = customItemManager.getHandler(HuntersMarkHandler.class).getVulnerabilityMultiplier(player.getUniqueId());
            if (vulnerability > 1.0) {
                event.setDamage(event.getDamage() * vulnerability);
            }

            // 1b2. Alchemist Wand Taunt - the wielder takes extra damage for the whole
            // duration, both their own direct hits and damage redirected onto them from
            // chained teammates (the redirect deals the raw, unamplified amount, relying on
            // this check to apply the increase uniformly instead of double-counting it).
            if (mythicManager.getHandler(AlchemistWandHandler.class).isTaunting(player.getUniqueId())) {
                double increase = ItemsConfig.getInstance().getAlchemistTauntDamageIncreasePercent();
                event.setDamage(event.getDamage() * (1.0 + increase / 100.0));
            }

            // 1c. Blooming Rose - same-team zone: reduce damage and clamp so health never drops
            // below the 2-heart floor (the base clamp guarantees final damage can't exceed it)
            double roseReduction = customItemManager.getHandler(BloomingRoseHandler.class).getBloomingRoseDamageReduction(player);
            if (roseReduction > 0) {
                event.setDamage(event.getDamage() * (1.0 - roseReduction / 100.0));
                double floor = customItemManager.getHandler(BloomingRoseHandler.class).getBloomingRoseMinHealth(player);
                double maxDamage = Math.max(0, player.getHealth() - floor);
                event.setDamage(Math.min(event.getDamage(), maxDamage));
            }

            // 2. Handle custom armor defensive effects
            handleArmorDefenseEffects(event, player);

            // 3. Track damage for bonus calculations
            trackDamageForBonuses(event, player);

            // 4. Alchemist Wand Blink Swap protection
            if (mythicManager.getHandler(AlchemistWandHandler.class).handleAlchemistBlinkProtection(player)) {
                event.setCancelled(true);
                return;
            }

        } catch (Exception e) {
            logDamageError(player, e);
        }
    }

    /**
     * Log damage handling error
     */
    private void logDamageError(Player player, Exception e) {
        Messages.debug("DAMAGE", "Error handling damage for " + player.getName() + ": " + e.getMessage());
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

            // Ice Fan is a pure ability-tool (gust/burst) - suppress vanilla melee swings from
            // it entirely, only letting through damage explicitly dealt via its abilities
            if (onIceFanMeleeSuppression(event, attacker)) {
                return;
            }

            // Apply protection checks
            if (applyProtectionChecks(event, attacker, victim)) {
                return;
            }

            // Alchemist Wand Taunt - a chained teammate takes zero damage, it's all redirected
            // to the wielder instead (amplified by the wielder's own damage-increase check above)
            if (victim != null && redirectAlchemistTauntDamage(event, victim)) {
                return;
            }

            // Soul Katana Phantom Slice: zero armor/effect-based damage modifiers so the flat
            // ability strike lands untouched (transient flag set only around the direct damage call)
            if (attacker != null && victim != null && weaponItemManager.getHandler(SoulKatanaHandler.class).isPhantomSliceDamage(attacker.getUniqueId())) {
                applyPhantomSliceDamageModifiers(event);
            }

            // Process damage effects
            if (attacker != null && victim != null) {
                processVictimDamageEffects(attacker, victim);
                handleAttackerEffects(event, attacker, victim);
                handleAttackerArmorEffects(event, attacker, victim);
            }

        } catch (Exception e) {
            logPvPDamageError(e);
        }
    }

    /**
     * Apply all protection checks (lobby, respawn, team damage)
     */
    private boolean applyProtectionChecks(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (attacker != null && victim != null) {
            UUID attackerId = attacker.getUniqueId();
            UUID victimId = victim.getUniqueId();

            // Special case: Goblin Spear Charge
            // If attacker is charging and victim is caught by THIS attacker, allow damage (bypass protection)
            if (mythicManager.getHandler(GoblinSpearHandler.class).isGoblinSpearCharging(attackerId)) {
                UUID victimCharger = mythicManager.getHandler(GoblinSpearHandler.class).getGoblinChargerOf(victimId);
                if (attackerId.equals(victimCharger)) {
                    return false; // Allow damage between charger and their victim
                }
            }
            
            // Allow chargers to be hit
            if (mythicManager.getHandler(GoblinSpearHandler.class).isGoblinSpearCharging(victimId)) {
                return false;
            }
        }

        if (handleLobbyProtection(event, attacker)) {
            return true;
        }
        if (handleTeamDamage(event, attacker, victim)) {
            return true;
        }
        return handleRespawnProtection(event, attacker, victim);
    }

    /**
     * Process damage effects on victim
     */
    private void processVictimDamageEffects(Player attacker, Player victim) {
        handleInvisibilityRemoval(attacker, victim);
    }

    /**
     * Soul Katana Phantom Slice: zeroes the ARMOR/MAGIC/RESISTANCE/ABSORPTION damage modifiers
     * and pins BASE to the flat strike damage so armor and active effects on both sides are
     * ignored. All calls are guarded by {@link EntityDamageEvent#isApplicable(DamageModifier)}
     * before modifying a modifier.
     */
    private void applyPhantomSliceDamageModifiers(EntityDamageByEntityEvent event) {
        for (EntityDamageEvent.DamageModifier modifier : new EntityDamageEvent.DamageModifier[]{
                EntityDamageEvent.DamageModifier.ARMOR,
                EntityDamageEvent.DamageModifier.MAGIC,
                EntityDamageEvent.DamageModifier.RESISTANCE,
                EntityDamageEvent.DamageModifier.ABSORPTION}) {
            if (event.isApplicable(modifier)) {
                event.setDamage(modifier, 0);
            }
        }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.BASE)) {
            event.setDamage(EntityDamageEvent.DamageModifier.BASE,
                    ItemsConfig.getInstance().getSoulKatanaStrikeDamage());
        }
    }

    /**
     * Log PvP damage error
     */
    private void logPvPDamageError(Exception e) {
        Messages.debug("DAMAGE", "Error handling PvP damage: " + e.getMessage());
    }

    /**
     * Alchemist Wand Taunt: if the victim is currently chained to a Taunt wielder, cancel their
     * damage entirely and deal the equivalent (raw, unamplified) amount to the wielder instead -
     * see AlchemistWandHandler#redirectTauntDamage for the re-entrancy guard around that call,
     * and the "1b2" check in onEntityDamage for where the wielder's own damage increase applies.
     */
    private boolean redirectAlchemistTauntDamage(EntityDamageByEntityEvent event, Player victim) {
        AlchemistWandHandler handler = mythicManager.getHandler(AlchemistWandHandler.class);
        UUID wielderUuid = handler.getTauntRedirectTarget(victim.getUniqueId());
        if (wielderUuid == null) return false;

        Player wielder = Bukkit.getPlayer(wielderUuid);
        if (wielder == null || !wielder.isOnline() || handler.isRedirecting(wielderUuid)) return false;

        event.setCancelled(true);
        handler.redirectTauntDamage(wielder, event.getFinalDamage(), event.getDamager());
        return true;
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

    // ==================== TOTEM OF HAUNTING ====================

    /**
     * Cancels a would-be-lethal hit from another player when the victim holds a Totem of
     * Haunting, triggering its death-save instead. Runs at HIGHEST (after the main HIGH-priority
     * handlers have applied their damage modifiers) so the lethality check reflects final damage.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamageTotemCheck(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() > 0) {
            return;
        }

        ItemStack main = victim.getInventory().getItemInMainHand();
        ItemStack off = victim.getInventory().getItemInOffHand();
        ItemStack totem;
        if (PDCDetection.getCustomItem(main) == CustomItem.TOTEM_OF_HAUNTING) {
            totem = main;
        } else if (PDCDetection.getCustomItem(off) == CustomItem.TOTEM_OF_HAUNTING) {
            totem = off;
        } else {
            return;
        }

        event.setCancelled(true);
        customItemManager.getHandler(TotemOfHauntingHandler.class).triggerTotemOfHaunting(victim, totem);
    }

    /**
     * Cancels vanilla melee damage from Ice Fan (a pure ability-tool - its own gust/burst hits
     * flow through {@link IceFanHandler#isIceFanAbilityDamage(UUID)} and are let through).
     */
    private boolean onIceFanMeleeSuppression(EntityDamageByEntityEvent event, Player attacker) {
        if (attacker == null) return false;
        if (PDCDetection.getCustomItem(attacker.getInventory().getItemInMainHand()) != CustomItem.ICE_FAN) return false;
        if (customItemManager.getHandler(IceFanHandler.class).isIceFanAbilityDamage(attacker.getUniqueId())) return false;

        event.setCancelled(true);
        return true;
    }

    // ==================== KNOCKBACK IMMUNITY (Monitor Priority) ====================

    /**
     * Checks fall damage for the Tectonic Cap and Dragon Outrage landings. Runs at NORMAL
     * priority so the final damage reflects all modifiers while still being cancellable.
     */
    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        armorManager.getHandler(DragonSetHandler.class).onDragonOutrageLanding(event, player);
        armorManager.getHandler(TectonicCapHandler.class).onTectonicCapFall(event, player);
    }

    /**
     * Handle post-damage effects with MONITOR priority (after all damage modifications).
     * Processes: Flamebringer fire KB immunity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPostDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Wind Charge Fall Damage Fix
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Check if player recently used or was hit by a wind charge
            if (CooldownManager.getInstance().getRemainingCooldownMs(player.getUniqueId(), "WIND_CHARGE_PROTECTION") > 0) {
                event.setCancelled(true);
                return;
            }
        }

        // Flamebringer: Negate knockback from fire damage
        if ((event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
             event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
             event.getCause() == EntityDamageEvent.DamageCause.LAVA) &&
            armorManager.getHandler(FlamebringerSetHandler.class).hasFlamebringerNoFireKb(player)) {

            // Schedule to reset velocity after knockback is applied
            SchedulerUtils.runTask(() -> {
                if (player.isOnline()) {
                    player.setVelocity(player.getVelocity().multiply(0));
                }
            });
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
     * Handle team damage - prevent players from damaging teammates.
     * @return true if damage was cancelled
     */
    private boolean handleTeamDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return false;
        }

        GameSession session = gameManager.getPlayerSession(attacker);
        if (session == null) {
            return false;
        }

        // Check if victim is on the same team as attacker
        if (session.getTeamRed().hasPlayer(attacker.getUniqueId()) &&
            session.getTeamRed().hasPlayer(victim.getUniqueId())) {
            event.setCancelled(true);
            return true;
        }

        if (session.getTeamBlue().hasPlayer(attacker.getUniqueId()) &&
            session.getTeamBlue().hasPlayer(victim.getUniqueId())) {
            event.setCancelled(true);
            return true;
        }

        return false;
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

        // Allow attacker to deal damage even if they are protected
        if (attacker != null) {
            CashClashPlayer attackerCcp = session.getCashClashPlayer(attacker.getUniqueId());
            if (attackerCcp != null && attackerCcp.isRespawnProtected()) {
                return false;
            }
        }

        CashClashPlayer victimCcp = session.getCashClashPlayer(victim.getUniqueId());
        if (victimCcp != null && victimCcp.isRespawnProtected()) {
            // Allow damage if victim is currently charging with Goblin Spear
            if (mythicManager.getHandler(GoblinSpearHandler.class).isGoblinSpearCharging(victim.getUniqueId())) {
                return false;
            }

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
        if (state == GameState.WAITING || state == GameState.SHOPPING
                || session.isActionsRestricted() || session.isDamageDisabled()) {
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

        // Dragon Set: no explosion immunity
        
        // Guardian's Vest: resistance when low health
        armorManager.getHandler(GuardianVestHandler.class).onPlayerDamaged(player, healthAfter);

        // Deathmauler: track damage for absorption
        armorManager.getHandler(DeathmaulerSetHandler.class).onDeathmaulerDamageTaken(player);

        // Flamebringer: lava trigger speed
        if (cause == EntityDamageEvent.DamageCause.LAVA) {
            armorManager.getHandler(FlamebringerSetHandler.class).onFlamebringerLavaDamage(player);
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

        // Dragon Set: charge a scale on fully-charged melee hits
        armorManager.getHandler(DragonSetHandler.class).handleDragonHit(attacker);

        // Dragon Set: apply empowered Dragon Rush strike
        armorManager.getHandler(DragonSetHandler.class).onDragonRushHit(event);

        // Bullseye Pants: Storming arrow
        handleBullseyePantsEffect(event, attacker, victim);

        // Deathmauler: Soul Burst
        armorManager.getHandler(DeathmaulerSetHandler.class).tryDeathmaulerSoulBurst(attacker, victim, session);
    }

    /**
     * Handle Bullseye Pants "Storming arrow" passive.
     * Headshots trigger a storm arrow immediately (no counter increment).
     * Every 4th non-headshot landed arrow does 30% more damage and deals AOE damage.
     */
    private void handleBullseyePantsEffect(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        if (!armorManager.getHandler(BullseyePantsHandler.class).hasBullseyePants(attacker)) {
            return;
        }

        // ---------------- HEADSHOT CHECK ----------------
        org.bukkit.Location hitLoc = arrow.getLocation();
        double arrowY = hitLoc.getY();
        double headY = victim.getLocation().getY() + victim.getEyeHeight();
        boolean isHeadshot = Math.abs(arrowY - headY) <= 0.25;

        if (isHeadshot) {
            SoundUtils.playAt(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.4f);
            triggerStorm(attacker, victim, event.getDamage() * 1.3);
            return;
        }

        if (armorManager.getHandler(BullseyePantsHandler.class).incrementBullseyeHit(attacker)) {
            // 4th non-headshot hit triggered
            double originalDamage = event.getDamage();
            event.setDamage(originalDamage * 1.3); // +30% damage

            triggerStorm(attacker, victim, originalDamage);
            Messages.send(attacker, "armor.bullseye-storm-triggered");
        }
    }

    /**
     * Spawn the storm arrow burst: impact particles, wind burst sound, and 6 AOE arrows
     * firing outward (uncolored so they read as normal arrows).
     */
    private void triggerStorm(Player attacker, Player victim, double aoeBaseDamage) {
        org.bukkit.Location impact = victim.getLocation().add(0, 1, 0);
        ParticleUtils.bullseyeStorm(impact);
        SoundUtils.playAt(impact, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.1f, 1.2f);

        double aoeDamage = aoeBaseDamage * 0.35;
        for (int i = 0; i < 6; i++) {
            double angle = 2 * Math.PI * i / 6;
            Vector direction = new Vector(Math.cos(angle), 0.2, Math.sin(angle)).normalize();

            Arrow aoeArrow = attacker.getWorld().spawn(impact, Arrow.class);
            aoeArrow.setShooter(attacker);
            aoeArrow.setVelocity(direction.multiply(1.5));
            aoeArrow.setCritical(true);
            aoeArrow.setDamage(aoeDamage);
            aoeArrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            SchedulerUtils.runTaskLater(aoeArrow::remove, 40L);
        }
    }

    /**
     * Check if damage is from an explosion.
     */
    private boolean isExplosionDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
               cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
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

        // Update last damage time (combat-grace-period checks read this)
        currentRound.setLastDamageTime(player.getUniqueId(), System.currentTimeMillis());

        if (event.getDamage() <= 0) {
            return;
        }

        // Attribute damage dealt to the attacker (for the Most Damage bonus), not the victim
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = resolveAttacker(byEntity);
            if (attacker != null) {
                currentRound.addDamage(attacker.getUniqueId(), event.getFinalDamage());
            }
        }
    }


    // ==================== ATTACKER EFFECTS ====================

    /**
     * Handle invisibility cloak removal when dealing or taking damage.
     */
    private void handleInvisibilityRemoval(Player attacker, Player victim) {
        // Remove invisibility from attacker when they deal damage
        if (customItemManager.getHandler(InvisCloakHandler.class).isInvisActive(attacker.getUniqueId())) {
            customItemManager.getHandler(InvisCloakHandler.class).toggleInvisCloak(attacker, false);
            Messages.send(attacker, "listener.invisibility-lost-attacker");
        }

        // Remove invisibility from victim when they take damage
        if (customItemManager.getHandler(InvisCloakHandler.class).isInvisActive(victim.getUniqueId())) {
            customItemManager.getHandler(InvisCloakHandler.class).toggleInvisCloak(victim, false);
            Messages.send(victim, "listener.invisibility-lost-victim");
        }
    }

    /**
     * Handle attacker-side effects (mythic items, custom items, combat modifiers).
     */
    private void handleAttackerEffects(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Apply custom item effects
        handleCustomItemEffects(attacker, weapon, session);

        // Apply mythic item effects
        handleMythicItemEffects(event, attacker, victim, weapon);

        // Apply combat modifiers (strength/power nerfs)
        applyCombatModifiers(event, attacker, weapon);
    }

    /**
     * Handle custom item effects (e.g., Bag of Potatoes).
     */
    private void handleCustomItemEffects(Player attacker, ItemStack weapon, GameSession session) {
        if (!isValidWeapon(weapon)) {
            return;
        }

        CustomItem customType = PDCDetection.getCustomItem(weapon);
        if (customType == CustomItem.BAG_OF_POTATOES) {
            customItemManager.getHandler(BagOfPotatoesHandler.class).handleBagOfPotatoesHit(attacker, weapon, session);
        }
    }

    /**
     * Check if weapon is valid (has item meta)
     */
    private boolean isValidWeapon(ItemStack weapon) {
        return weapon.hasItemMeta();
    }

    /**
     * Handle mythic item effects (legendary weapons and abilities).
     */
    private void handleMythicItemEffects(EntityDamageByEntityEvent event, Player attacker, Player victim, ItemStack weapon) {
        if (!isValidWeapon(weapon)) {
            return;
        }

        MythicItem mythic = PDCDetection.getMythic(weapon);
        if (mythic == null) {
            return;
        }

        processMythicDamageEffects(event, attacker, victim, mythic);
    }

    /**
     * Process damage effects for specific mythic items
     */
    private void processMythicDamageEffects(EntityDamageByEntityEvent event, Player attacker, Player victim, MythicItem mythic) {
        switch (mythic) {
            case CARLS_BATTLEAXE -> applyMythicCriticalEffect(event, attacker, victim, mythicManager.getHandler(CarlsBattleaxeHandler.class)::handleCarlsCriticalHit);
            case ELECTRIC_EEL_SWORD -> applyMythicCriticalEffect(event, attacker, victim, mythicManager.getHandler(ElectricEelHandler.class)::handleElectricEelChain);
            case WARDEN_GLOVES -> mythicManager.getHandler(WardenGlovesHandler.class).useWardenPunch(attacker, victim);
            case GOBLIN_SPEAR -> applyGoblinSpearEffect(event, attacker, victim);
            case BLOODWRENCH_CROSSBOW, BLAZEBITE_CROSSBOWS -> applyLegendaryCrossbowBoost(event, attacker);
            case ALCHEMIST_WAND -> mythicManager.getHandler(AlchemistWandHandler.class).onAlchemistMeleeHit(attacker, victim);
            default -> { /* No special handling */ }
        }
    }

    /**
     * Apply mythic critical hit effect
     */
    private void applyMythicCriticalEffect(EntityDamageByEntityEvent event, Player attacker, Player victim, MythicEffectHandler handler) {
        if (event.isCritical()) {
            handler.apply(attacker, victim);
        }
    }

    /**
     * Apply Goblin Spear melee effect
     */
    private void applyGoblinSpearEffect(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (event.getDamager() instanceof Player) {
            mythicManager.getHandler(GoblinSpearHandler.class).handleGoblinSpearHit(attacker, victim, true);
        }
    }

    /**
     * Apply legendary crossbow damage boost
     */
    private void applyLegendaryCrossbowBoost(EntityDamageByEntityEvent event, Player attacker) {
        if (event.getDamager() instanceof Projectile) {
            double currentDamage = event.getDamage();
            double boostedDamage = currentDamage * LEGENDARY_CROSSBOW_DAMAGE_BOOST;
            event.setDamage(boostedDamage);
            Messages.debug(attacker, "LEGENDARY_CROSSBOW: Damage boosted from " + currentDamage + " to " + boostedDamage);
        }
    }

    /**
     * Functional interface for mythic effect handlers
     */
    @FunctionalInterface
    private interface MythicEffectHandler {
        void apply(Player attacker, Player victim);
    }


    /**
     * Apply combat modifiers (strength nerf, power enchantment nerf/cap).
     */
    private void applyCombatModifiers(EntityDamageByEntityEvent event, Player attacker, ItemStack weapon) {
        // Nerf strength potion effect by 50%
        applyStrengthNerf(event, attacker);

        // Nerf/cap power enchantment on bows
        applyPowerNerf(event, attacker, weapon);

        // Investor's Set: melee damage tradeoff for the set's passive team income
        applyInvestorMeleeNerf(event, attacker);
    }

    /**
     * Investor's Set reduces the wearer's melee damage 5% per piece worn (e.g. full set =
     * -20%), as a tradeoff for the set's passive team income on kills/objectives. Melee only -
     * projectile damage (bows/crossbows) is untouched.
     */
    private void applyInvestorMeleeNerf(EntityDamageByEntityEvent event, Player attacker) {
        if (!(event.getDamager() instanceof Player)) return;

        double multiplier = armorManager.getHandler(InvestorSetHandler.class).getMeleeDamageMultiplier(attacker);
        if (multiplier >= 1.0) return;

        event.setDamage(event.getDamage() * multiplier);
        Messages.debug("ARMOR", "InvestorSet: Reduced melee damage from " + event.getDamage() / multiplier + " to " + event.getDamage());
    }

    /**
     * Apply strength potion nerf (50% damage reduction).
     */
    private void applyStrengthNerf(EntityDamageByEntityEvent event, Player attacker) {
        if (!CashClashPlayer.hasEffect(attacker, PotionEffectType.STRENGTH)) {
            return;
        }

        PotionEffect strength = CashClashPlayer.getEffect(attacker, PotionEffectType.STRENGTH);
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
