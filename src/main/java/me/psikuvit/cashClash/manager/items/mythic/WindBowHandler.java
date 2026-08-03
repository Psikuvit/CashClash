package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wind Bow - magazine shot system, right-click boost, and wind-gust arrow hits.
 */
public class WindBowHandler extends MythicItemHandler {

    private final Map<UUID, Integer> windBowShotsRemaining;

    public WindBowHandler(MythicItemManager manager) {
        super(manager);
        this.windBowShotsRemaining = new ConcurrentHashMap<>();
    }

    /**
     * Handle Wind Bow shot.
     * 10 shots per magazine, then 30 second reload cooldown.
     * @return true if shot is allowed, false if on cooldown
     */
    public boolean handleWindBowShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if on reload cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_RELOAD)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD);
            Messages.send(player, "mythic.wind-bow-reloading",
                    "remaining", String.valueOf(remaining));
            return false;
        }

        // Get or initialize shots remaining
        int shots = windBowShotsRemaining.getOrDefault(uuid, cfg.getWindBowShotsPerMagazine());

        if (shots <= 0) {
            // Start reload cooldown
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD, cfg.getWindBowReloadCooldown());
            windBowShotsRemaining.put(uuid, cfg.getWindBowShotsPerMagazine());
            Messages.send(player, "mythic.wind-bow-out-of-shots");
            SoundUtils.play(player, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.5f);
            return false;
        }

        // Consume a shot
        shots--;
        windBowShotsRemaining.put(uuid, shots);

        if (shots == 0) {
            // Start reload cooldown
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_RELOAD, cfg.getWindBowReloadCooldown());
            windBowShotsRemaining.put(uuid, cfg.getWindBowShotsPerMagazine());
            Messages.send(player, "mythic.wind-bow-magazine-empty");
            SoundUtils.play(player, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.5f);
        } else if (shots <= 3) {
            Messages.send(player, "mythic.wind-bow-shots-remaining", "{shots}", String.valueOf(shots));
        }

        Messages.debug(player, "WIND_BOW: Shot fired, " + shots + " remaining");
        return true;
    }

    /**
     * Wind Bow right-click boost (while sneaking).
     * Boosts player up and forward.
     * 30 second cooldown.
     */
    public void useWindBowBoost(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "WIND_BOW: Boost ability triggered");

        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.WIND_BOW_BOOST)) {
            Messages.debug(player, "WIND_BOW: Boost on cooldown - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST) + "s");
            Messages.send(player, "mythic.wind-bow-boost-cooldown", "{cooldown_seconds}", String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.WIND_BOW_BOOST)));
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
     * Wind Bow arrow hit - propel target and nearby players backwards.
     * Acts as a wind gust in 3 block radius. Passive ability.
     */
    public void handleWindBowHit(Player shooter, Player target) {
        Location hitLoc = target.getLocation();
        Vector pushDirection = hitLoc.toVector().subtract(shooter.getLocation().toVector()).normalize();
        pushDirection.setY(0.3);

        int hitCount = 0;
        // Push target and all nearby players
        for (Entity entity : target.getWorld().getNearbyEntities(hitLoc, cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius(), cfg.getWindBowPushRadius())) {
            if (!(entity instanceof Player p)) continue;
            if (p.equals(shooter)) continue;

            p.setVelocity(pushDirection.clone().multiply(cfg.getWindBowPushPower()));
            hitCount++;
        }

        Messages.debug(shooter, "WIND_BOW: Wind gust pushed " + hitCount + " players, radius: " + cfg.getWindBowPushRadius() + ", power: " + cfg.getWindBowPushPower());
        SoundUtils.playAt(hitLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 0.8f);
        ParticleUtils.cloud(hitLoc, 30, 1);
    }

    @Override
    public void cleanup() {
        windBowShotsRemaining.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        windBowShotsRemaining.remove(player.getUniqueId());
    }
}
