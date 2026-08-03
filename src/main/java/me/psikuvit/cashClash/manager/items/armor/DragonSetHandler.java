package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dragon Set - fully-charged melee hits charge Dragon Scales (up to the configured max).
 * Sneaking while looking at a target consumes one scale to Dragon Rush:
 * - teammate: both players become briefly invincible
 * - enemy: next melee hit deals bonus damage
 * Killing with the set grants Strength for a few seconds.
 * With all scales charged, sneaking launches Dragon Outrage: a massive landing explosion.
 */
public class DragonSetHandler extends ArmorSetHandler {

    private final Map<UUID, Integer> dragonScales; // Player -> charged scales (max 3)
    private final Map<UUID, Integer> dragonHitCount; // Player -> fully-charged melee hits toward next scale
    private final Map<UUID, Long> dragonRushDamageBuff; // Player -> expiry of +25% rush damage buff
    private final Set<UUID> dragonRushInvincible; // Players invincible during teammate Dragon Rush
    private final Set<UUID> dragonRushIndicators; // Players with an active Dragon Rush reminder
    private final Set<UUID> dragonOutrageIndicators; // Players with an active Dragon Outrage reminder
    private final Set<UUID> dragonOutrageActive; // Players mid Dragon Outrage flight
    private final Map<UUID, Long> dragonOutrageStartTime; // Player -> outrage activation time
    private final Map<UUID, BukkitTask> dragonOutrageTasks; // Player -> outrage flight trail task

    public DragonSetHandler(CustomArmorManager manager) {
        super(manager);
        this.dragonScales = new ConcurrentHashMap<>();
        this.dragonHitCount = new ConcurrentHashMap<>();
        this.dragonRushDamageBuff = new ConcurrentHashMap<>();
        this.dragonRushInvincible = ConcurrentHashMap.newKeySet();
        this.dragonRushIndicators = ConcurrentHashMap.newKeySet();
        this.dragonOutrageIndicators = ConcurrentHashMap.newKeySet();
        this.dragonOutrageActive = ConcurrentHashMap.newKeySet();
        this.dragonOutrageStartTime = new ConcurrentHashMap<>();
        this.dragonOutrageTasks = new ConcurrentHashMap<>();
    }

    public boolean hasDragonSet(Player player) {
        boolean hasChest = false, hasBoots = false, hasHelmet = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.DRAGON_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DRAGON_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.DRAGON_HELMET) hasHelmet = true;
        }
        return hasChest && hasBoots && hasHelmet;
    }

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

    @Override
    public void cleanup() {
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
    }

    @Override
    public void resetRoundTracking() {
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
    }
}
