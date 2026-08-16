package me.psikuvit.cashClash.manager.items.custom;

import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles both grenade variants: the damaging grenade and the smoke grenade.
 * Both are thrown as a FIRE_CHARGE item, which becomes the tracking handle so
 * the explosion fires at the exact spot it stopped, and the set of live items
 * is torn down on game end.
 */
public class GrenadeHandler extends CustomItemHandler {

    private final Set<Item> activeGrenades;

    public GrenadeHandler(CustomItemManager manager) {
        super(manager);
        this.activeGrenades = new HashSet<>();
    }

    public void throwGrenade(Player player, ItemStack item, boolean isSmoke) {
        consumeItem(player, item);

        Item thrownItem = player.getWorld().dropItem(
                player.getEyeLocation(),
                new ItemStack(Material.FIRE_CHARGE) // Both grenades use FIRE_CHARGE for resource pack compatibility
        );
        thrownItem.setVelocity(player.getLocation().getDirection().multiply(cfg.getGrenadeThrowVelocity()));
        thrownItem.setPickupDelay(Integer.MAX_VALUE);
        activeGrenades.add(thrownItem);

        SoundUtils.play(player, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);

        int fuseSeconds = isSmoke ? cfg.getSmokeGrenadeFuseSeconds() : cfg.getGrenadeFuseSeconds();
        SchedulerUtils.runTaskLater(() -> {
            if (!thrownItem.isValid()) return;
            activeGrenades.remove(thrownItem);

            Location loc = thrownItem.getLocation();
            thrownItem.remove();

            if (isSmoke) {
                explodeSmokeGrenade(loc);
            } else {
                explodeGrenade(loc);
            }
        }, fuseSeconds * 20L);
    }

    private void explodeGrenade(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        ParticleUtils.explosion(loc);
        SoundUtils.playAt(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        double innerRadius = cfg.getGrenadeInnerRadius();
        double outerRadius = cfg.getGrenadeOuterRadius();

        for (Entity entity : world.getNearbyEntities(loc, outerRadius, outerRadius, outerRadius)) {
            if (!(entity instanceof Player target)) continue;

            double distance = target.getLocation().distance(loc);
            double damage;

            if (distance <= innerRadius) {
                damage = cfg.getGrenadeInnerDamage();
            } else if (distance <= outerRadius) {
                damage = cfg.getGrenadeOuterDamage();
            } else {
                continue;
            }

            target.damage(damage);
            SoundUtils.play(target, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        }
    }

    private void explodeSmokeGrenade(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        SoundUtils.playAt(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);

        double radius = cfg.getSmokeGrenadeRadius();
        int poisonLevel = cfg.getSmokeGrenadePoisonLevel();
        int poisonDurationTicks = cfg.getSmokeGrenadePoisonDurationSeconds() * 20;
        int blindnessDurationTicks = cfg.getSmokeGrenadeBlindnessDurationSeconds() * 20;

        BukkitTask cloudTask = SchedulerUtils.runTaskTimer(() -> {
            ParticleUtils.campfireSmoke(loc, 20, 2.5, 1, 2.5);

            for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
                if (!(entity instanceof Player target)) continue;

                CashClashPlayer.applyEffect(target, PotionEffectType.POISON, poisonDurationTicks, poisonLevel, false, true);
                CashClashPlayer.applyEffect(target, PotionEffectType.BLINDNESS, blindnessDurationTicks, 0, false, true);
            }
        }, 0L, 20L);

        SchedulerUtils.runTaskLater(() -> {
            if (cloudTask != null) {
                cloudTask.cancel();
            }
        }, cfg.getSmokeGrenadeCloudDurationSeconds() * 20L);
    }

    @Override
    public void cleanup() {
        activeGrenades.forEach(Item::remove);
        activeGrenades.clear();
    }
}
