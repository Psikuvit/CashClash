package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Electric Eel Sword - chain lightning on charged hits and a 3-charge zap dash.
 * Each dash charge recharges independently (same shape as Tectonic Cap's two fall-slam
 * charges), damaging and slowing every enemy caught along the dash path.
 */
public class ElectricEelHandler extends MythicItemHandler {

    private static final NamespacedKey EEL_SLOW_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "electric_eel_dash_slow");

    private final Map<UUID, Long> eelDashCharge1Cooldown;
    private final Map<UUID, Long> eelDashCharge2Cooldown;
    private final Map<UUID, Long> eelDashCharge3Cooldown;

    // Victim UUID -> scheduled task that removes their dash-slow modifier; refreshed on re-hit
    private final Map<UUID, BukkitTask> eelSlowRemovalTasks;

    public ElectricEelHandler(MythicItemManager manager) {
        super(manager);
        this.eelDashCharge1Cooldown = new ConcurrentHashMap<>();
        this.eelDashCharge2Cooldown = new ConcurrentHashMap<>();
        this.eelDashCharge3Cooldown = new ConcurrentHashMap<>();
        this.eelSlowRemovalTasks = new ConcurrentHashMap<>();
    }

    /**
     * Electric Eel Sword chain damage.
     * Fully charged hits damage nearby enemies in 5 block radius for 0.5 hearts.
     * 1 second cooldown.
     */
    public void handleElectricEelChain(Player attacker, LivingEntity victim) {
        UUID uuid = attacker.getUniqueId();

        Messages.debug(attacker, "ELECTRIC_EEL: Chain damage check");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN)) {
            Messages.debug(attacker, "ELECTRIC_EEL: Chain on cooldown");
            return;
        }
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ELECTRIC_EEL_CHAIN, cfg.getEelChainCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) {
            Messages.debug(attacker, "ELECTRIC_EEL: No session");
            return;
        }

        Team attackerTeam = session.getPlayerTeam(attacker);
        Location victimLoc = victim.getLocation();
        int radius = cfg.getEelChainRadius();
        int chainCount = 0;

        // Chain damage to nearby enemies
        for (Entity entity : victim.getWorld().getNearbyEntities(victimLoc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(attacker) || target.equals(victim)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && attackerTeam != null &&
                targetTeam.getTeamNumber() == attackerTeam.getTeamNumber()) continue;

            target.damage(cfg.getEelChainDamage(), attacker);
            chainCount++;

            // Lightning spark effect
            ParticleUtils.electricSpark(target.getLocation().add(0, 1, 0), 15, 0.3);
        }

        Messages.debug(attacker, "ELECTRIC_EEL: Chained to " + chainCount + " enemies, radius: " + radius + ", damage: " + cfg.getEelChainDamage());
        SoundUtils.playAt(victimLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.5f);
    }

    /**
     * Electric Eel Sword dash. 3 independent charges (same shape as Tectonic Cap's two
     * fall-slam charges) - blocked only when all three are on cooldown. Zaps the player
     * forward (stopping short of walls, like the old teleport ability), and for a brief
     * window afterward damages+slows any enemy caught along the dash path.
     */
    public void useElectricEelDash(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !eelDashCharge1Cooldown.containsKey(uuid) || eelDashCharge1Cooldown.get(uuid) <= now;
        boolean charge2Ready = !eelDashCharge2Cooldown.containsKey(uuid) || eelDashCharge2Cooldown.get(uuid) <= now;
        boolean charge3Ready = !eelDashCharge3Cooldown.containsKey(uuid) || eelDashCharge3Cooldown.get(uuid) <= now;

        if (!charge1Ready && !charge2Ready && !charge3Ready) {
            long soonest = Math.min(eelDashCharge1Cooldown.getOrDefault(uuid, now),
                    Math.min(eelDashCharge2Cooldown.getOrDefault(uuid, now), eelDashCharge3Cooldown.getOrDefault(uuid, now)));
            long remaining = Math.max(0, (soonest - now) / 1000L);
            Messages.debug(player, "ELECTRIC_EEL: All 3 dash charges on cooldown - " + remaining + "s");
            Messages.send(player, "mythic.electric-eel-dash-cooldown", "{cooldown_seconds}", String.valueOf(remaining));
            return;
        }

        long cooldownEnd = now + cfg.getEelDashRechargeSeconds() * 1000L;
        if (charge1Ready) {
            eelDashCharge1Cooldown.put(uuid, cooldownEnd);
        } else if (charge2Ready) {
            eelDashCharge2Cooldown.put(uuid, cooldownEnd);
        } else {
            eelDashCharge3Cooldown.put(uuid, cooldownEnd);
        }

        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World world = player.getWorld();
        double distance = cfg.getEelDashDistance();

        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);
        double pushStrength = distance;
        if (result != null && result.getHitBlock() != null) {
            double hitDistance = result.getHitPosition().distance(start.toVector());
            pushStrength = Math.max(0.1, hitDistance - 0.5);
            Messages.debug(player, "ELECTRIC_EEL: Dash hit wall, reduced push strength");
        }

        player.setVelocity(direction.multiply(pushStrength));
        ParticleUtils.electricSpark(player.getLocation().add(0, 1, 0), 30, 0.5);
        Messages.send(player, "mythic.electric-eel-zap");

        // Scan for entities caught along the dash path while the push carries the player.
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;
        double dashDamage = cfg.getEelDashDamage();
        double hitRadius = cfg.getEelDashHitRadius();
        Set<UUID> alreadyHit = ConcurrentHashMap.newKeySet();

        BukkitTask scanTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 8 || !player.isOnline()) {
                    cancel();
                    return;
                }

                for (Entity entity : player.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(player) || alreadyHit.contains(target.getUniqueId())) continue;

                    if (session != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && playerTeam != null &&
                            targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;
                    }

                    alreadyHit.add(target.getUniqueId());
                    target.damage(dashDamage, player);
                    applyEelDashSlow(target);
                    SoundUtils.play(target, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.2f);
                    ParticleUtils.electricSpark(target.getLocation().add(0, 1, 0), 20, 0.4);
                }
            }
        }, 0L, 1L);

        manager.trackTask(uuid, scanTask);
        Messages.debug(player, "ELECTRIC_EEL: Dash used, distance: " + pushStrength);
    }

    /**
     * Slows the target's base movement speed by an exact percentage (not a discrete vanilla
     * Slowness level) for a fixed duration, refreshing in place if they're hit again before
     * the previous slow expired.
     */
    private void applyEelDashSlow(Player target) {
        UUID id = target.getUniqueId();
        AttributeInstance speed = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;

        BukkitTask existing = eelSlowRemovalTasks.remove(id);
        if (existing != null && !existing.isCancelled()) existing.cancel();
        speed.removeModifier(EEL_SLOW_KEY);

        double reduction = cfg.getEelDashSlowPercent() / 100.0;
        speed.addModifier(new AttributeModifier(EEL_SLOW_KEY, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));

        int durationTicks = cfg.getEelDashSlowDuration();
        BukkitTask removalTask = SchedulerUtils.runTaskLater(() -> {
            AttributeInstance s = target.getAttribute(Attribute.MOVEMENT_SPEED);
            if (s != null) s.removeModifier(EEL_SLOW_KEY);
            eelSlowRemovalTasks.remove(id);
        }, durationTicks);
        eelSlowRemovalTasks.put(id, removalTask);
        manager.trackTask(id, removalTask);
    }

    @Override
    public void cleanup() {
        eelDashCharge1Cooldown.clear();
        eelDashCharge2Cooldown.clear();
        eelDashCharge3Cooldown.clear();

        eelSlowRemovalTasks.forEach((id, task) -> {
            if (task != null && !task.isCancelled()) task.cancel();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(EEL_SLOW_KEY);
            }
        });
        eelSlowRemovalTasks.clear();
    }
}
