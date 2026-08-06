package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tectonic Cap - negates fall damage and slams the ground, damaging nearby enemies.
 * Two charges with a shared recharge; each charge recharges independently after the
 * configured recharge time. The slam radius scales with the negated fall damage.
 */
public class TectonicCapHandler extends ArmorSetHandler {

    // Ring radii for both the falling-warning aura and the slam impact burst
    private static final double INNER_RING_RADIUS = 2.0;
    private static final double OUTER_RING_RADIUS = 4.0;

    private final Map<UUID, Long> tectonicCharge1Cooldown;
    private final Map<UUID, Long> tectonicCharge2Cooldown;
    private final Map<UUID, Long> nextFallWarningTick; // throttle for the falling warning aura

    public TectonicCapHandler(CustomArmorManager manager) {
        super(manager);
        this.tectonicCharge1Cooldown = new ConcurrentHashMap<>();
        this.tectonicCharge2Cooldown = new ConcurrentHashMap<>();
        this.nextFallWarningTick = new ConcurrentHashMap<>();
    }

    public boolean hasTectonicCap(Player player) {
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.TECTONIC_CAP) return true;
        }
        return false;
    }

    /**
     * Falling warning aura: while airborne, falling far enough that a landing would deal
     * fall damage, and with at least one slam charge ready, ring the wearer's feet with an
     * inner dark-brown ring (radius 2) and an outer light-brown ring (radius 4) to telegraph
     * the impending slam.
     */
    public void handleFallWarning(Player player) {
        if (!hasTectonicCap(player)) return;
        if (player.isOnGround() || player.isFlying()) return;
        if (player.getVelocity().getY() >= 0) return;
        if (player.getFallDistance() < 3.0f) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !tectonicCharge1Cooldown.containsKey(id) || tectonicCharge1Cooldown.get(id) <= now;
        boolean charge2Ready = !tectonicCharge2Cooldown.containsKey(id) || tectonicCharge2Cooldown.get(id) <= now;
        if (!charge1Ready && !charge2Ready) return;

        Long next = nextFallWarningTick.get(id);
        if (next != null && now < next) return;
        nextFallWarningTick.put(id, now + 150L);

        Location center = player.getLocation().clone().add(0, 0.1, 0);

        for (int i = 0; i < 10; i++) {
            double angle = 2 * Math.PI * i / 10;
            double x = Math.cos(angle) * INNER_RING_RADIUS;
            double z = Math.sin(angle) * INNER_RING_RADIUS;
            ParticleUtils.spawnDust(center.clone().add(x, 0, z), Color.fromRGB(80, 45, 20), 1.0f, 1);
        }

        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            double x = Math.cos(angle) * OUTER_RING_RADIUS;
            double z = Math.sin(angle) * OUTER_RING_RADIUS;
            ParticleUtils.spawnDust(center.clone().add(x, 0, z), Color.fromRGB(180, 120, 60), 1.0f, 1);
        }
    }

    public void onTectonicCapFall(EntityDamageEvent event, Player player) {
        if (!hasTectonicCap(player)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !tectonicCharge1Cooldown.containsKey(id) || tectonicCharge1Cooldown.get(id) <= now;
        boolean charge2Ready = !tectonicCharge2Cooldown.containsKey(id) || tectonicCharge2Cooldown.get(id) <= now;

        if (!charge1Ready && !charge2Ready) return;

        Location impact = player.getLocation().clone().add(0, 0.1, 0);
        Material feet = player.getLocation().getBlock().getType();

        if (feet == Material.WATER || feet == Material.LAVA || feet == Material.COBWEB) {
            return;
        }

        double fallDamage = event.getFinalDamage();
        event.setCancelled(true);
        double radius = cfg.getTectonicCapRadius() + (fallDamage * 0.3);
        World world = player.getWorld();

        ParticleUtils.spawnDust(impact, Color.fromRGB(180, 120, 60), 2.5f, 75, OUTER_RING_RADIUS, 0.1, OUTER_RING_RADIUS);
        ParticleUtils.spawnDust(impact, Color.fromRGB(80, 45, 20), 2.5f, 60, INNER_RING_RADIUS, 0.1, INNER_RING_RADIUS);

        SoundUtils.playAt(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(player)) continue;

            target.damage(cfg.getTectonicCapDamage() + fallDamage, player);

            Vector knockback = target.getLocation()
                    .toVector()
                    .subtract(impact.toVector())
                    .normalize()
                    .setY(0.25)
                    .multiply(0.7);

            target.setVelocity(knockback);
            if (target.getLocation().distance(impact) <= 2.0) {
                CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, 20 * 4, 1, false, true, true);
            }
        }

        long cooldownEnd = System.currentTimeMillis() + (cfg.getTectonicCapRechargeSeconds() * 1000L);
        if (charge1Ready) {
            tectonicCharge1Cooldown.put(id, cooldownEnd);
        } else {
            tectonicCharge2Cooldown.put(id, cooldownEnd);
        }
    }

    @Override
    public void cleanup() {
        tectonicCharge1Cooldown.clear();
        tectonicCharge2Cooldown.clear();
        nextFallWarningTick.clear();
    }

    @Override
    public void resetRoundTracking() {
        // Tectonic charges persist across rounds
    }
}
