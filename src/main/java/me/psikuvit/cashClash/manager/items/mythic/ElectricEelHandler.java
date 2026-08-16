package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
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
 * Charges recharge one at a time, in sequence - using a charge queues its refill behind
 * whichever charge is already recharging, rather than starting a parallel independent timer
 * per charge.
 */
public class ElectricEelHandler extends MythicItemHandler {

    private final Map<UUID, Integer> eelDashCharges;
    private final Map<UUID, Long> eelNextChargeReadyAt;
    private final Map<UUID, BukkitTask> eelSlowRemovalTasks;

    public ElectricEelHandler(MythicItemManager manager) {
        super(manager);
        this.eelDashCharges = new ConcurrentHashMap<>();
        this.eelNextChargeReadyAt = new ConcurrentHashMap<>();
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

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(attacker);
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
     * Electric Eel Sword dash. Up to the configured max charges, but they recharge
     * sequentially - only one charge is ever recharging at a time, so burning through all
     * of them takes {@code maxCharges * rechargeSeconds} to fully refill, not
     * {@code rechargeSeconds} for all of them in parallel. Blocked only when no charges remain.
     * Zaps the player forward (stopping short of walls, like the old teleport ability), and
     * for a brief window afterward damages+slows any enemy caught along the dash path.
     */
    public void useElectricEelDash(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        settleDashCharges(uuid, now);

        int maxCharges = cfg.getEelDashMaxCharges();
        int charges = eelDashCharges.getOrDefault(uuid, maxCharges);
        if (charges <= 0) {
            long readyAt = eelNextChargeReadyAt.getOrDefault(uuid, now);
            long remaining = Math.max(0, (readyAt - now) / 1000L);
            Messages.debug(player, "ELECTRIC_EEL: No dash charges left - next in " + remaining + "s");
            Messages.send(player, "mythic.electric-eel-dash-cooldown", "{cooldown_seconds}", String.valueOf(remaining));
            return;
        }

        eelDashCharges.put(uuid, charges - 1);
        if (!eelNextChargeReadyAt.containsKey(uuid)) {
            eelNextChargeReadyAt.put(uuid, now + cfg.getEelDashRechargeSeconds() * 1000L);
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
        SoundUtils.play(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        Messages.send(player, "mythic.electric-eel-zap");

        // Scan for entities caught along the dash path while the push carries the player.
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
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
     * Applies any charges that have finished recharging since the last check, chaining the
     * next charge's recharge timer immediately behind the one that just completed - never more
     * than one charge recharging at once.
     */
    private void settleDashCharges(UUID uuid, long now) {
        int maxCharges = cfg.getEelDashMaxCharges();
        int charges = eelDashCharges.getOrDefault(uuid, maxCharges);
        Long readyAt = eelNextChargeReadyAt.get(uuid);
        long rechargeMillis = cfg.getEelDashRechargeSeconds() * 1000L;

        while (readyAt != null && now >= readyAt && charges < maxCharges) {
            charges++;
            readyAt = charges < maxCharges ? readyAt + rechargeMillis : null;
        }

        eelDashCharges.put(uuid, charges);
        if (readyAt != null) {
            eelNextChargeReadyAt.put(uuid, readyAt);
        } else {
            eelNextChargeReadyAt.remove(uuid);
        }
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
        speed.removeModifier(Keys.EEL_SLOW);

        double reduction = cfg.getEelDashSlowPercent() / 100.0;
        speed.addModifier(new AttributeModifier(Keys.EEL_SLOW, -reduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1));

        int durationTicks = cfg.getEelDashSlowDuration();
        BukkitTask removalTask = SchedulerUtils.runTaskLater(() -> {
            AttributeInstance s = target.getAttribute(Attribute.MOVEMENT_SPEED);
            if (s != null) s.removeModifier(Keys.EEL_SLOW);
            eelSlowRemovalTasks.remove(id);
        }, durationTicks);
        eelSlowRemovalTasks.put(id, removalTask);
        manager.trackTask(id, removalTask);
    }

    @Override
    public void cleanup() {
        eelDashCharges.clear();
        eelNextChargeReadyAt.clear();

        eelSlowRemovalTasks.forEach((id, task) -> {
            if (task != null && !task.isCancelled()) task.cancel();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(Keys.EEL_SLOW);
            }
        });
        eelSlowRemovalTasks.clear();
    }
}
