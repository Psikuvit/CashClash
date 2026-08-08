package me.psikuvit.cashClash.manager.items.weapon;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.armor.DeathmaulerSetHandler;
import me.psikuvit.cashClash.manager.items.armor.DragonSetHandler;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Soul Katana - Phantom Slice: shift + right-click launches the player into a dash
 * that resolves on landing. Every enemy within the forward cone takes flat ability
 * damage (armor/effects zeroed through the transient phantom-slice flag) plus a
 * healing-reduction debuff shown by a rotating soul mark. Also hooks the Deathmauler
 * soul-burst and the Dragon-scale armor counters on a strike.
 */
public class SoulKatanaHandler extends WeaponItemHandler {

    // Soul Katana - Phantom Slice: attackers whose damage call is the flat ability strike (set
    // only around the direct damage call so DamageListener zeroes armor/effect modifiers there)
    private final Set<UUID> phantomSliceDamageActive;

    // Soul Katana - Phantom Slice dash state: a dashing player strikes on landing (after leaving
    // the ground); the last-location map feeds the dash trail and the mark task the debuff aura
    private final Map<UUID, Boolean> soulKatanaDashing;
    private final Map<UUID, Boolean> soulKatanaLeftGround;
    private final Map<UUID, Location> soulKatanaLastLocations;
    private final Map<UUID, BukkitTask> soulKatanaTrailTasks;
    private final Map<UUID, BukkitTask> soulKatanaMarkTasks;

    public SoulKatanaHandler(WeaponItemManager manager) {
        super(manager);
        this.phantomSliceDamageActive = new HashSet<>();
        this.soulKatanaDashing = new HashMap<>();
        this.soulKatanaLeftGround = new HashMap<>();
        this.soulKatanaLastLocations = new HashMap<>();
        this.soulKatanaTrailTasks = new HashMap<>();
        this.soulKatanaMarkTasks = new HashMap<>();
    }

    /**
     * Shift + right-click Phantom Slice: launches the player into a dash. The strike does not
     * resolve on a timer - it lands once the player leaves the ground and touches back down
     * (see {@link #handleSoulKatanaLand(Player)}).
     */
    public void usePhantomSlice(Player player) {
        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE)) {
            double remaining = cooldownManager.getRemainingCooldownMs(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE) / 1000.0;
            Messages.send(player, "customitem.soul-katana-cooldown", "remaining", String.valueOf((int) Math.ceil(remaining)));
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.SOUL_KATANA_PHANTOM_SLICE, cfg.getSoulKatanaCooldownSeconds());

        double leap = cfg.getSoulKatanaLeapDistance();
        Vector dir = player.getLocation().getDirection().normalize();
        player.setVelocity(dir.multiply(leap * 0.2).setY(0.4));

        Messages.send(player, "customitem.soul-katana-slice");
        SoundUtils.play(player, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);

        soulKatanaDashing.put(uuid, true);
        soulKatanaLeftGround.put(uuid, false);
        startSoulKatanaDashTrail(player);
    }

    /**
     * Called from MoveListener while a player is dashing: tracks leaving the ground and resolves
     * the Phantom Slice strike on landing - every enemy within the strike reach in the forward
     * cone takes flat ability damage (armor/effects zeroed via {@link #isPhantomSliceDamage(UUID)})
     * plus the healing-reduction debuff.
     */
    public void handleSoulKatanaLand(Player player) {
        UUID id = player.getUniqueId();
        if (!soulKatanaDashing.containsKey(id)) return;

        if (!player.isOnGround()) {
            soulKatanaLeftGround.put(id, true);
            return;
        }
        if (!soulKatanaLeftGround.getOrDefault(id, false)) return;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        double reach = cfg.getSoulKatanaStrikeRadius();
        int debuffDuration = cfg.getSoulKatanaHealingReductionDurationSeconds();
        double healingMultiplier = 1.0 - cfg.getSoulKatanaHealingReductionPercent() / 100.0;

        Vector playerDirection = player.getLocation().getDirection().normalize();
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player)) continue;
            if (session != null) {
                Team attackerTeam = session.getPlayerTeam(player);
                Team targetTeam = session.getPlayerTeam(target);
                if (attackerTeam != null && attackerTeam.equals(targetTeam)) continue;
            }
            Vector delta = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (delta.lengthSquared() <= 0.0001) continue;
            Vector directionToTarget = delta.normalize();
            double dot = playerDirection.dot(directionToTarget);
            if (player.getLocation().distance(target.getLocation()) > reach || dot <= 0.3) continue;

            dealPhantomSliceDamage(target, player);
            if (session != null) armorManager.getHandler(DeathmaulerSetHandler.class).tryDeathmaulerSoulBurst(player, target, session);
            armorManager.getHandler(DragonSetHandler.class).handleDragonHit(player);
            SoundUtils.playAt(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);

            CashClashPlugin.getInstance().getCustomItemManager().applyHealingReduction(target.getUniqueId(), healingMultiplier, debuffDuration);
            startSoulKatanaHealingMark(target);
        }

        // Forward arc burst marking the end of the dash
        Location center = player.getLocation();
        Vector facing = player.getLocation().getDirection().normalize();
        for (double angle = -90; angle <= 90; angle += 9) {
            Vector particleDirection = facing.clone().rotateAroundY(Math.toRadians(angle));
            Location particleLocation = center.clone().add(0, 1.8, 0).add(particleDirection.multiply(2.9));
            ParticleUtils.spawnDust(particleLocation, Color.fromRGB(120, 220, 255), 1.5f, 5, 0.12);
            ParticleUtils.spawnDust(particleLocation, Color.fromRGB(30, 120, 255), 1.3f, 3, 0.08);
        }
        SoundUtils.playAt(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.5f);

        endSoulKatanaDash(player);
    }

    /**
     * Direct damage call still fires a real EntityDamageByEntityEvent for kill attribution;
     * the transient flag lets DamageListener zero this hit's armor/effect modifiers.
     */
    private void dealPhantomSliceDamage(Player target, Player attacker) {
        double damage = cfg.getSoulKatanaStrikeDamage();
        phantomSliceDamageActive.add(attacker.getUniqueId());
        try {
            target.damage(damage, attacker);
        } finally {
            phantomSliceDamageActive.remove(attacker.getUniqueId());
        }
    }

    /**
     * Dust + end-rod trail behind the dashing player, self-cancelling when the dash ends.
     */
    private void startSoulKatanaDashTrail(Player player) {
        UUID id = player.getUniqueId();
        soulKatanaLastLocations.put(id, player.getLocation().clone());
        BukkitTask task = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !soulKatanaDashing.containsKey(id)) {
                    cancel();
                    soulKatanaTrailTasks.remove(id);
                    soulKatanaLastLocations.remove(id);
                    return;
                }
                Location last = soulKatanaLastLocations.get(id);
                Location current = player.getLocation().clone();
                if (last != null) {
                    Vector delta = current.toVector().subtract(last.toVector());
                    if (delta.lengthSquared() > 0.0001) {
                        // particles trail behind the movement
                        Location particleLoc = last.clone().add(0, 1, 0).subtract(delta.normalize().multiply(0.5));
                        ParticleUtils.spawnDust(particleLoc, Color.fromRGB(120, 220, 255), 1.3f, 8, 0.15);
                        ParticleUtils.spawn(Particle.END_ROD, particleLoc, 3, 0.1);
                    }
                }
                soulKatanaLastLocations.put(id, current);
            }
        }, 0L, 1L);
        soulKatanaTrailTasks.put(id, task);
    }

    /**
     * Rotating soul mark over a marked target's head, pulsing while the healing-reduction debuff
     * is active. Self-cancelling when the debuff expires or the target leaves.
     */
    private void startSoulKatanaHealingMark(Player target) {
        UUID id = target.getUniqueId();
        if (soulKatanaMarkTasks.containsKey(id)) return; // already marked

        Messages.send(target, "customitem.soul-katana-healing-reduced", "percent", String.valueOf(cfg.getSoulKatanaHealingReductionPercent()));
        BukkitTask task = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int soundTick = 0;

            @Override
            public void run() {
                Long endTime = CashClashPlugin.getInstance().getCustomItemManager().getHealingReducedUntil().get(id);
                if (endTime == null || System.currentTimeMillis() >= endTime || !target.isOnline()) {
                    cancel();
                    soulKatanaMarkTasks.remove(id);
                    CashClashPlugin.getInstance().getCustomItemManager().getHealingReducedUntil().remove(id);
                    CashClashPlugin.getInstance().getCustomItemManager().getHealingReductionMultiplier().remove(id);
                    return;
                }
                Location center = target.getLocation().clone().add(0, 2.4, 0);
                long time = System.currentTimeMillis();
                for (int i = 0; i < 12; i++) {
                    double angle = (Math.PI * 2 / 12 * i) + (time % 1000) / 1000.0 * Math.PI * 2;
                    double x = Math.cos(angle) * 0.35;
                    double z = Math.sin(angle) * 0.35;
                    ParticleUtils.spawnDust(center.clone().add(x, 0, z), Color.fromRGB(0, 50, 255), 1.2f, 1, 0);
                }
                if (++soundTick >= 15) {
                    soundTick = 0;
                    SoundUtils.playAt(target.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8f, 0.6f);
                }
                ParticleUtils.spawnDust(center.clone().add((Math.random() - 0.5) * 0.4, -0.3, (Math.random() - 0.5) * 0.4),
                        Color.fromRGB(100, 200, 255), 1.0f, 2, 0);
            }
        }, 0L, 2L);
        soulKatanaMarkTasks.put(id, task);
    }

    private void endSoulKatanaDash(Player player) {
        UUID id = player.getUniqueId();
        soulKatanaDashing.remove(id);
        soulKatanaLeftGround.remove(id);
        BukkitTask task = soulKatanaTrailTasks.remove(id);
        if (task != null) task.cancel();
        soulKatanaLastLocations.remove(id);
    }

    /**
     * @return true while the player's Phantom Slice strike damage is being applied (transient)
     */
    public boolean isPhantomSliceDamage(UUID attackerUuid) {
        return phantomSliceDamageActive.contains(attackerUuid);
    }

    @Override
    public void cleanup() {
        soulKatanaTrailTasks.values().forEach(BukkitTask::cancel);
        soulKatanaTrailTasks.clear();
        soulKatanaMarkTasks.values().forEach(BukkitTask::cancel);
        soulKatanaMarkTasks.clear();
        soulKatanaDashing.clear();
        soulKatanaLeftGround.clear();
        soulKatanaLastLocations.clear();

        phantomSliceDamageActive.clear();
    }
}
