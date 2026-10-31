package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.manager.items.mythic.AlchemistWandHandler;
import me.psikuvit.cashClash.manager.items.mythic.HudSegment;
import me.psikuvit.cashClash.manager.items.mythic.WardenGlovesHandler;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.AbilityTargets;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flamebringer Set - Furnace Blood (fire immunity speed), lava speed procs, fire trail,
 * and a gravitational pull that kills charge and a crouch releases.
 */
public class FlamebringerSetHandler extends ArmorSetHandler {

    private final Set<UUID> sneakReady = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> flamebringerKills; // Player -> kills charged toward the pull this round
    private final Map<UUID, BukkitTask> flamebringerFireTask; // Player -> fire effect task
    private final Map<UUID, Long> flamebringerSpeedEndTime; // Player -> time when speed effect should end
    private final Map<UUID, BukkitTask> flamebringerTrailTasks; // Player -> fire trail task
    private final Map<UUID, Long> flamebringerTrailEndTime; // Player -> time when fire trail should end
    private final Map<UUID, List<Location>> flamebringerTrailLocations; // Player -> recent positions for trail

    public FlamebringerSetHandler(CustomArmorManager manager) {
        super(manager);
        this.flamebringerKills = new ConcurrentHashMap<>();
        this.flamebringerFireTask = new ConcurrentHashMap<>();
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

    @Override
    public List<HudSegment> hudSegments(Player player) {
        if (!hasFlamebringerSet(player)) return List.of();
        List<HudSegment> segments = new ArrayList<>();
        addCooldownSegment(segments, player.getUniqueId(), CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN, "flamebringer");
        segments.add(new HudSegment(hudText("flamebringer-pull",
                "kills", String.valueOf(flamebringerKills.getOrDefault(player.getUniqueId(), 0)),
                "max", String.valueOf(cfg.getFlamebringerKillsForPull())), false));
        return segments;
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
     * Triggered when lava damages the player: grants Speed and a fire trail, then goes on the
     * configured cooldown. No limit on how many times per round.
     */
    public void onFlamebringerLavaDamage(Player p) {
        if (!hasFlamebringerSet(p)) return;
        UUID id = p.getUniqueId();

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN)) {
            return;
        }

        CashClashPlayer.applyEffect(p, PotionEffectType.SPEED, cfg.getFlamebringerSpeedDuration() * 20, cfg.getFlamebringerSpeedLevel(), false, false, true);
        SoundUtils.play(p, Sound.ITEM_FIRECHARGE_USE, 1.5f, 1.0f);
        flamebringerTrailEndTime.put(id, System.currentTimeMillis() + (cfg.getFlamebringerSpeedDuration() * 1000L));
        startFlamebringerTrail(p);
        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.FLAMEBRINGER_LAVA_COOLDOWN, cfg.getFlamebringerAbilityCooldownSeconds());
        Messages.send(p, "armor.flamebringer-lava-activated");
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
                if (AbilityTargets.isEnemy(p, entity)) entity.setFireTicks(60);
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
     * Kills charge the gravitational pull, up to {@code kills-for-pull} - kills past that don't
     * carry over into the next charge.
     */
    public void onFlamebringerKill(Player killer) {
        if (!hasFlamebringerSet(killer)) return;

        UUID id = killer.getUniqueId();
        int needed = cfg.getFlamebringerKillsForPull();
        int before = flamebringerKills.getOrDefault(id, 0);
        if (before >= needed) return;

        flamebringerKills.put(id, before + 1);
        if (before + 1 >= needed) {
            Messages.send(killer, "armor.flamebringer-pull-ready");
            SoundUtils.play(killer, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.6f);
        }
    }

    /**
     * Sneak-start arms the pull and the release fires it, like Bunny Shoes - so a shift-ability
     * used mid-sneak wins over it.
     */
    public void onPlayerToggleSneak(Player player, boolean sneaking) {
        if (!hasFlamebringerSet(player)) return;

        UUID id = player.getUniqueId();
        if (sneaking) {
            sneakReady.add(id);
        } else if (sneakReady.remove(id)) {
            tryActivatePull(player);
        }
    }

    private void tryActivatePull(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
        if (flamebringerKills.getOrDefault(id, 0) < cfg.getFlamebringerKillsForPull()) return;
        if (manager.isMythicShiftLocked(id)) return;
        if (CashClashPlugin.getInstance().getMythicItemManager().getHandler(AlchemistWandHandler.class).isTaunting(id)) return;
        if (CashClashPlugin.getInstance().getMythicItemManager().getHandler(WardenGlovesHandler.class).isRisingFuryActive(id)) return;

        if (isInShoppingPhase(player)) {
            sendShoppingPhaseRestriction(player, "gamestate.cannot-use-custom-armor-shopping");
            return;
        }
        if (isSilenced(player)) {
            Messages.send(player, "listener.cannot-use-items-while-silenced");
            return;
        }

        flamebringerKills.put(id, 0);
        startGravitationalPull(player);
    }

    /**
     * Pulls every enemy around the player's spot at activation towards it for the configured
     * duration.
     */
    private void startGravitationalPull(Player player) {
        Messages.send(player, "armor.flamebringer-pull-activated");
        SoundUtils.play(player, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        double radius = cfg.getFlamebringerPullRadius();
        double pullStrength = cfg.getFlamebringerPullStrength();
        int durationTicks = (int) (cfg.getFlamebringerPullDuration() * 20);
        Location center = player.getLocation();

        BukkitTask pullTask = SchedulerUtils.runTaskTimer(() -> {
            if (!player.isOnline()) return;

            ParticleUtils.flamebringerPull(center, radius);

            for (LivingEntity target : AbilityTargets.enemiesNear(player, center, radius, radius, radius)) {
                Vector direction = center.toVector().subtract(target.getLocation().toVector()).normalize();
                target.setVelocity(direction.multiply(pullStrength));
            }
        }, 0L, 2L);

        SchedulerUtils.runTaskLater(() -> {
            if (pullTask != null) {
                pullTask.cancel();
            }
            if (player.isOnline()) {
                Messages.send(player, "armor.flamebringer-pull-ended");
            }
        }, durationTicks);
    }

    @Override
    public void cleanup() {
        // Cancel all flamebringer tasks
        flamebringerFireTask.values().forEach(BukkitTask::cancel);
        flamebringerFireTask.clear();
        sneakReady.clear();
        flamebringerKills.clear();
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
