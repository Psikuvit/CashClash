package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ice Fan: a single right-click ability - a half-circle burst of cold wind swept out in front
 * of the player, damaging, freezing and knocking back everyone it hits. Has a fixed number of
 * uses (a plain PDC counter, no vanilla/visual durability bar) before it breaks.
 */
public class IceFanHandler extends CustomItemHandler {

    // Half the arc's total angular width - 90 either side of the aim direction makes a full
    // 180 semicircle swept out in front of the player.
    private static final double HALF_CIRCLE_HALF_ANGLE_DEGREES = 90;
    private static final double ARC_STEP_DEGREES = 15;

    // A transient flag suppressing DamageListener's vanilla-melee cancellation for the burst's
    // own hits
    private final Set<UUID> iceFanAbilityDamageActive;
    // Independent freeze-stack timer per target, and the task pumping it into freezeTicks
    private final Map<UUID, Long> iceFanFreezeExpiresAt;
    private final Map<UUID, BukkitTask> iceFanFreezePumpTasks;

    public IceFanHandler(CustomItemManager manager) {
        super(manager);
        this.iceFanAbilityDamageActive = new HashSet<>();
        this.iceFanFreezeExpiresAt = new HashMap<>();
        this.iceFanFreezePumpTasks = new HashMap<>();
    }

    /**
     * @return true if the attacker is currently mid-swing with Ice Fan's own burst ability
     * (used by DamageListener to distinguish that from a vanilla melee swing, which Ice Fan
     * should never deal - it's a pure ability-tool).
     */
    public boolean isIceFanAbilityDamage(UUID attackerUuid) {
        return iceFanAbilityDamageActive.contains(attackerUuid);
    }

    /**
     * Right-click: sweeps a half-circle burst of cold wind out in front of the player, damaging,
     * freezing and knocking back every enemy caught in it. Consumes one of the item's fixed
     * number of uses, breaking it once they run out.
     */
    public void handleIceFanRightClick(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ICE_FAN_BURST)) return;

        int usesRemaining = getIceFanUsesRemaining(item);
        if (usesRemaining <= 0) {
            Messages.send(player, "customitem.ice-fan-broken");
            return;
        }

        int newUses = usesRemaining - 1;
        setIceFanUsesRemaining(item, newUses);
        player.getInventory().setItemInMainHand(item);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ICE_FAN_BURST, cfg.getIceFanCooldownSeconds());

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, cfg.getIceFanRange())) {
            dealIceFanDamage(player, target, cfg.getIceFanBurstDamage());
            stackIceFanFreeze(target);

            Vector knockback = target.getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .multiply(0.45)
                    .setY(0.25);
            target.setVelocity(target.getVelocity().add(knockback));
        }

        spawnBurstShootParticles(player, origin, direction);
        SoundUtils.play(player, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.0f, 0.6f);

        if (newUses <= 0) breakIceFan(player);
    }

    /**
     * Stacks freeze duration onto the target's remaining Ice Fan freeze time, capped at a
     * configured max, and starts the pump task (if not already running) that keeps freezeTicks
     * synced to it every tick - directly setting freezeTicks here would just get overwritten by
     * the next pump.
     */
    private void stackIceFanFreeze(Player target) {
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();

        long currentExpiry = iceFanFreezeExpiresAt.getOrDefault(uuid, now);
        long remaining = Math.max(0, currentExpiry - now);
        long newRemaining = Math.min(remaining + cfg.getIceFanFreezeDurationMs(), cfg.getIceFanMaxFreezeMs());
        iceFanFreezeExpiresAt.put(uuid, now + newRemaining);

        if (!iceFanFreezePumpTasks.containsKey(uuid)) {
            BukkitTask task = SchedulerUtils.runTaskTimer(() -> pumpIceFanFreeze(target), 0L, cfg.getIceFanFreezePumpIntervalTicks());
            iceFanFreezePumpTasks.put(uuid, task);
        }
    }

    /**
     * Keeps the target's freezeTicks matching the remaining time on our own timer every tick,
     * overriding Bukkit's natural decay, until the stack expires. Also pulses the blue freeze
     * heart indicator every 5 ticks (matching BlazeBite Glacier's frostbite particle cadence)
     * for the whole freeze duration, not just a one-off burst on the hit that caused it.
     */
    private void pumpIceFanFreeze(Player target) {
        UUID uuid = target.getUniqueId();
        Long expiresAt = iceFanFreezeExpiresAt.get(uuid);
        long now = System.currentTimeMillis();

        if (expiresAt == null || now >= expiresAt || !target.isOnline()) {
            iceFanFreezeExpiresAt.remove(uuid);
            BukkitTask task = iceFanFreezePumpTasks.remove(uuid);
            if (task != null) task.cancel();
            return;
        }

        int ticksLeft = (int) ((expiresAt - now) / 50L);
        target.setFreezeTicks(140 + ticksLeft);

        if (ticksLeft % 5 == 0) {
            ParticleUtils.blueFreezeHeart(target.getEyeLocation().add(0, 0.5, 0));
        }
    }

    /**
     * Half-circle sweep of burst particles shooting outward from the player - same per-step
     * distance, stagger, color and density as before, just swept across a 180 arc in front of
     * the player at each step instead of a single point along a straight line.
     */
    private void spawnBurstShootParticles(Player player, Location origin, Vector direction) {
        Vector flatDirection = direction.clone().setY(0);
        if (flatDirection.lengthSquared() < 1.0E-4) flatDirection = new Vector(0, 0, 1);
        Vector arcDirection = flatDirection.normalize();

        for (int i = 0; i < 5; i++) {
            double distance = 0.6 + (i * 0.7);
            SchedulerUtils.runTaskLater(() -> {
                if (!player.isOnline()) return;
                for (double angle = -HALF_CIRCLE_HALF_ANGLE_DEGREES; angle <= HALF_CIRCLE_HALF_ANGLE_DEGREES; angle += ARC_STEP_DEGREES) {
                    Vector rotated = rotateAroundY(arcDirection, Math.toRadians(angle));
                    ParticleUtils.iceFanBurst(origin.clone().add(rotated.multiply(distance)));
                }
            }, i);
        }
    }

    /**
     * Rotates a horizontal vector around the world Y axis by the given angle (radians).
     */
    private Vector rotateAroundY(Vector v, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vector(v.getX() * cos + v.getZ() * sin, v.getY(), -v.getX() * sin + v.getZ() * cos);
    }

    /**
     * Finds enemy players within range and within the half-circle arc swept in front of the
     * player, matching the burst's visual.
     */
    private List<Player> findIceFanTargets(Player player, Location origin, Vector direction, double range) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        List<Player> targets = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (playerTeam != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam == null || targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;
            }
            if (!isInCone(origin, direction, target.getEyeLocation(), HALF_CIRCLE_HALF_ANGLE_DEGREES)) continue;
            targets.add(target);
        }
        return targets;
    }

    private boolean isInCone(Location origin, Vector facing, Location targetLoc, double halfAngleDegrees) {
        Vector toTarget = targetLoc.toVector().subtract(origin.toVector());
        if (toTarget.lengthSquared() < 1.0E-4) return true;
        double angle = Math.toDegrees(facing.clone().normalize().angle(toTarget.normalize()));
        return angle <= halfAngleDegrees;
    }

    /**
     * Deals Ice Fan ability damage while flagged so DamageListener.onIceFanMeleeSuppression
     * doesn't also cancel this explicit hit.
     */
    private void dealIceFanDamage(Player attacker, Player target, double damage) {
        iceFanAbilityDamageActive.add(attacker.getUniqueId());
        try {
            target.damage(damage, attacker);
        } finally {
            iceFanAbilityDamageActive.remove(attacker.getUniqueId());
        }
    }

    private int getIceFanUsesRemaining(ItemStack item) {
        Integer remaining = PDCDetection.getItemUses(item);
        return remaining != null ? remaining : cfg.getIceFanMaxUses();
    }

    private void setIceFanUsesRemaining(ItemStack item, int remaining) {
        if (!item.hasItemMeta()) return;
        int clamped = Math.clamp(remaining, 0, cfg.getIceFanMaxUses());
        PDCSetter.of(item).set(Keys.ITEM_USES, PersistentDataType.INTEGER, clamped).apply();
    }

    private void breakIceFan(Player player) {
        Messages.send(player, "customitem.ice-fan-broken");
        SoundUtils.play(player, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);

        // Identity check (is an Ice Fan still sitting in that hand) rather than an exact
        // ItemStack#equals match against the (possibly stale/copied) reference passed in -
        // getItemInMainHand()/getItemInOffHand() aren't guaranteed to return the same object
        // setIceFanUsesRemaining just mutated.
        if (PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.ICE_FAN) {
            player.getInventory().setItemInMainHand(null);
        } else if (PDCDetection.getCustomItem(player.getInventory().getItemInOffHand()) == CustomItem.ICE_FAN) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    @Override
    public void cleanup() {
        iceFanAbilityDamageActive.clear();

        iceFanFreezePumpTasks.values().forEach(BukkitTask::cancel);
        iceFanFreezePumpTasks.clear();
        iceFanFreezeExpiresAt.clear();
    }
}
