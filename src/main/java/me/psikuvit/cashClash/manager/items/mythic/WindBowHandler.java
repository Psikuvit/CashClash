package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.util.ChanceBag;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Wind Bow - left-click Soaring Heights boost, and a wind gust wherever a fixed-luck share of its
 * arrows land.
 */
public class WindBowHandler extends MythicItemHandler {

    private final ChanceBag gustChance;

    public WindBowHandler(MythicItemManager manager) {
        super(manager);
        this.gustChance = new ChanceBag();
    }

    /**
     * Rolls whether this shot's impact releases a wind gust - exactly the configured share of the
     * player's shots.
     */
    public boolean rollGust(Player player) {
        return gustChance.roll(player.getUniqueId(), cfg.getWindBowGustChancePercent());
    }

    /**
     * Soaring Heights (left-click): boosts the player up and forward, then goes on cooldown.
     */
    public void useWindBowBoost(Player player) {
        UUID uuid = player.getUniqueId();

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_BOOST)) {
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
     * Wind gust at an arrow's impact - pushes every player around it (except the shooter) away
     * from the shooter.
     */
    public void releaseGust(Player shooter, Location impact) {
        Vector pushDirection = impact.toVector().subtract(shooter.getLocation().toVector());
        if (pushDirection.lengthSquared() < 1.0E-4) pushDirection = shooter.getLocation().getDirection();
        pushDirection.normalize().setY(0.3);

        double radius = cfg.getWindBowPushRadius();
        int hitCount = 0;
        for (Entity entity : impact.getWorld().getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof Player p)) continue;
            if (p.equals(shooter)) continue;

            p.setVelocity(pushDirection.clone().multiply(cfg.getWindBowPushPower()));
            hitCount++;
        }

        Messages.debug(shooter, "WIND_BOW: Wind gust pushed " + hitCount + " players, radius: " + radius + ", power: " + cfg.getWindBowPushPower());
        SoundUtils.playAt(impact, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
        ParticleUtils.cloud(impact, 30, 1);
    }

    @Override
    public void cleanup() {
        gustChance.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        gustChance.reset(player.getUniqueId());
    }
}
