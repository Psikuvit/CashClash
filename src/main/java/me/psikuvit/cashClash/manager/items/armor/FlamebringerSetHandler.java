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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flamebringer Set - Furnace Blood (fire immunity speed), lava speed procs, fire trail,
 * and a gravitational pull after enough kills.
 */
public class FlamebringerSetHandler extends ArmorSetHandler {

    private final Map<UUID, Integer> flamebringerKills; // Player -> kill count this round
    private final Map<UUID, BukkitTask> flamebringerFireTask; // Player -> fire effect task
    private final Map<UUID, Integer> flamebringerLavaUses; // Player -> lava speed procs this game
    private final Map<UUID, Long> flamebringerSpeedEndTime; // Player -> time when speed effect should end
    private final Map<UUID, BukkitTask> flamebringerTrailTasks; // Player -> fire trail task
    private final Map<UUID, Long> flamebringerTrailEndTime; // Player -> time when fire trail should end
    private final Map<UUID, List<Location>> flamebringerTrailLocations; // Player -> recent positions for trail

    public FlamebringerSetHandler(CustomArmorManager manager) {
        super(manager);
        this.flamebringerKills = new ConcurrentHashMap<>();
        this.flamebringerFireTask = new ConcurrentHashMap<>();
        this.flamebringerLavaUses = new ConcurrentHashMap<>();
        this.flamebringerSpeedEndTime = new ConcurrentHashMap<>();
        this.flamebringerTrailTasks = new ConcurrentHashMap<>();
        this.flamebringerTrailEndTime = new ConcurrentHashMap<>();
        this.flamebringerTrailLocations = new ConcurrentHashMap<>();
    }

    public boolean hasFlamebringerSet(Player player) {
        boolean hasBoots = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.FLAMEBRINGER_BOOTS) hasBoots = true;
            if (ca == CustomArmorItem.FLAMEBRINGER_LEGGINGS) hasLegs = true;
        }
        return hasBoots && hasLegs;
    }

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

    @Override
    public void cleanup() {
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
    }

    @Override
    public void resetRoundTracking() {
        // Reset flamebringer kill counters
        flamebringerKills.clear();
        flamebringerSpeedEndTime.clear();
        flamebringerTrailTasks.values().forEach(BukkitTask::cancel);
        flamebringerTrailTasks.clear();
        flamebringerTrailEndTime.clear();
        flamebringerTrailLocations.clear();
    }
}
