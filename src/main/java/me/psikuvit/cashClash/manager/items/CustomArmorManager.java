package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles runtime behavior for custom armor pieces (effects, cooldowns, detection helpers).
 */
public class CustomArmorManager {

    private static CustomArmorManager instance;

    private final CooldownManager cooldownManager;

    private final Map<UUID, Integer> magicHelmetEffectIndex;

    // Bunny Shoes tracking
    private final Map<UUID, Boolean> bunnyToggleReady;

    // Guardian's Vest tracking
    private final Map<UUID, Integer> guardianUsesThisRound;

    // Deathmauler tracking
    private final Map<UUID, Integer> deathmaulerExtraHearts;

    // Dragon Set tracking
    private final Map<UUID, UUID> dragonMarkedTargets; // Attacker -> Marked Target
    private final Map<UUID, BukkitTask> dragonMarkTasks; // Marked player -> particle task
    private final Map<UUID, Double> dragonDamageBoost; // Attacker -> damage boost for next hit

    // Flamebringer Set tracking
    private final Map<UUID, Integer> flamebringerKills; // Player -> kill count this round
    private final Map<UUID, BukkitTask> flamebringerFireTask; // Player -> fire effect task
    private final Map<UUID, Integer> flamebringerLavaUses; // Player -> lava speed procs this game
    private final Map<UUID, Long> flamebringerSpeedEndTime; // Player -> time when speed effect should end

    private final Random random;

    private CustomArmorManager() {
        this.cooldownManager = CooldownManager.getInstance();
        this.magicHelmetEffectIndex = new ConcurrentHashMap<>();

        this.bunnyToggleReady = new ConcurrentHashMap<>();

        this.guardianUsesThisRound = new ConcurrentHashMap<>();

        this.deathmaulerExtraHearts = new ConcurrentHashMap<>();

        this.dragonMarkedTargets = new ConcurrentHashMap<>();
        this.dragonMarkTasks = new ConcurrentHashMap<>();
        this.dragonDamageBoost = new ConcurrentHashMap<>();

        this.flamebringerKills = new ConcurrentHashMap<>();
        this.flamebringerFireTask = new ConcurrentHashMap<>();
        this.flamebringerLavaUses = new ConcurrentHashMap<>();
        this.flamebringerSpeedEndTime = new ConcurrentHashMap<>();

        this.random = new Random();
    }

    public static CustomArmorManager getInstance() {
        if (instance == null) {
            instance = new CustomArmorManager();
        }
        return instance;
    }

    private List<CustomArmorItem> getEquippedCustomArmor(Player p) {
        List<CustomArmorItem> found = new ArrayList<>();
        for (ItemStack is : p.getInventory().getArmorContents()) {
            if (is == null) continue;

            CustomArmorItem P = PDCDetection.getCustomArmor(is);
            if (P == null) continue;
            found.add(P);
        }
        return found;
    }

    public int countInvestorsPieces(Player p) {
        int cnt = 0;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca.isInvestorsSet()) cnt++;
        }
        return cnt;
    }

    public boolean hasTaxEvasion(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.TAX_EVASION_PANTS) return true;
        }
        return false;
    }

    public boolean hasMagicHelmet(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.MAGIC_HELMET) return true;
        }
        return false;
    }

    public boolean hasDeathmaulerSet(Player p) {
        boolean hasChest = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DEATHMAULER_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DEATHMAULER_LEGGINGS) hasLegs = true;
        }
        return hasChest && hasLegs;
    }

    public boolean hasDragonSet(Player p) {
        boolean hasChest = false, hasBoots = false, hasHelmet = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.DRAGON_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DRAGON_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.DRAGON_HELMET) hasHelmet = true;
        }
        return hasChest && hasBoots && hasHelmet;
    }

    public boolean hasBunnyShoes(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.BUNNY_SHOES) return true;
        }
        return false;
    }

    public boolean hasGuardianVest(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.GUARDIANS_VEST) return true;
        }
        return false;
    }

    public boolean hasFlamebringerSet(Player p) {
        boolean hasBoots = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.FLAMEBRINGER_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.FLAMEBRINGER_LEGGINGS) hasLegs = true;
        }
        return hasBoots && hasLegs;
    }

    // ==================== MAGIC HELMET ====================
    
    // Track if magic helmet has been activated this round (reset on round start)
    private final Set<UUID> magicHelmetActivated = ConcurrentHashMap.newKeySet();

    /**
     * Magic Helmet activates on first melee damage taken:
     * Plays all three effects sequentially in order:
     * 1. Resistance I (4s)
     * 2. Absorption I (4s) 
     * 3. Speed I (4s)
     * 25 second cooldown after speed wears off
     */
    public void onMagicHelmetMeleeDamage(Player p) {
        if (!hasMagicHelmet(p)) return;

        UUID id = p.getUniqueId();
        
        // Only activate once per round (until cooldown ends)
        if (magicHelmetActivated.contains(id)) return;
        
        // Check if on cooldown
        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.MAGIC_HELMET)) {
            return;
        }

        // Mark as activated
        magicHelmetActivated.add(id);
        
        // Play effects sequentially
        // 1. Resistance I (4s)
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 4 * 20, 0, false, true, true));
        Messages.send(p, "armor.magic-helmet-absorption");
        SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.4f);
        
        // 2. Absorption I after 4 seconds
        SchedulerUtils.runTaskLater(() -> {
            if (p.isOnline() && hasMagicHelmet(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 4 * 20, 0, false, true, true));
                Messages.send(p, "armor.magic-helmet-resistance");
                SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            }
        }, 4 * 20L);
        
        // 3. Speed I after 8 seconds (4s resistance + 4s absorption)
        SchedulerUtils.runTaskLater(() -> {
            if (p.isOnline() && hasMagicHelmet(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4 * 20, 0, false, true, true));
                Messages.send(p, "armor.magic-helmet-speed");
                SoundUtils.play(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.6f);
                
                // Start 25 second cooldown after speed wears off (4 seconds)
                SchedulerUtils.runTaskLater(() -> {
                    if (p.isOnline()) {
                        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.MAGIC_HELMET, 25);
                        magicHelmetActivated.remove(id); // Reset activation flag after cooldown starts
                        Messages.send(p, "armor.magic-helmet-cooldown");
                    }
                }, 4 * 20L);
            }
        }, 8 * 20L);
    }
    
    /**
     * Reset magic helmet activation tracking for a new round.
     */
    public void resetMagicHelmetForRound(UUID playerId) {
        magicHelmetActivated.remove(playerId);
    }

    public void onPlayerAttack(Player attacker, Player target) {
        // Dragon Set: Mark target on first hit
        if (hasDragonSet(attacker)) {
            handleDragonMarkOnHit(attacker, target);
        }
    }

    // ==================== DRAGON SET ====================

    /**
     * Dragon Set: Mark target for 4 seconds on hit.
     * Allows dash to marked target within 5 blocks.
     * Next hit on marked target deals 25% more damage.
     */
    private void handleDragonMarkOnHit(Player attacker, Player target) {
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        // If already have an active mark, do not re-mark
        if (dragonMarkedTargets.containsKey(attackerId) && cooldownManager.isOnCooldown(attackerId, CooldownManager.Keys.DRAGON_MARK_EXPIRE)) {
            return;
        }

        // Check if on cooldown
        if (cooldownManager.isOnCooldown(attackerId, CooldownManager.Keys.DRAGON_DASH)) {
            return;
        }

        // Mark the target
        dragonMarkedTargets.put(attackerId, targetId);

        // Cancel any existing mark particle task for this target
        BukkitTask existingTask = dragonMarkTasks.remove(targetId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Show particles above marked target
        BukkitTask markTask = SchedulerUtils.runTaskTimer(() -> {
            Player markedPlayer = org.bukkit.Bukkit.getPlayer(targetId);
            if (markedPlayer != null && markedPlayer.isOnline()) {
                ParticleUtils.dragonMark(markedPlayer.getLocation());
            }
        }, 0L, 10L); // Every 0.5 seconds

        dragonMarkTasks.put(targetId, markTask);

        // Set mark expiration
        int markDuration = cfg.getDragonMarkDuration();
        cooldownManager.setCooldownSeconds(attackerId, CooldownManager.Keys.DRAGON_MARK_EXPIRE, markDuration);

        Messages.send(attacker, "armor.dragon-target-marked");
        SoundUtils.play(attacker, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // Clear mark after duration
        SchedulerUtils.runTaskLater(() -> {
            if (dragonMarkedTargets.get(attackerId) != null && dragonMarkedTargets.get(attackerId).equals(targetId)) {
                dragonMarkedTargets.remove(attackerId);
                BukkitTask task = dragonMarkTasks.remove(targetId);
                if (task != null) {
                    task.cancel();
                }
            }
        }, markDuration * 20L);
    }

    /**
     * Try to dash to marked target (triggered by right-click).
     */
    public boolean tryDragonDash(Player attacker) {
        if (!hasDragonSet(attacker)) return false;

        UUID attackerId = attacker.getUniqueId();
        UUID targetId = dragonMarkedTargets.get(attackerId);

        if (targetId == null) {
            // No marked target - silently fail
            return false;
        }

        // Check if mark expired
        if (!cooldownManager.isOnCooldown(attackerId, CooldownManager.Keys.DRAGON_MARK_EXPIRE)) {
            dragonMarkedTargets.remove(attackerId);
            BukkitTask task = dragonMarkTasks.remove(targetId);
            if (task != null) {
                task.cancel();
            }
            // Silently fail - no message
            return false;
        }

        Player target = org.bukkit.Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            // Silently fail - no message
            dragonMarkedTargets.remove(attackerId);
            BukkitTask task = dragonMarkTasks.remove(targetId);
            if (task != null) {
                task.cancel();
            }
            return false;
        }

        // Check distance
        ItemsConfig cfg = ItemsConfig.getInstance();
        double dashRange = cfg.getDragonDashRange();
        double distance = attacker.getLocation().distance(target.getLocation());

        if (distance > dashRange) {
            // Silently fail - no message
            return false;
        }

        // Perform dash
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        org.bukkit.Location targetLoc = target.getLocation().clone().add(direction.multiply(-0.5));
        targetLoc.setYaw(target.getLocation().getYaw());
        targetLoc.setPitch(target.getLocation().getPitch());
        attacker.teleport(targetLoc);
        attacker.setVelocity(new Vector(0, 0.2, 0));

        // Store damage boost for next hit
        dragonDamageBoost.put(attackerId, cfg.getDragonDamageBoost());

        // Remove mark and start cooldown
        dragonMarkedTargets.remove(attackerId);
        BukkitTask task = dragonMarkTasks.remove(targetId);
        if (task != null) {
            task.cancel();
        }

        cooldownManager.setCooldownSeconds(attackerId, CooldownManager.Keys.DRAGON_DASH, cfg.getDragonCooldown());

        // Effects only - no chat messages
        ParticleUtils.dragonDashTrail(attacker.getLocation());
        SoundUtils.play(attacker, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);

        return true;
    }

    /**
     * Get and consume the dragon damage boost for an attacker.
     */
    public double getDragonDamageBoost(Player attacker) {
        Double boost = dragonDamageBoost.remove(attacker.getUniqueId());
        return boost != null ? boost : 0.0;
    }

    /**
     * Handle dragon set kill effect: Strength I for 4 seconds + orange glow for 1 second.
     */
    public void onDragonKill(Player killer) {
        if (!hasDragonSet(killer)) return;

        ItemsConfig cfg = ItemsConfig.getInstance();

        // Grant Strength I
        killer.addPotionEffect(new PotionEffect(
            PotionEffectType.STRENGTH,
            cfg.getDragonKillStrengthDuration() * 20,
            cfg.getDragonKillStrengthLevel()
        ));

        // Grant Glowing (orange)
        killer.addPotionEffect(new PotionEffect(
            PotionEffectType.GLOWING,
            cfg.getDragonKillGlowDuration() * 20,
            0
        ));

        Messages.send(killer, "armor.dragon-kill-buff");
        SoundUtils.play(killer, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.8f);
    }

    // ==================== BUNNY SHOES ====================

    public void onPlayerToggleSneak(Player p, boolean sneaking) {
        if (!hasBunnyShoes(p)) return;
        UUID id = p.getUniqueId();

        if (sneaking) {
            bunnyToggleReady.put(id, true);
        } else {
            Boolean ready = bunnyToggleReady.get(id);
            if (ready != null && ready) {
                bunnyToggleReady.put(id, false);
                tryActivateBunnyShoes(p);
            }
        }
    }

    private void tryActivateBunnyShoes(Player p) {
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();

        // Check if player is silenced (carrying enemy flag in CTF)
        if (isSilenced(p)) {
            Messages.send(p, "listener.cannot-use-items-while-silenced");
            return;
        }

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.BUNNY_SHOES)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES);
            Messages.send(p, "armor.bunny-shoes-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        int duration = cfg.getBunnyShoesDuration();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration * 20, 0));
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES, cfg.getBunnyShoesCooldown());

        Messages.send(p, "armor.bunny-shoes-activated", "duration", String.valueOf(duration));
        SoundUtils.play(p, Sound.ENTITY_RABBIT_JUMP, 1.0f, 1.5f);
    }

    // ==================== GUARDIAN'S VEST ====================

    public void onPlayerDamaged(Player p, double healthAfter) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();

        if (healthAfter > 8.0) return; // 4 hearts = 8 HP

        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.GUARDIAN_VEST)) return;

        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 1));
        guardianUsesThisRound.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.GUARDIAN_VEST, 20);

        Messages.send(p, "armor.guardian-vest-activated", "uses", String.valueOf(used + 1));
        SoundUtils.play(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
    }

    // ==================== DEATHMAULER'S OUTFIT ====================

    public void onPlayerKill(Player killer, GameSession session) {
        if (!hasDeathmaulerSet(killer)) return;
        UUID id = killer.getUniqueId();

        // Heal 4 hearts (8 HP) using centralized health system for max health
        CashClashPlayer killerCCP = session != null ? session.getCashClashPlayer(id) : null;
        double maxHealth = killerCCP != null ? killerCCP.getMaxHealth() : 20.0;
        double newHealth = Math.min(maxHealth, killer.getHealth() + 8.0);
        killer.setHealth(newHealth);

        Messages.send(killer, "armor.deathmauler-heal");

        // Show small healing particle effect on normal kills
        ParticleUtils.deathmaulerHeal(killer.getLocation());
        SoundUtils.play(killer, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.5f);
    }

    public void onDeathmaulerDamageTaken(Player p) {
        if (!hasDeathmaulerSet(p)) return;
        UUID id = p.getUniqueId();
        ItemsConfig cfg = ItemsConfig.getInstance();
        cooldownManager.setTimestamp(id, CooldownManager.Keys.DEATHMAULER_DAMAGE);

        int delaySeconds = cfg.getDeathmaulerAbsorptionDelay();
        // Schedule absorption check after configured delay without damage
        SchedulerUtils.runTaskLater(() -> {
            if (!cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.DEATHMAULER_DAMAGE, delaySeconds)) {
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 0));
            Messages.send(p, "armor.deathmauler-absorption");
        }, delaySeconds * 20L);
    }

    // ==================== DEATHMAULER SOUL BURST ====================

    public void tryDeathmaulerSoulBurst(Player attacker, Player victim, GameSession session) {
        if (!hasDeathmaulerSet(attacker) || victim == null) return;
        UUID id = attacker.getUniqueId();

        // Use centralized health system for correct max health
        var attackerCCP = session != null ? session.getCashClashPlayer(id) : null;
        double max = attackerCCP != null ? attackerCCP.getMaxHealth() : 20.0;
        if (attacker.getHealth() > max * 0.5) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST)) return;

        if (!isFullyChargedMelee(attacker)) return;

        double damage = 3.0; // 1.5 hearts
        double radius = 7.0;
        double totalDealt = 0.0;

        for (org.bukkit.entity.Entity entity : attacker.getWorld().getNearbyEntities(attacker.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker)) continue;

            if (session != null) {
                Team aTeam = session.getPlayerTeam(attacker);
                Team tTeam = session.getPlayerTeam(target);
                if (tTeam != null && aTeam == tTeam) continue;
            }

            double newHealth = Math.max(0.0, target.getHealth() - damage);
            target.setHealth(newHealth);
            totalDealt += damage;
            ParticleUtils.hitFeedback(target.getLocation(), 10, 0.2);
        }

        if (totalDealt > 0) {
            double newHealth = Math.min(attacker.getHealth() + totalDealt, max);
            attacker.setHealth(newHealth);
        }

        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST, 35);
        Messages.send(attacker, "armor.soul-burst");
        SoundUtils.play(attacker, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
    }

    private boolean isFullyChargedMelee(Player attacker) {
        // Bukkit exposes attack cooldown directly
        try {
            return attacker.getAttackCooldown() >= 0.99f;
        } catch (NoSuchMethodError ignored) {
            return true;
        }
    }

    // ==================== FLAMEBRINGER SET ====================

    /**
     * Flamebringer Furnace Blood: If player is on fire, take no fire tick KB and gain Speed I for 12s.
     */
    public void onFlamebringerFireTick(Player p) {
        if (!hasFlamebringerSet(p)) return;
        if (p.isDead()) return; // Don't apply effects to dead players

        UUID id = p.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (p.getFireTicks() > 0) {
            // Check if speed was already applied and is still active
            Long endTime = flamebringerSpeedEndTime.get(id);
            if (endTime != null && currentTime < endTime) {
                // Speed effect is still active, don't reapply
                return;
            }

            // Apply speed for 12 seconds
            ItemsConfig cfg = ItemsConfig.getInstance();
            p.removePotionEffect(PotionEffectType.SPEED);
            p.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                cfg.getFlamebringerSpeedDuration() * 20,
                cfg.getFlamebringerSpeedLevel(),
                false,
                false,
                true
            ));
            // Track when this speed effect should end
            flamebringerSpeedEndTime.put(id, currentTime + (cfg.getFlamebringerSpeedDuration() * 1000L));
        } else {
            // Player is no longer on fire, clear the speed
            p.removePotionEffect(PotionEffectType.SPEED);
            flamebringerSpeedEndTime.remove(id);
        }
    }

    /**
     * Triggered when lava damages the player: grant Speed I for 6s, max 3 per game, 2s cooldown between procs.
     */
    public void onFlamebringerLavaDamage(Player p) {
        if (!hasFlamebringerSet(p)) return;
        UUID id = p.getUniqueId();

        int used = flamebringerLavaUses.getOrDefault(id, 0);
        if (used >= 3) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN)) {
            return;
        }

        p.removePotionEffect(PotionEffectType.SPEED);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6 * 20, 0, false, false, true));
        flamebringerLavaUses.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN, 2);
        Messages.send(p, "armor.flamebringer-speed", "remaining", String.valueOf(3 - (used + 1)));
    }

    /**
     * Check if player should have fire tick knockback negation.
     */
    public boolean hasFlamebringerNoFireKb(Player p) {
        if (!hasFlamebringerSet(p)) return false;
        return p.getFireTicks() > 0 && ItemsConfig.getInstance().getFlamebringerNoFireKb();
    }

    /**
     * Handle Flamebringer kill tracking and gravitational pull on 2nd kill.
     */
    public void onFlamebringerKill(Player killer) {
        if (!hasFlamebringerSet(killer)) return;

        UUID id = killer.getUniqueId();
        int kills = flamebringerKills.getOrDefault(id, 0) + 1;
        flamebringerKills.put(id, kills);

        ItemsConfig cfg = ItemsConfig.getInstance();

        if (kills >= cfg.getFlamebringerKillsForPull()) {
            flamebringerKills.put(id, 0);

            Messages.send(killer, "armor.flamebringer-pull-activated");
            SoundUtils.play(killer, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

            double radius = cfg.getFlamebringerPullRadius();
            double duration = cfg.getFlamebringerPullDuration();
            double pullStrength = cfg.getFlamebringerPullStrength();

            GameManager gameManager = GameManager.getInstance();
            GameSession session = gameManager.getPlayerSession(killer);
            Team killerTeam = session != null ? session.getPlayerTeam(killer) : null;

            Location killerLoc = killer.getLocation();

            int durationTicks = (int) (duration * 20);
            BukkitTask pullTask = SchedulerUtils.runTaskTimer(() -> {
                if (!killer.isOnline()) return;

                ParticleUtils.flamebringerPull(killerLoc, radius);

                for (org.bukkit.entity.Entity entity : killer.getWorld().getNearbyEntities(killerLoc, radius, radius, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(killer)) continue;

                    if (session != null && killerTeam != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam == killerTeam) continue;
                    }

                    Vector direction = killerLoc.toVector().subtract(target.getLocation().toVector()).normalize();
                    target.setVelocity(direction.multiply(pullStrength));
                }
            }, 0L, 2L);

            SchedulerUtils.runTaskLater(() -> {
                if (pullTask != null) {
                    pullTask.cancel();
                }
                if (killer.isOnline()) {
                    Messages.send(killer, "armor.flamebringer-pull-ended");
                }
            }, durationTicks);
        }
    }

    // ==================== TAX EVASION PANTS ====================

    public void onTaxEvasionTick(Player p, GameSession session) {
        if (!hasTaxEvasion(p)) return;
        if (p.isDead()) return; // Don't award coins to dead players
        if (session == null) return;
        
        // Only trigger during combat phase
        if (session.getState() != GameState.COMBAT) return;
        
        UUID id = p.getUniqueId();

        long lastCheck = cooldownManager.getTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
        if (lastCheck == 0) {
            cooldownManager.setTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
            return;
        }

        // Living for 1 minute grants 3k
        if (cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.TAX_EVASION_MINUTE, 60)) {
            CashClashPlayer ccp = session.getCashClashPlayer(id);
            if (ccp != null) {
                ccp.addCoins(3000);
                Messages.send(p, "armor.tax-evasion-reward");
                SoundUtils.play(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            cooldownManager.setTimestamp(id, CooldownManager.Keys.TAX_EVASION_MINUTE);
        }
    }

    public double getTaxEvasionDeathPenalty() {
        return 0.075; // Only lose 7.5% on death
    }

    // ==================== INVESTOR'S SET ====================

    public double getInvestorMultiplier(Player p) {
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.125 * pieces); // +12.5% per piece
    }

    public double getInvestorMeleeDamageMultiplier(Player p, int currentRound) {
        if (currentRound < 4) return 1.0;
        int pieces = countInvestorsPieces(p);
        if (pieces <= 0) return 1.0;
        return 1.0 + (0.05 * pieces); // +5% per piece in rounds 4/5
    }

    /**
     * Calculate the price for an investor piece based on how many are already owned.
     */
    public long getInvestorPrice(CustomArmorItem armor, int piecesOwned) {
        if (!armor.isInvestorsSet()) return armor.getBasePrice();
        // Each piece increases price by 25%
        double multiplier = Math.pow(1.25, piecesOwned);
        return Math.round(armor.getBasePrice() * multiplier);
    }

    // ==================== RESET ====================

    public void cleanup() {
        magicHelmetEffectIndex.clear();
        magicHelmetActivated.clear();

        bunnyToggleReady.clear();

        guardianUsesThisRound.clear();

        deathmaulerExtraHearts.clear();

        // Cancel all dragon mark tasks
        dragonMarkTasks.values().forEach(BukkitTask::cancel);
        dragonMarkTasks.clear();
        dragonMarkedTargets.clear();
        dragonDamageBoost.clear();

        // Cancel all flamebringer tasks
        flamebringerFireTask.values().forEach(BukkitTask::cancel);
        flamebringerFireTask.clear();
        flamebringerKills.clear();
        flamebringerLavaUses.clear();
        flamebringerSpeedEndTime.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }
    
    /**
     * Reset per-round tracking for all players (called at round start).
     */
    public void resetRoundTracking() {
        magicHelmetActivated.clear();
        guardianUsesThisRound.clear();
        deathmaulerExtraHearts.clear();

        // Cancel all dragon mark tasks and clear dragon tracking
        dragonMarkTasks.values().forEach(BukkitTask::cancel);
        dragonMarkTasks.clear();
        dragonMarkedTargets.clear();
        dragonDamageBoost.clear();

        // Reset flamebringer kill counters
        flamebringerKills.clear();
        flamebringerSpeedEndTime.clear();
    }

    /**
     * Check if player is silenced (carrying enemy flag in CTF)
     */
    private boolean isSilenced(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }
}

