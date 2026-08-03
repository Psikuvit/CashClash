package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warden Gloves - boxing punch ability with Speed I and a right-click shockwave cone.
 */
public class WardenGlovesHandler extends MythicItemHandler {

    // Warden Gloves boxing punch counter (UUID -> punch count)
    private final Map<UUID, Integer> wardenPunchCount;
    // Warden Gloves boxing ability active (UUID -> true if ability is active)
    private final Set<UUID> wardenBoxingActive;

    public WardenGlovesHandler(MythicItemManager manager) {
        super(manager);
        this.wardenPunchCount = new ConcurrentHashMap<>();
        this.wardenBoxingActive = ConcurrentHashMap.newKeySet();
    }

    /**
     * Warden Gloves boxing ability - Left click to punch.
     * On every 5th punch, speed increases.
     * Ability lasts for 20 seconds, 35 second cooldown.
     */
    public void useWardenPunch(Player player, Player victim) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Punch attack on " + victim.getName());

        // Check if boxing ability is on cooldown (ability hasn't been started yet)
        if (!wardenBoxingActive.contains(uuid) && cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_BOXING)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING);
            Messages.send(player, "mythic.genericitem-cooldown", "{item_name}", "Boxing gloves", "{cooldown_seconds}", String.valueOf(remaining));
            return;
        }

        // Start boxing ability if not already active
        if (!wardenBoxingActive.contains(uuid)) {
            startWardenBoxingAbility(player);
        }

        // Increment punch count (kept for analytics/debug)
        int punchCount = wardenPunchCount.getOrDefault(uuid, 0) + 1;
        wardenPunchCount.put(uuid, punchCount);

        // Apply punch knockback
        Vector knockback = victim.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(1.2)
                .setY(0.4);
        victim.setVelocity(knockback);

        // Maintain Speed I every punch to avoid ramping
        int durationTicks = cfg.getWardenBoxingDuration() * 20;
        CashClashPlayer.applyEffect(player, PotionEffectType.SPEED, durationTicks, 0, false, true);

        // Punch sound effect
        SoundUtils.play(victim, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
        ParticleUtils.sweep(victim.getLocation().add(0, 1, 0));

        Messages.debug(player, "WARDEN_GLOVES: Punch hit! Count: " + punchCount);
    }

    /**
     * Start the Warden boxing ability (20 second duration).
     */
    private void startWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        wardenBoxingActive.add(uuid);
        wardenPunchCount.put(uuid, 0);

        // Start with Speed I immediately and keep it at Speed I
        int durationTicks = cfg.getWardenBoxingDuration() * 20;
        CashClashPlayer.applyEffect(player, PotionEffectType.SPEED, durationTicks, 0, false, true);

        Messages.send(player, "mythic.boxing-gloves-activated");
        Messages.send(player, "mythic.genericitem-punch");
        SoundUtils.play(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);

        // End the ability after duration
        BukkitTask endTask = SchedulerUtils.runTaskLater(() -> endWardenBoxingAbility(player), durationTicks);

        manager.trackTask(uuid, endTask);

        Messages.debug(player, "WARDEN_GLOVES: Boxing ability started with Speed I - " + cfg.getWardenBoxingDuration() + "s duration");
    }

    /**
     * End the Warden boxing ability and start cooldown.
     */
    private void endWardenBoxingAbility(Player player) {
        UUID uuid = player.getUniqueId();

        if (!wardenBoxingActive.contains(uuid)) return;

        wardenBoxingActive.remove(uuid);
        wardenPunchCount.remove(uuid);

        // Remove speed effect
        CashClashPlayer.removeEffect(player, PotionEffectType.SPEED);

        // Start cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_BOXING, cfg.getWardenBoxingCooldown());
        Messages.send(player, "mythic.boxing-gloves-cooldown", "seconds", String.valueOf(cfg.getWardenBoxingCooldown()));
        Messages.debug(player, "WARDEN_GLOVES: Boxing ability ended - cooldown " + cfg.getWardenBoxingCooldown() + "s");
    }

    /**
     * Check if player has boxing ability active.
     */
    public boolean isWardenBoxingActive(UUID playerId) {
        return wardenBoxingActive.contains(playerId);
    }

    /**
     * Warden Gloves shockwave attack (Right-click ability).
     * Unleashes shockwave dealing damage + big knockback in cone.
     */
    public void useWardenShockwave(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WARDEN_GLOVES: Shockwave ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)) {
            Messages.debug(player, "WARDEN_GLOVES: Shockwave on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE) + "s");
            Messages.send(player, "mythic.shockwave-cooldown", "cooldown_seconds",
                    String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE)));
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WARDEN_SHOCKWAVE, cfg.getWardenShockwaveCooldown());

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.debug(player, "WARDEN_GLOVES: No session");
            return;
        }

        Team playerTeam = session.getPlayerTeam(player);
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().setY(0).normalize();
        World world = player.getWorld();

        // Sonic boom visual effect
        ParticleUtils.sonicBoom(loc.clone().add(direction.clone().multiply(2)).add(0, 1, 0));
        SoundUtils.playAt(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        int range = cfg.getWardenShockwaveRange();
        int hitCount = 0;

        // Damage and knockback enemies in cone
        for (Entity entity : world.getNearbyEntities(loc, range, 4, range)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            Team targetTeam = session.getPlayerTeam(target);
            if (targetTeam != null && playerTeam != null &&
                targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) continue;

            // Check if target is in front of player (cone check)
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            if (direction.dot(toTarget) < 0.3) continue; // Not in cone (about 70 degree cone)

            target.damage(cfg.getWardenShockwaveDamage(), player);

            Vector knockback = toTarget.multiply(cfg.getWardenKnockbackPower()).setY(0.8);
            target.setVelocity(knockback);
            hitCount++;
        }

        Messages.debug(player, "WARDEN_GLOVES: Shockwave hit " + hitCount + " enemies, damage: " + cfg.getWardenShockwaveDamage() + ", cooldown: " + cfg.getWardenShockwaveCooldown() + "s");
        Messages.send(player, "mythic.shockwave-activated");
    }

    @Override
    public void cleanup() {
        wardenPunchCount.clear();
        wardenBoxingActive.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        wardenPunchCount.remove(uuid);
        wardenBoxingActive.remove(uuid);
    }
}
