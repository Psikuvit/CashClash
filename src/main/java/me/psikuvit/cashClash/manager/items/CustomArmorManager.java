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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

    private final ItemsConfig cfg;

    private final Map<UUID, Boolean> bunnyToggleReady;

    // Guardian's Vest tracking
    private final Map<UUID, Integer> guardianUsesThisRound;

    // Deathmauler tracking
    private final Map<UUID, Integer> deathmaulerExtraHearts;

    // Dragon Set tracking
    private final Map<UUID, Integer> dragonScales; // Player -> charged scales (max 3)
    private final Map<UUID, Integer> dragonHitCount; // Player -> fully-charged melee hits toward next scale
    private final Map<UUID, Long> dragonRushDamageBuff; // Player -> expiry of +25% rush damage buff
    private final Set<UUID> dragonRushInvincible; // Players invincible during teammate Dragon Rush

    // Bullseye Pants tracking
    private final Map<UUID, Integer> bullseyeHitCount; // Attacker -> current hit count

    // Tectonic Cap tracking
    private final Map<UUID, Long> tectonicCharge1Cooldown;
    private final Map<UUID, Long> tectonicCharge2Cooldown;

    // Flamebringer Set tracking
    private final Map<UUID, Integer> flamebringerKills; // Player -> kill count this round
    private final Map<UUID, BukkitTask> flamebringerFireTask; // Player -> fire effect task
    private final Map<UUID, Integer> flamebringerLavaUses; // Player -> lava speed procs this game
    private final Map<UUID, Long> flamebringerSpeedEndTime; // Player -> time when speed effect should end
    private final Map<UUID, BukkitTask> flamebringerTrailTasks; // Player -> fire trail task
    private final Map<UUID, Long> flamebringerTrailEndTime; // Player -> time when fire trail should end
    private final Map<UUID, List<Location>> flamebringerTrailLocations; // Player -> recent positions for trail

    // Mythic shift lock: players blocked from activating bunny shoes while a mythic shift ability is active
    private final Set<UUID> mythicShiftLock;

    private final Random random;

    private CustomArmorManager() {
        this.cooldownManager = CooldownManager.getInstance();
        this.cfg = ItemsConfig.getInstance();

        this.bunnyToggleReady = new ConcurrentHashMap<>();

        this.guardianUsesThisRound = new ConcurrentHashMap<>();

        this.deathmaulerExtraHearts = new ConcurrentHashMap<>();

        this.dragonScales = new ConcurrentHashMap<>();
        this.dragonHitCount = new ConcurrentHashMap<>();
        this.dragonRushDamageBuff = new ConcurrentHashMap<>();
        this.dragonRushInvincible = ConcurrentHashMap.newKeySet();

        this.bullseyeHitCount = new ConcurrentHashMap<>();

        this.tectonicCharge1Cooldown = new ConcurrentHashMap<>();
        this.tectonicCharge2Cooldown = new ConcurrentHashMap<>();

        this.flamebringerKills = new ConcurrentHashMap<>();
        this.flamebringerFireTask = new ConcurrentHashMap<>();
        this.flamebringerLavaUses = new ConcurrentHashMap<>();
        this.flamebringerSpeedEndTime = new ConcurrentHashMap<>();
        this.flamebringerTrailTasks = new ConcurrentHashMap<>();
        this.flamebringerTrailEndTime = new ConcurrentHashMap<>();
        this.flamebringerTrailLocations = new ConcurrentHashMap<>();

        this.mythicShiftLock = ConcurrentHashMap.newKeySet();

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

    public boolean hasBullseyePants(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.BULLSEYE_PANTS) return true;
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

    public boolean hasTectonicCap(Player p) {
        for (CustomArmorItem ca : getEquippedCustomArmor(p)) {
            if (ca == CustomArmorItem.TECTONIC_CAP) return true;
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

    // ==================== TECTONIC CAP ====================

    /**
     * Tectonic Cap: negates fall damage and slams the ground, damaging nearby enemies.
     * Two charges with a shared recharge; each charge recharges independently after the
     * configured recharge time. The slam radius scales with the negated fall damage.
     */
    public void onTectonicCapFall(EntityDamageEvent event, Player player) {
        if (!hasTectonicCap(player)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !tectonicCharge1Cooldown.containsKey(id) || tectonicCharge1Cooldown.get(id) <= now;
        boolean charge2Ready = !tectonicCharge2Cooldown.containsKey(id) || tectonicCharge2Cooldown.get(id) <= now;

        if (!charge1Ready && !charge2Ready) return;

        Location impact = player.getLocation().clone().add(0, 0.1, 0);
        Material feet = player.getLocation().getBlock().getType();

        if (feet == Material.WATER || feet == Material.LAVA || feet == Material.COBWEB) {
            return;
        }

        double fallDamage = event.getFinalDamage();
        event.setCancelled(true);
        double radius = cfg.getTectonicCapRadius() + (fallDamage * 0.3);
        World world = player.getWorld();

        ParticleUtils.spawnDust(impact, Color.fromRGB(180, 120, 60), 2.5f, 75, 1.1, 0.1, 1.1);
        ParticleUtils.spawnDust(impact, Color.fromRGB(80, 45, 20), 2.5f, 60, 1.1, 0.1, 1.1);

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            target.damage(cfg.getTectonicCapDamage() + fallDamage, player);

            Vector knockback = target.getLocation()
                    .toVector()
                    .subtract(impact.toVector())
                    .normalize()
                    .setY(0.25)
                    .multiply(0.7);

            target.setVelocity(knockback);
            if (target.getLocation().distance(impact) <= 2.0) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        20 * 4, // 4 seconds
                        1,      // Slowness II
                        false,
                        true,
                        true
                ));
            }
        }

        long cooldownEnd = System.currentTimeMillis() + (cfg.getTectonicCapRechargeSeconds() * 1000L);
        if (charge1Ready) {
            tectonicCharge1Cooldown.put(id, cooldownEnd);
        } else {
            tectonicCharge2Cooldown.put(id, cooldownEnd);
        }
    }

    // ==================== DRAGON SET ====================

    /**
     * Dragon Set: fully-charged melee hits charge Dragon Scales (up to the configured max).
     * Sneaking while looking at a target consumes one scale to Dragon Rush:
     * - teammate: both players become briefly invincible
     * - enemy: next melee hit deals bonus damage
     * Killing with the set grants Strength for a few seconds.
     */
    public int getDragonScales(Player player) {
        return dragonScales.getOrDefault(player.getUniqueId(), 0);
    }

    private void setDragonScales(Player player, int amount) {
        dragonScales.put(player.getUniqueId(), Math.min(cfg.getDragonMaxScales(), amount));
    }

    public boolean consumeDragonScale(Player player) {
        if (getDragonScales(player) <= 0) {
            return false;
        }
        setDragonScales(player, getDragonScales(player) - 1);
        return true;
    }

    /**
     * Charge a Dragon Scale on fully-charged melee hits.
     */
    public void handleDragonHit(Player player) {
        if (!hasDragonSet(player)) return;
        if (!isFullyChargedMelee(player)) return;
        if (getDragonScales(player) >= cfg.getDragonMaxScales()) return;

        int hits = dragonHitCount.getOrDefault(player.getUniqueId(), 0) + 1;
        if (hits >= cfg.getDragonHitsForScale()) {
            setDragonScales(player, getDragonScales(player) + 1);
            dragonHitCount.put(player.getUniqueId(), 0);
            SoundUtils.play(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 1.6f);
            Messages.send(player, "armor.dragon-scale-charged", "scales", String.valueOf(getDragonScales(player)));
        } else {
            dragonHitCount.put(player.getUniqueId(), hits);
        }
    }

    /**
     * Dragon Rush: consume a scale to dash to a target in line of sight.
     */
    public void onDragonRush(Player player) {
        if (!hasDragonSet(player)) return;

        Entity target = player.getTargetEntity(cfg.getDragonRushRange());
        if (!(target instanceof Player targetPlayer)) return;
        if (!player.hasLineOfSight(targetPlayer)) return;

        if (!consumeDragonScale(player)) {
            Messages.send(player, "armor.dragon-need-scale");
            return;
        }

        Messages.send(player, "armor.dragon-scale-used", "scales", String.valueOf(getDragonScales(player)));

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team playerTeam = session.getPlayerTeam(player);
        Team targetTeam = session.getPlayerTeam(targetPlayer);
        if (playerTeam == null || targetTeam == null) return;

        Location destination = targetPlayer.getLocation();
        Vector direction = destination.toVector().subtract(player.getLocation().toVector()).normalize();

        player.setFlying(false);
        player.setAllowFlight(false);

        if (playerTeam.getTeamNumber() == targetTeam.getTeamNumber()) {
            // Teammate rush: land just short of the target, both invincible briefly
            Location startLocation = player.getLocation().clone();
            destination.subtract(direction.multiply(1.5));
            ParticleUtils.dragonRushCircle(startLocation, Color.fromRGB(200, 150, 255), 1.2f);
            player.teleport(destination);
            ParticleUtils.dragonRushCircle(destination, Color.fromRGB(200, 150, 255), 1.2f);
            SoundUtils.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 2.5f, 1.5f);

            dragonRushInvincible.add(player.getUniqueId());
            dragonRushInvincible.add(targetPlayer.getUniqueId());
            SchedulerUtils.runTaskLater(() -> {
                dragonRushInvincible.remove(player.getUniqueId());
                dragonRushInvincible.remove(targetPlayer.getUniqueId());
                Messages.send(player, "armor.dragon-rush-invincibility-ended");
                Messages.send(targetPlayer, "armor.dragon-rush-invincibility-ended");
            }, 10L);
        } else {
            // Enemy rush: teleport onto the target and empower the next melee hit
            ParticleUtils.dragonRushCircle(player.getLocation(), Color.fromRGB(140, 0, 255), 1.5f);
            player.teleport(destination);
            ParticleUtils.dragonRushCircle(destination, Color.fromRGB(140, 0, 255), 1.5f);
            SoundUtils.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.1f);

            dragonRushDamageBuff.put(player.getUniqueId(), System.currentTimeMillis() + (cfg.getDragonRushBuffSeconds() * 1000L));
            Messages.send(player, "armor.dragon-rush-empowered");
        }
    }

    /**
     * Apply the empowered Dragon Rush strike if the damage buff is active.
     */
    public void onDragonRushHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (!dragonRushDamageBuff.containsKey(uuid)) return;

        long expireTime = dragonRushDamageBuff.get(uuid);
        if (System.currentTimeMillis() > expireTime) {
            dragonRushDamageBuff.remove(uuid);
            return;
        }

        event.setDamage(event.getDamage() * (1.0 + cfg.getDragonRushDamagePercent()));
        Messages.send(player, "armor.dragon-rush-empowered-strike");
        dragonRushDamageBuff.remove(uuid);
    }

    /**
     * Check if a player is invincible from a teammate Dragon Rush.
     */
    public boolean isDragonRushInvincible(UUID uuid) {
        return dragonRushInvincible.contains(uuid);
    }

    /**
     * Dragon kill effect: Strength + a swirling Dragon Fury veil.
     */
    public void onPlayerKillDragon(Player killer) {
        if (!hasDragonSet(killer)) return;

        killer.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                cfg.getDragonKillStrengthDuration() * 20,
                cfg.getDragonKillStrengthLevel()
        ));
        killer.playSound(killer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);

        Color purple = Color.fromRGB(160, 40, 255);
        for (int tick = 0; tick < 10; tick++) {
            final int currentTick = tick;
            SchedulerUtils.runTaskLater(() -> {
                if (!killer.isOnline()) return;
                double progress = currentTick / 9.0;
                double y = progress * 1.8;
                for (int i = 0; i < 12; i++) {
                    double angle = (Math.PI * 2 * i / 12.0) + (progress * Math.PI * 6);
                    double x = Math.cos(angle) * 0.45;
                    double z = Math.sin(angle) * 0.45;
                    Location particleLoc = killer.getLocation().clone().add(x, y, z);
                    ParticleUtils.dragonFuryVeil(particleLoc, purple);
                }
            }, tick);
        }

        Messages.send(killer, "armor.dragon-kill-buff");
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

        // Blocked while a mythic shift ability is active
        if (mythicShiftLock.contains(id)) return;

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
            // Player is no longer on fire, check if speed should be cleared
            Long endTime = flamebringerSpeedEndTime.get(id);
            if (endTime != null && currentTime >= endTime) {
                p.removePotionEffect(PotionEffectType.SPEED);
                flamebringerSpeedEndTime.remove(id);
            }
        }
    }

    /**
     * Triggered when lava damages the player: grant Speed for 12s, max 3 per game, 2s cooldown between procs.
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
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, cfg.getFlamebringerSpeedDuration() * 20, cfg.getFlamebringerSpeedLevel(), false, false, true));
        SoundUtils.play(p, Sound.ITEM_FIRECHARGE_USE, 1.5f, 1.0f);
        flamebringerTrailEndTime.put(id, System.currentTimeMillis() + (cfg.getFlamebringerSpeedDuration() * 1000L));
        startFlamebringerTrail(p);
        flamebringerLavaUses.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN, 2);
        Messages.send(p, "armor.flamebringer-speed", "remaining", String.valueOf(3 - (used + 1)));
    }

    /**
     * Start the Flamebringer fire trail (dust particles + ignites enemies in the path).
     */
    private void startFlamebringerTrail(Player p) {
        UUID id = p.getUniqueId();
        if (flamebringerTrailTasks.containsKey(id)) return;

        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!p.isOnline() || p.isDead()) {
                stopFlamebringerTrail(p);
                return;
            }

            Long endTime = flamebringerTrailEndTime.get(id);
            if (endTime == null || System.currentTimeMillis() >= endTime || !hasFlamebringerSet(p)) {
                stopFlamebringerTrail(p);
                return;
            }

            List<Location> trail = flamebringerTrailLocations.computeIfAbsent(id, k -> new ArrayList<>());
            Location loc = p.getLocation().clone();
            trail.add(loc);
            if (trail.size() > 6) {
                trail.removeFirst();
            }

            for (Location trailLoc : trail) {
                ParticleUtils.spawnDust(trailLoc.clone().add(0, 0.2, 0), Color.RED, 1.0f, 3, 0.15, 0.05, 0.15);
            }

            for (Entity entity : p.getNearbyEntities(1.2, 1.0, 1.2)) {
                if (!(entity instanceof Player target)) continue;
                if (target.equals(p)) continue;
                target.setFireTicks(60);
            }
        }, 0L, 2L);

        flamebringerTrailTasks.put(id, task);
    }

    private void stopFlamebringerTrail(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask task = flamebringerTrailTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        flamebringerTrailLocations.remove(id);
        flamebringerTrailEndTime.remove(id);
    }

    /**
     * Check if player should have fire tick knockback negation.
     */
    public boolean hasFlamebringerNoFireKb(Player p) {
        if (!hasFlamebringerSet(p)) return false;
        return p.getFireTicks() > 0 && cfg.getFlamebringerNoFireKb();
    }

    /**
     * Handle Flamebringer kill tracking and gravitational pull on 2nd kill.
     */
    public void onFlamebringerKill(Player killer) {
        if (!hasFlamebringerSet(killer)) return;

        UUID id = killer.getUniqueId();
        int kills = flamebringerKills.getOrDefault(id, 0) + 1;
        flamebringerKills.put(id, kills);

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

    // ==================== BULLSEYE PANTS ====================

    /**
     * Increments the hit count for Bullseye Pants.
     * @return true if it was the 4th hit (triggering the effect)
     */
    public boolean incrementBullseyeHit(Player p) {
        UUID id = p.getUniqueId();
        int hits = bullseyeHitCount.getOrDefault(id, 0) + 1;
        if (hits >= 4) {
            bullseyeHitCount.put(id, 0);
            return true;
        }
        bullseyeHitCount.put(id, hits);
        return false;
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

    /**
     * Investor's Set: reward the killer's team coins on every kill.
     */
    public void onInvestorKill(Player killer, GameSession session) {
        if (killer == null || session == null) return;
        int pieces = countInvestorsPieces(killer);
        if (pieces <= 0) return;

        Team team = session.getPlayerTeam(killer);
        if (team == null) return;

        int reward = 200 * pieces;
        for (UUID uuid : team.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                ccp.addCoins(reward);
            }
            Player teammate = org.bukkit.Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                playInvestorRewardEffect(teammate, reward);
            }
        }
    }

    /**
     * Investor's Set: reward the capturing player's team coins on a CTF flag capture.
     */
    public void onInvestorObjectivectf(Player player) {
        if (player == null) return;
        int pieces = countInvestorsPieces(player);
        if (pieces <= 0) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        Team team = session.getPlayerTeam(player);
        if (team == null) return;

        int reward = 200 * pieces;
        for (UUID uuid : team.getPlayers()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                ccp.addCoins(reward);
            }
            Player teammate = org.bukkit.Bukkit.getPlayer(uuid);
            if (teammate != null && teammate.isOnline()) {
                playInvestorRewardEffect(teammate, reward);
            }
        }
    }

    /**
     * Play the coin reward particle/sound effect for an investor reward recipient.
     */
    private void playInvestorRewardEffect(Player player, int reward) {
        SoundUtils.play(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 1.5f);
        Messages.send(player, "armor.investor-reward", "reward", String.valueOf(reward));

        Color lightGreen = Color.fromRGB(120, 255, 120);
        Location center = player.getLocation().clone().add(0, 0.6, 0);
        for (int i = 0; i < 18; i++) {
            int delay = i;
            SchedulerUtils.runTaskLater(() -> {
                double angle = (Math.PI * 2 / 18) * delay;
                double x = Math.cos(angle) * 0.55;
                double z = Math.sin(angle) * 0.55;
                ParticleUtils.spawnDust(center.clone().add(x, 0, z), lightGreen, 1.3f, 2);
            }, (int) (delay * 0.8));
        }
    }

    /**
     * Temporarily block mythic shift activations for a player (used while a shift ability runs).
     */
    public void lockMythicShift(Player player) {
        UUID id = player.getUniqueId();
        mythicShiftLock.add(id);
        SchedulerUtils.runTaskLater(() -> mythicShiftLock.remove(id), 10L);
    }

    /**
     * Expose the bunny shoes toggle-ready state so it can be cleared on death.
     */
    public Map<UUID, Boolean> getBunnyToggleReady() {
        return bunnyToggleReady;
    }

    // ==================== RESET ====================

    public void cleanup() {
        bunnyToggleReady.clear();

        guardianUsesThisRound.clear();

        deathmaulerExtraHearts.clear();

        bullseyeHitCount.clear();

        tectonicCharge1Cooldown.clear();
        tectonicCharge2Cooldown.clear();

        dragonScales.clear();
        dragonHitCount.clear();
        dragonRushDamageBuff.clear();
        dragonRushInvincible.clear();

        // Cancel all flamebringer tasks
        flamebringerFireTask.values().forEach(BukkitTask::cancel);
        flamebringerFireTask.clear();
        flamebringerKills.clear();
        flamebringerLavaUses.clear();
        flamebringerSpeedEndTime.clear();
        flamebringerTrailTasks.values().forEach(BukkitTask::cancel);
        flamebringerTrailTasks.clear();
        flamebringerTrailEndTime.clear();
        flamebringerTrailLocations.clear();

        mythicShiftLock.clear();

        // Note: cooldowns are managed by CooldownManager and will be cleared when players are cleared
    }
    
    /**
     * Reset per-round tracking for all players (called at round start).
     */
    public void resetRoundTracking() {
        guardianUsesThisRound.clear();
        deathmaulerExtraHearts.clear();
        bullseyeHitCount.clear();

        dragonScales.clear();
        dragonHitCount.clear();
        dragonRushDamageBuff.clear();
        dragonRushInvincible.clear();

        // Reset flamebringer kill counters
        flamebringerKills.clear();
        flamebringerSpeedEndTime.clear();
        flamebringerTrailTasks.values().forEach(BukkitTask::cancel);
        flamebringerTrailTasks.clear();
        flamebringerTrailEndTime.clear();
        flamebringerTrailLocations.clear();
    }

    /**
     * Check if player is silenced (carrying enemy flag in CTF or dead)
     */
    private boolean isSilenced(Player player) {
        // Dead players are silenced for all items
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            me.psikuvit.cashClash.game.round.RoundData roundData = session.getCurrentRoundData();
            if (roundData != null && !roundData.isAlive(player.getUniqueId())) {
                return true;
            }
        }

        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }
}

