package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.AbilityTargets;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Bow - left-click Soaring Heights boost, and a wind gust wherever a fully drawn shot lands.
 * Any other shot is a plain arrow.
 */
public class WindBowHandler extends MythicItemHandler {

    // The speed vanilla gives a bow's arrow at full draw, in blocks per tick
    private static final double FULL_DRAW_ARROW_SPEED = 3.0;

    public WindBowHandler(MythicItemManager manager) {
        super(manager);
    }

    /**
     * Whether a shot releases a wind gust where it lands: only when the bow was drawn all the
     * way back.
     */
    public boolean releasesGust(float force) {
        return force >= 1.0f;
    }

    /**
     * Soaring Heights (left-click): boosts the player up and forward, then goes on cooldown.
     */
    public void useWindBowBoost(Player player) {
        UUID uuid = player.getUniqueId();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_BOOST)) {
            manager.flashCooldown(player);
            return;
        }

        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST, cfg.getWindBowBoostCooldown());

        Vector direction = player.getLocation().getDirection();
        direction.setY(Math.max(direction.getY() + 0.5, 0.5));

        Vector velocity = direction.multiply(cfg.getWindBowBoostPower());
        player.setVelocity(velocity);

        Messages.debug(player, "WIND_BOW: Boosted! Power: " + cfg.getWindBowBoostPower() + ", Cooldown: " + cfg.getWindBowBoostCooldown() + "s");
        SoundUtils.play(player, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
        ParticleUtils.cloud(player.getLocation(), 20, 0.5);

        Messages.send(player, "mythic.wind-bow-boost-activated");
    }

    /**
     * Launches a shot at the speed its draw gives ({@code force} x vanilla's full-draw speed)
     * without the shooter's own movement on top, which vanilla adds: an arrow's damage follows
     * its speed, so a shot fired mid Soaring Heights boost would otherwise hit far harder - or
     * shot backwards, far softer - than the same shot fired standing still.
     */
    public void steadyLaunchSpeed(AbstractArrow arrow, float force) {
        Vector velocity = arrow.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6) return;
        arrow.setVelocity(velocity.normalize().multiply(force * FULL_DRAW_ARROW_SPEED));
    }

    /**
     * Wind gust at an arrow's impact - pushes every enemy around it away from the shooter;
     * teammates are left alone. Outside a game it pushes the practice range's enemy mobs.
     */
    public void releaseGust(Player shooter, Location impact) {
        Vector pushDirection = impact.toVector().subtract(shooter.getLocation().toVector());
        if (pushDirection.lengthSquared() < 1.0E-4) pushDirection = shooter.getLocation().getDirection();
        pushDirection.normalize().setY(0.3);

        double radius = cfg.getWindBowPushRadius();
        int hitCount = 0;
        for (Entity entity : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (!AbilityTargets.isEnemy(shooter, entity)) continue;

            entity.setVelocity(pushDirection.clone().multiply(cfg.getWindBowPushPower()));
            hitCount++;
        }

        Messages.debug(shooter, "WIND_BOW: Wind gust pushed " + hitCount + " enemies, radius: " + radius + ", power: " + cfg.getWindBowPushPower());
        SoundUtils.playAt(impact, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
        ParticleUtils.cloud(impact, 30, 1);
    }

    @Override
    protected Set<String> silencedAbilities() {
        return Set.of("boost");
    }

    @Override
    public List<HudSegment> hudSegments(Player player) {
        List<HudSegment> segments = new ArrayList<>();
        UUID uuid = player.getUniqueId();
        addCooldownSegment(segments, uuid, CooldownManager.Keys.WIND_BOW_BOOST, "boost");
        return segments;
    }

    @Override
    public void cleanup() {
    }
}
