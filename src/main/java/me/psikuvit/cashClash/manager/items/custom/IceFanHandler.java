package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
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
import org.bukkit.inventory.meta.Damageable;
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
 * Ice Fan: an ability-only tool. Holding left-click sustains a continuous gust (driven by
 * repeated PlayerAnimationEvent swings, which fire regardless of whether a block, air, or a
 * player is under the cursor) that deals damage and grants 1.5s of freeze directly on every
 * blast that connects; right-click fires a damaging/knockback burst (no freeze). Both consume
 * a PDC-backed 75-point durability budget mirrored onto the visual bar.
 */
public class IceFanHandler extends CustomItemHandler {

    // How long after the last swing before the gust is considered "released". Generous window -
    // this item's swing cadence while held isn't a steady stream (~2/sec like combat), so a short
    // timeout let the gust die between swings after only 1-2 pulses.
    private static final long GUST_HOLD_TIMEOUT_MS = 1500L;
    // Gust tick cadence - matches the original per-click cadence (~2/sec) this replaces
    private static final long GUST_TICK_INTERVAL = 10L;
    // Each gust blast that connects stacks 1.5s of freeze ticks onto the target's current
    // freeze (1.5s -> 3s -> 4.5s ...), capped at 6s total, instead of building a hit streak
    // toward a delayed full freeze.
    private static final int GUST_HIT_FREEZE_TICKS = 30;
    private static final int GUST_MAX_FREEZE_TICKS = 120;

    // Ice Fan - the continuous left-click gust's hold-state, and a transient flag suppressing
    // DamageListener's vanilla-melee cancellation for its own hits
    private final Set<UUID> iceFanAbilityDamageActive;
    private final Map<UUID, Long> gustLastSwingTime;
    private final Map<UUID, BukkitTask> activeGustTasks;

    public IceFanHandler(CustomItemManager manager) {
        super(manager);
        this.iceFanAbilityDamageActive = new HashSet<>();
        this.gustLastSwingTime = new HashMap<>();
        this.activeGustTasks = new HashMap<>();
    }

    /**
     * @return true if the attacker is currently mid-swing with Ice Fan's own gust/burst
     * ability (used by DamageListener to distinguish that from a vanilla melee swing, which
     * Ice Fan should never deal - it's a pure ability-tool).
     */
    public boolean isIceFanAbilityDamage(UUID attackerUuid) {
        return iceFanAbilityDamageActive.contains(attackerUuid);
    }

    /**
     * Called on every arm swing while holding Ice Fan (from PlayerAnimationEvent, which fires
     * regardless of whether a block, air, or a player is under the cursor - so aiming directly
     * at a player still gusts instead of silently doing nothing or throwing a vanilla punch).
     * Starts the continuous gust tick loop if it isn't already running; otherwise just refreshes
     * the "still holding" timer the loop is watching.
     */
    public void onIceFanSwing(Player player) {
        UUID uuid = player.getUniqueId();
        gustLastSwingTime.put(uuid, System.currentTimeMillis());

        if (activeGustTasks.containsKey(uuid)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (getIceFanDurability(item) <= 0) {
            Messages.send(player, "customitem.ice-fan-broken");
            return;
        }

        BukkitTask task = SchedulerUtils.runTaskTimer(() -> tickGust(player), 0L, GUST_TICK_INTERVAL);
        activeGustTasks.put(uuid, task);
    }

    /**
     * One gust pulse - reuses the exact damage/durability numbers the old per-click design used,
     * just fired automatically on a timer instead of needing a fresh click each time. Stops
     * itself once no swing has landed within the hold timeout (release) or the fan breaks.
     */
    private void tickGust(Player player) {
        UUID uuid = player.getUniqueId();
        Long lastSwing = gustLastSwingTime.get(uuid);
        boolean stillHolding = lastSwing != null
                && (System.currentTimeMillis() - lastSwing) <= GUST_HOLD_TIMEOUT_MS
                && player.isOnline()
                && PDCDetection.getCustomItem(player.getInventory().getItemInMainHand()) == CustomItem.ICE_FAN;

        if (!stillHolding) {
            stopGust(uuid);
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        int remaining = getIceFanDurability(item);
        if (remaining <= 0) {
            stopGust(uuid);
            breakIceFan(player, item);
            return;
        }

        int drainThisTick = Math.max(1, cfg.getIceFanGustDurabilityPerSecond() / 2);
        int newRemaining = remaining - drainThisTick;
        setIceFanDurability(item, newRemaining);

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, 3)) {
            dealIceFanDamage(player, target, cfg.getIceFanGustDamagePerTick());
            target.setFreezeTicks(Math.min(target.getFreezeTicks() + GUST_HIT_FREEZE_TICKS, GUST_MAX_FREEZE_TICKS));
            ParticleUtils.blueFreezeHeart(target.getEyeLocation().add(0, 0.5, 0));
        }

        spawnGustParticles(player, origin, direction);
        SoundUtils.play(player, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);

        if (newRemaining <= 0) {
            stopGust(uuid);
            breakIceFan(player, item);
        }
    }

    private void stopGust(UUID uuid) {
        BukkitTask task = activeGustTasks.remove(uuid);
        if (task != null) task.cancel();
        gustLastSwingTime.remove(uuid);
    }

    /**
     * Cyan/white gust particles shooting straight out from the player along the aim direction,
     * matching the right-click burst's outward look instead of a fixed-point puff.
     */
    private void spawnGustParticles(Player player, Location origin, Vector direction) {
        for (double d = 0.6; d <= 3.0; d += 0.7) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            ParticleUtils.iceFanGust(point);
        }
    }

    /**
     * Right-click: a single burst hit, instantly freezing targets. Requires >= 25 durability
     * remaining and costs 25 durability.
     */
    public void handleIceFanRightClick(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.ICE_FAN_BURST)) return;

        int remaining = getIceFanDurability(item);
        if (remaining < cfg.getIceFanBurstMinDurability()) {
            Messages.send(player, "customitem.ice-fan-not-enough-durability");
            return;
        }

        int newRemaining = remaining - cfg.getIceFanBurstDurabilityCost();
        setIceFanDurability(item, newRemaining);
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.ICE_FAN_BURST, 1);

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, 5)) {
            dealIceFanDamage(player, target, cfg.getIceFanBurstDamage());

            Vector knockback = target.getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize()
                    .multiply(0.45)
                    .setY(0.25);
            target.setVelocity(target.getVelocity().add(knockback));
        }

        spawnBurstShootParticles(player, origin, direction);
        SoundUtils.play(player, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.0f, 0.6f);

        if (newRemaining <= 0) breakIceFan(player, item);
    }

    /**
     * Burst particles that travel outward from the player along the aim direction instead of
     * appearing as one static point.
     */
    private void spawnBurstShootParticles(Player player, Location origin, Vector direction) {
        for (int i = 0; i < 5; i++) {
            double distance = 0.6 + (i * 0.7);
            int delay = i;
            SchedulerUtils.runTaskLater(() -> {
                if (!player.isOnline()) return;
                ParticleUtils.iceFanBurst(origin.clone().add(direction.clone().multiply(distance)));
            }, delay);
        }
    }

    /**
     * Finds enemy players within range and within a narrow forward-facing cone, so the gust/burst
     * only hit what the player is actually aiming at.
     */
    private List<Player> findIceFanTargets(Player player, Location origin, Vector direction, double range) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        List<Player> targets = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(origin, range, range, range)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (playerTeam != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam == null || targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;
            }
            if (!isInCone(origin, direction, target.getEyeLocation(), 30)) continue;
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

    private int getIceFanDurability(ItemStack item) {
        Integer remaining = PDCDetection.getItemUses(item);
        return remaining != null ? remaining : cfg.getIceFanMaxDurability();
    }

    /**
     * Persists remaining durability as a PDC counter and mirrors it onto the visual durability
     * bar proportionally, since the underlying material's vanilla max durability doesn't match
     * the 75-point budget.
     */
    private void setIceFanDurability(ItemStack item, int remaining) {
        if (!item.hasItemMeta()) return;

        int max = cfg.getIceFanMaxDurability();
        int clamped = Math.max(0, Math.min(max, remaining));

        PDCSetter tags = PDCSetter.of(item);
        tags.set(Keys.ITEM_USES, PersistentDataType.INTEGER, clamped);

        if (tags.meta() instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            double fractionUsed = 1.0 - ((double) clamped / max);
            damageable.setDamage((int) Math.round(fractionUsed * maxDurability));
        }
        tags.apply();
    }

    private void breakIceFan(Player player, ItemStack item) {
        Messages.send(player, "customitem.ice-fan-broken");
        SoundUtils.play(player, Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);

        if (item.equals(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
        } else if (item.equals(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    @Override
    public void cleanup() {
        activeGustTasks.values().forEach(BukkitTask::cancel);
        activeGustTasks.clear();
        gustLastSwingTime.clear();
        iceFanAbilityDamageActive.clear();
    }
}
