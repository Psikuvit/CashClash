package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.RuneManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
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

    // Dragon Set tracking
    private final Map<UUID, Integer> dragonScales; // Player -> charged scales (max 3)
    private final Map<UUID, Integer> dragonHitCount; // Player -> fully-charged melee hits toward next scale
    private final Map<UUID, Long> dragonRushDamageBuff; // Player -> expiry of +25% rush damage buff
    private final Set<UUID> dragonRushInvincible; // Players invincible during teammate Dragon Rush
    private final Set<UUID> dragonRushIndicators; // Players with an active Dragon Rush reminder
    private final Set<UUID> dragonOutrageIndicators; // Players with an active Dragon Outrage reminder
    private final Set<UUID> dragonOutrageActive; // Players mid Dragon Outrage flight
    private final Map<UUID, Long> dragonOutrageStartTime; // Player -> outrage activation time
    private final Map<UUID, BukkitTask> dragonOutrageTasks; // Player -> outrage flight trail task

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

        this.dragonScales = new ConcurrentHashMap<>();
        this.dragonHitCount = new ConcurrentHashMap<>();
        this.dragonRushDamageBuff = new ConcurrentHashMap<>();
        this.dragonRushInvincible = ConcurrentHashMap.newKeySet();
        this.dragonRushIndicators = ConcurrentHashMap.newKeySet();
        this.dragonOutrageIndicators = ConcurrentHashMap.newKeySet();
        this.dragonOutrageActive = ConcurrentHashMap.newKeySet();
        this.dragonOutrageStartTime = new ConcurrentHashMap<>();
        this.dragonOutrageTasks = new ConcurrentHashMap<>();

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

        SoundUtils.playAt(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

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
                CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, 20 * 4, 1, false, true, true);
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

    public int getMaxDragonScales() {
        return cfg.getDragonMaxScales();
    }

    private void setDragonScales(Player player, int amount) {
        dragonScales.put(player.getUniqueId(), Math.min(cfg.getDragonMaxScales(), amount));
        if (amount >= cfg.getDragonMaxScales()) {
            startDragonOutrageIndicator(player);
        }
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

        startDragonRushIndicator(player);
    }

    /**
     * Dragon Rush: consume a scale to dash to a target in line of sight.
     */
    public void onDragonRush(Player player) {
        if (!hasDragonSet(player)) return;

        UUID id = player.getUniqueId();

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DRAGON_DASH)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(id, CooldownManager.Keys.DRAGON_DASH);
            Messages.send(player, "armor.dragon-rush-cooldown", "remaining", String.valueOf(remaining));
            return;
        }

        Entity target = player.getTargetEntity(cfg.getDragonRushRange());
        if (!(target instanceof Player targetPlayer)) {
            failDragonRush(player);
            return;
        }
        if (!player.hasLineOfSight(targetPlayer)) {
            failDragonRush(player);
            return;
        }

        if (!consumeDragonScale(player)) {
            Messages.send(player, "armor.dragon-need-scale");
            return;
        }

        dragonRushIndicators.remove(id);

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
     * Failed Dragon Rush: short cooldown so the player can re-aim quickly.
     */
    private void failDragonRush(Player player) {
        UUID id = player.getUniqueId();
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DRAGON_DASH, 5);
        Messages.send(player, "armor.dragon-rush-no-target");
        SoundUtils.play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.4f);
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

    // ==================== DRAGON RUSH / OUTRAGE INDICATORS ====================

    /**
     * Show periodic reminders (purple target ring) while the player has scales and is
     * aiming at a valid rush target.
     */
    public void startDragonRushIndicator(Player player) {
        if (cooldownManager.isOnCooldown(player.getUniqueId(), CooldownManager.Keys.DRAGON_DASH)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (dragonRushIndicators.contains(uuid)) {
            return;
        }
        dragonRushIndicators.add(uuid);

        for (int i = 0; i < 3; i++) {
            long delay = i * 100L;
            SchedulerUtils.runTaskLater(() -> {
                if (!player.isOnline()
                        || !hasDragonSet(player)
                        || getDragonScales(player) <= 0
                        || getDragonScales(player) >= cfg.getDragonMaxScales()) {
                    dragonRushIndicators.remove(uuid);
                    return;
                }
                Entity target = player.getTargetEntity(cfg.getDragonRushRange());
                if (!(target instanceof Player targetPlayer)) {
                    return;
                }
                if (!player.hasLineOfSight(targetPlayer)) {
                    return;
                }
                showDragonRushIndicator(player, targetPlayer);
            }, delay);
        }

        SchedulerUtils.runTaskLater(() -> {
            dragonRushIndicators.remove(uuid);
        }, 300L);
    }

    /**
     * Draw a purple circle under the rush target and play a heartbeat sound.
     */
    public void showDragonRushIndicator(Player player, Player target) {
        Location center = target.getLocation().clone().add(0, 0.1, 0);
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            double x = Math.cos(angle) * 1.2;
            double z = Math.sin(angle) * 1.2;
            ParticleUtils.spawnDust(center.clone().add(x, 0.9, z), Color.fromRGB(140, 0, 255), 0.8f, 1);
        }
        SoundUtils.play(player, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.7f);
    }

    /**
     * Show a dark-purple ring around the player when all scales are charged and
     * Dragon Outrage is available.
     */
    public void startDragonOutrageIndicator(Player player) {
        UUID uuid = player.getUniqueId();
        if (dragonOutrageIndicators.contains(uuid)) {
            return;
        }
        dragonOutrageIndicators.add(uuid);

        for (int i = 0; i < 3; i++) {
            long delay = i * 100L;
            SchedulerUtils.runTaskLater(() -> {
                if (!player.isOnline()
                        || !hasDragonSet(player)
                        || getDragonScales(player) < cfg.getDragonMaxScales()) {
                    dragonOutrageIndicators.remove(uuid);
                    return;
                }
                Location center = player.getLocation().clone().add(0, 0.9, 0);
                for (int j = 0; j < 16; j++) {
                    double angle = 2 * Math.PI * j / 16;
                    double x = Math.cos(angle) * 1.0;
                    double z = Math.sin(angle) * 1.0;
                    ParticleUtils.spawnDust(center.clone().add(x, 0, z), Color.fromRGB(80, 0, 120), 1.2f, 1);
                }
                SoundUtils.play(player, Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.6f);
            }, delay);
        }

        SchedulerUtils.runTaskLater(() -> dragonOutrageIndicators.remove(uuid), 300L);
    }

    /**
     * Dragon Outrage: with all scales charged, sneak to launch into the air toward
     * the aimed location, then slam down in a massive explosion.
     */
    public void startDragonOutrage(Player player) {
        if (!hasDragonSet(player)) return;
        if (getDragonScales(player) < cfg.getDragonMaxScales()) return;

        UUID uuid = player.getUniqueId();
        if (dragonOutrageActive.contains(uuid)) return;

        setDragonScales(player, 0);
        dragonOutrageIndicators.remove(uuid);

        dragonOutrageActive.add(uuid);
        dragonOutrageStartTime.put(uuid, System.currentTimeMillis());

        Messages.send(player, "armor.dragon-outrage-activated");
        SoundUtils.play(player, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 1.0f);

        Location landing = getDragonOutrageTarget(player);
        if (landing == null) {
            landing = player.getLocation().clone();
        }

        Vector direction = player.getLocation().getDirection().normalize();
        Vector launchVelocity = new Vector(direction.getX() * 0.35, 0.9, direction.getZ() * 0.35);
        player.setVelocity(launchVelocity);

        startDragonOutrageFlight(player, landing);
    }

    /**
     * Resolve the aimed landing spot for Dragon Outrage (solid top face up to 40 blocks).
     */
    public Location getDragonOutrageTarget(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 40);

        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        Block block = result.getHitBlock();

        if (!block.getType().isSolid()) {
            return null;
        }

        if (result.getHitBlockFace() != BlockFace.UP) {
            return null;
        }

        return block.getLocation().add(0.5, 1.05, 0.5);
    }

    /**
     * Fly the player toward the landing spot, spawning a spiral trail, then slam down.
     */
    private void startDragonOutrageFlight(Player player, Location landing) {
        UUID uuid = player.getUniqueId();
        if (dragonOutrageTasks.containsKey(uuid)) return;

        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!player.isOnline() || !dragonOutrageActive.contains(uuid)) {
                endDragonOutrageFlight(player);
                return;
            }

            long elapsed = System.currentTimeMillis() - dragonOutrageStartTime.getOrDefault(uuid, 0L);

            ParticleUtils.dragonOutrageTrail(player.getLocation().clone().add(0, 1, 0));

            double distance = player.getLocation().distance(landing);
            boolean reached = distance <= 1.5;
            boolean timedOut = elapsed >= 3000;

            if (reached || timedOut) {
                endDragonOutrageFlight(player);
                dragonOutrageExplosion(player, landing);
                return;
            }

            // Steer toward the aimed landing spot
            Vector toTarget = landing.toVector().subtract(player.getLocation().toVector()).normalize();
            Vector steer = toTarget.multiply(0.8);
            if (steer.getY() > 0.6) steer.setY(0.6);
            if (steer.getY() < -0.4) steer.setY(-0.4);
            player.setVelocity(steer);
        }, 2L, 1L);

        dragonOutrageTasks.put(uuid, task);
    }

    private void endDragonOutrageFlight(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = dragonOutrageTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        dragonOutrageActive.remove(uuid);
        dragonOutrageStartTime.remove(uuid);
    }

    /**
     * Massive landing explosion for Dragon Outrage: expanding sphere, ground shockwave,
     * and damage to enemies within a 5x3x5 box.
     */
    public void dragonOutrageExplosion(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        SoundUtils.playAt(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
        SoundUtils.playAt(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.8f);
        SoundUtils.playAt(location, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.5f, 0.8f);

        Color purple = Color.fromRGB(140, 60, 220);
        Color black = Color.fromRGB(20, 0, 30);

        BukkitTask explosionTask = SchedulerUtils.runTaskTimer(() -> {
            // Expanding purple/black sphere
            for (int i = 0; i < 40; i++) {
                double x = (Math.random() - 0.5) * 8;
                double y = Math.random() * 5;
                double z = (Math.random() - 0.5) * 8;
                Location particle = location.clone().add(x, y, z);
                ParticleUtils.spawnDust(particle, purple, 2.0f, 1, 0.05);
                ParticleUtils.spawnDust(particle, black, 2.5f, 1);
            }

            // Ground shockwave ring
            for (int i = 0; i < 40; i++) {
                double angle = Math.PI * 2 * i / 40;
                double x = Math.cos(angle) * 3.5;
                double z = Math.sin(angle) * 3.5;
                ParticleUtils.spawnDust(location.clone().add(x, 0.1, z), purple, 2.0f, 1);
            }

            // Vertical energy column
            for (int i = 0; i < 15; i++) {
                double y = Math.random() * 4;
                ParticleUtils.spawnDust(location.clone().add(0, y, 0), black, 2.5f, 1);
            }
        }, 0L, 1L);

        SchedulerUtils.runTaskLater(explosionTask::cancel, 10L);

        for (Entity entity : world.getNearbyEntities(location, 5, 3, 5)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            target.damage(18, player);

            Vector knockback = target.getLocation()
                    .toVector()
                    .subtract(location.toVector())
                    .normalize()
                    .multiply(1.5);
            knockback.setY(0.6);
            target.setVelocity(knockback);
        }
    }

    /**
     * Dragon kill effect: Strength + a swirling Dragon Fury veil.
     */
    public void onPlayerKillDragon(Player killer) {
        if (!hasDragonSet(killer)) return;

        CashClashPlayer.applyEffect(killer, PotionEffectType.STRENGTH, cfg.getDragonKillStrengthDuration() * 20, cfg.getDragonKillStrengthLevel());
        SoundUtils.play(killer, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);

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
        if (!p.isOnline()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isDead()) return;
        if (p.getHealth() <= 0) return;
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
        if (!p.isOnline()) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isDead()) return;
        if (p.getHealth() <= 0) return;
        UUID id = p.getUniqueId();

        // Blocked while a mythic shift ability is active
        if (mythicShiftLock.contains(id)) return;

        // Blocked while a rune shift ability is active
        if (RuneManager.isRuneShiftLocked(p)) return;

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
        CashClashPlayer.applyEffect(p, PotionEffectType.SPEED, duration * 20, 1);
        CashClashPlayer.applyEffect(p, PotionEffectType.JUMP_BOOST, duration * 20, 0);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.BUNNY_SHOES, cfg.getBunnyShoesCooldown());

        Messages.send(p, "armor.bunny-shoes-activated", "duration", String.valueOf(duration));
        ParticleUtils.bunnyDiamond(p.getLocation().clone().add(0, 0.08, 0));
        SoundUtils.play(p, Sound.ENTITY_BREEZE_IDLE_AIR, 1.0f, 1.0f);
    }

    // ==================== GUARDIAN'S VEST ====================

    public void onPlayerDamaged(Player p, double healthAfter) {
        if (!hasGuardianVest(p)) return;
        UUID id = p.getUniqueId();

        if (healthAfter > 8.0) return; // 4 hearts = 8 HP

        int used = guardianUsesThisRound.getOrDefault(id, 0);
        if (used >= 3) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.GUARDIAN_VEST)) return;

        CashClashPlayer.applyEffect(p, PotionEffectType.RESISTANCE, 15 * 20, 1);
        guardianUsesThisRound.put(id, used + 1);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.GUARDIAN_VEST, 20);

        Messages.send(p, "armor.guardian-vest-activated", "uses", String.valueOf(used + 1));
        ParticleUtils.guardianRings(p.getLocation());
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

        // Dark hearts + smoke pulses rising from the kill
        for (int i = 0; i < 3; i++) {
            SchedulerUtils.runTaskLater(() -> {
                if (!killer.isOnline()) return;
                ParticleUtils.deathmaulerHeal(killer.getLocation());
            }, i * 8L);
            SoundUtils.play(killer, Sound.ENTITY_WITHER_HURT, 0.6f, 0.6f);
        }
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
            CashClashPlayer.applyEffect(p, PotionEffectType.ABSORPTION, 60 * 20, 0);
            Messages.send(p, "armor.deathmauler-absorption");
        }, delaySeconds * 20L);
    }

    // ==================== DEATHMAULER SOUL BURST ====================

    public void tryDeathmaulerSoulBurst(Player attacker, Player victim, GameSession session) {
        if (!hasDeathmaulerSet(attacker) || victim == null) return;
        UUID id = attacker.getUniqueId();

        // Use centralized health system for correct max health
        CashClashPlayer attackerCCP = session != null ? session.getCashClashPlayer(id) : null;
        double max = attackerCCP != null ? attackerCCP.getMaxHealth() : 20.0;
        if (attacker.getHealth() > max * 0.5) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST)) return;

        if (!isFullyChargedMelee(attacker)) return;

        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST, 35);
        Messages.send(attacker, "armor.soul-burst");
        SoundUtils.play(attacker, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);

        Location center = attacker.getLocation();
        Set<UUID> hitPlayers = ConcurrentHashMap.newKeySet();

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private double radius = 0.5;

            @Override
            public void run() {
                if (!attacker.isOnline() || attacker.isDead()) {
                    cancel();
                    return;
                }

                radius += 0.5;
                if (radius >= 6.0) {
                    cancel();
                    int enemies = hitPlayers.size();
                    Messages.send(attacker, enemies == 1 ? "armor.soul-burst-enemy" : "armor.soul-burst-enemies",
                            "count", String.valueOf(enemies));
                    return;
                }

                ParticleUtils.soulBurstRing(center, radius);

                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(attacker)) continue;
                    if (hitPlayers.contains(target.getUniqueId())) continue;

                    if (session != null) {
                        Team aTeam = session.getPlayerTeam(attacker);
                        Team tTeam = session.getPlayerTeam(target);
                        if (tTeam != null && aTeam == tTeam) continue;
                    }

                    hitPlayers.add(target.getUniqueId());

                    double newHealth = Math.max(0.0, target.getHealth() - 3.0);
                    target.setHealth(newHealth);

                    double healAmount = Math.min(max - attacker.getHealth(), 3.0);
                    attacker.setHealth(attacker.getHealth() + healAmount);

                    ParticleUtils.hitFeedback(target.getLocation(), 10, 0.2);
                }
            }
        }, 0L, 2L);
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
            CashClashPlayer.applyEffect(p, PotionEffectType.SPEED, cfg.getFlamebringerSpeedDuration() * 20, cfg.getFlamebringerSpeedLevel(), false, false, true);
            // Track when this speed effect should end
            flamebringerSpeedEndTime.put(id, currentTime + (cfg.getFlamebringerSpeedDuration() * 1000L));
        } else {
            // Player is no longer on fire, check if speed should be cleared
            Long endTime = flamebringerSpeedEndTime.get(id);
            if (endTime != null && currentTime >= endTime) {
                CashClashPlayer.removeEffect(p, PotionEffectType.SPEED);
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

        CashClashPlayer.applyEffect(p, PotionEffectType.SPEED, cfg.getFlamebringerSpeedDuration() * 20, cfg.getFlamebringerSpeedLevel(), false, false, true);
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

            GameSession session = GameManager.getInstance().getPlayerSession(p);
            Team playerTeam = session != null ? session.getPlayerTeam(p) : null;

            for (Entity entity : p.getNearbyEntities(1.2, 1.0, 1.2)) {
                if (!(entity instanceof Player target)) continue;
                if (target.equals(p)) continue;

                if (session != null && playerTeam != null) {
                    Team targetTeam = session.getPlayerTeam(target);
                    if (targetTeam == playerTeam) continue;
                }
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

                for (Entity entity : killer.getWorld().getNearbyEntities(killerLoc, radius, radius, radius)) {
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
            Player teammate = Bukkit.getPlayer(uuid);
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
            Player teammate = Bukkit.getPlayer(uuid);
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

        bullseyeHitCount.clear();

        tectonicCharge1Cooldown.clear();
        tectonicCharge2Cooldown.clear();

        dragonScales.clear();
        dragonHitCount.clear();
        dragonRushDamageBuff.clear();
        dragonRushInvincible.clear();
        dragonRushIndicators.clear();
        dragonOutrageIndicators.clear();
        dragonOutrageTasks.values().forEach(BukkitTask::cancel);
        dragonOutrageTasks.clear();
        dragonOutrageActive.clear();
        dragonOutrageStartTime.clear();

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
        bullseyeHitCount.clear();

        dragonScales.clear();
        dragonHitCount.clear();
        dragonRushDamageBuff.clear();
        dragonRushInvincible.clear();
        dragonRushIndicators.clear();
        dragonOutrageIndicators.clear();
        dragonOutrageTasks.values().forEach(BukkitTask::cancel);
        dragonOutrageTasks.clear();
        dragonOutrageActive.clear();
        dragonOutrageStartTime.clear();

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
            RoundData roundData = session.getCurrentRoundData();
            if (roundData != null && !roundData.isAlive(player.getUniqueId())) {
                return true;
            }
        }

        if (session == null || session.getGamemode() == null) return false;
        if (!(session.getGamemode() instanceof CaptureTheFlagGamemode gamemode)) return false;
        return gamemode.isSilenced(player.getUniqueId());
    }
}

