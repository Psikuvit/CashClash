package me.psikuvit.cashClash.manager.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Ice Fan: an ability-only tool. Left-click swings a discrete gust tick that
 * deals damage and builds a freeze streak on each target (freezing after ~3
 * continuous seconds); right-click fires a burst that instantly freezes. Both
 * consume a PDC-backed 75-point durability budget mirrored onto the visual bar.
 */
public class IceFanHandler extends CustomItemHandler {

    // Ice Fan - consecutive gust-hit streak per target (for the freeze-after-3s rule) and a
    // transient flag suppressing DamageListener's vanilla-melee cancellation for its own hits
    private final Map<UUID, Integer> iceFanGustStreak;
    private final Map<UUID, BukkitTask> iceFanGustResetTasks;
    private final Set<UUID> iceFanAbilityDamageActive;

    public IceFanHandler(CustomItemManager manager) {
        super(manager);
        this.iceFanGustStreak = new HashMap<>();
        this.iceFanGustResetTasks = new HashMap<>();
        this.iceFanAbilityDamageActive = new HashSet<>();
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
     * Left-click: one discrete ~0.5s "gust tick" per swing (Bukkit cannot detect a held-down
     * left mouse button - sustained rapid clicking approximates "continuous").
     */
    public void handleIceFanLeftClick(Player player, ItemStack item) {
        int remaining = getIceFanDurability(item);
        if (remaining <= 0) {
            Messages.send(player, "customitem.ice-fan-broken");
            return;
        }

        int drainThisClick = Math.max(1, cfg.getIceFanGustDurabilityPerSecond() / 2);
        int newRemaining = remaining - drainThisClick;
        setIceFanDurability(item, newRemaining);

        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();
        for (Player target : findIceFanTargets(player, origin, direction, 3)) {
            dealIceFanDamage(player, target, cfg.getIceFanGustDamagePerTick());
            registerIceFanGustHit(target.getUniqueId());
        }

        ParticleUtils.iceFanGust(origin.clone().add(direction.clone().multiply(1.5)));
        SoundUtils.play(player, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.6f);

        if (newRemaining <= 0) breakIceFan(player, item);
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
        for (Player target : findIceFanTargets(player, origin, direction, 4)) {
            dealIceFanDamage(player, target, cfg.getIceFanBurstDamage());
            target.setFreezeTicks(target.getMaxFreezeTicks());
        }

        ParticleUtils.iceFanBurst(origin.clone().add(direction.clone().multiply(2)));
        SoundUtils.play(player, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.0f, 0.6f);

        if (newRemaining <= 0) breakIceFan(player, item);
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

    /**
     * Tracks consecutive gust hits on a target, freezing them once they've been hit enough
     * times to approximate 3 continuous seconds of gust (sustained ~2 clicks/sec); the streak
     * resets if a full second passes without another gust hit landing.
     */
    private void registerIceFanGustHit(UUID targetUuid) {
        int hits = iceFanGustStreak.merge(targetUuid, 1, Integer::sum);

        BukkitTask existingReset = iceFanGustResetTasks.remove(targetUuid);
        if (existingReset != null) existingReset.cancel();

        int requiredHits = cfg.getIceFanGustFreezeSecondsRequired() * 2;
        if (hits >= requiredHits) {
            iceFanGustStreak.remove(targetUuid);
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) target.setFreezeTicks(target.getMaxFreezeTicks());
            return;
        }

        BukkitTask resetTask = SchedulerUtils.runTaskLater(() -> iceFanGustStreak.remove(targetUuid), 20L);
        iceFanGustResetTasks.put(targetUuid, resetTask);
    }

    private int getIceFanDurability(ItemStack item) {
        Integer remaining = PDCDetection.getItemUses(item);
        return remaining != null ? remaining : cfg.getIceFanMaxDurability();
    }

    /**
     * Persists remaining durability as a PDC counter and mirrors it onto the visual durability
     * bar proportionally, since Shears' vanilla max durability doesn't match the 75-point budget.
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
        iceFanGustResetTasks.values().forEach(BukkitTask::cancel);
        iceFanGustResetTasks.clear();
        iceFanGustStreak.clear();
        iceFanAbilityDamageActive.clear();
    }
}
