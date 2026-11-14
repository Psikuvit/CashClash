package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.manager.items.mythic.HudSegment;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.AbilityTargets;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tectonic Cap - negates fall damage and slams the ground, damaging nearby enemies.
 * Two charges with a shared recharge; each charge recharges independently after the
 * configured recharge time. The slam's damage scales with the distance fallen; its range is
 * fixed.
 */
public class TectonicCapHandler extends ArmorSetHandler {

    // Ring density, so a wider configured range still draws a solid ring
    private static final int RING_POINTS_PER_BLOCK = 16;
    private static final int CHARGES = 2;

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
     * While a slam charge is recharging: how many are ready and when the next one is back.
     */
    @Override
    public List<HudSegment> hudSegments(Player player) {
        if (!hasTectonicCap(player)) return List.of();

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long charge1Ms = tectonicCharge1Cooldown.getOrDefault(id, 0L) - now;
        long charge2Ms = tectonicCharge2Cooldown.getOrDefault(id, 0L) - now;
        int ready = (charge1Ms <= 0 ? 1 : 0) + (charge2Ms <= 0 ? 1 : 0);
        if (ready == CHARGES) return List.of();

        long nextMs = charge1Ms > 0 && charge2Ms > 0 ? Math.min(charge1Ms, charge2Ms) : Math.max(charge1Ms, charge2Ms);
        return List.of(new HudSegment(hudText("tectonic-cap",
                "ready", String.valueOf(ready),
                "max", String.valueOf(CHARGES),
                "seconds", String.valueOf(ceilSeconds(nextMs))), false));
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
        if (player.getFallDistance() < cfg.getTectonicCapFallWarningMinDistance()) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !tectonicCharge1Cooldown.containsKey(id) || tectonicCharge1Cooldown.get(id) <= now;
        boolean charge2Ready = !tectonicCharge2Cooldown.containsKey(id) || tectonicCharge2Cooldown.get(id) <= now;
        if (!charge1Ready && !charge2Ready) return;

        Long next = nextFallWarningTick.get(id);
        if (next != null && now < next) return;
        // Throttled a bit more than before (150ms -> 250ms) - the player is falling/moving
        // between draws, so redrawing too often stacks up multiple rings at slightly different
        // centers and reads as a messy smear rather than one clean ring.
        nextFallWarningTick.put(id, now + 250L);
    }

    /**
     * The slam on landing. Damage is {@code base-damage} plus {@code damage-scale-percent} for
     * every {@code damage-scale-blocks} fallen; the outer ring marks the damage range and the
     * inner one the slowness range, both straight from config.
     *
     * @param fallDistance blocks fallen - passed in, since a landing detected outside a game has
     *                     already reset the player's own fall distance
     */
    public void onTectonicCapFall(EntityDamageEvent event, Player player, float fallDistance) {
        if (!hasTectonicCap(player)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charge1Ready = !tectonicCharge1Cooldown.containsKey(id) || tectonicCharge1Cooldown.get(id) <= now;
        boolean charge2Ready = !tectonicCharge2Cooldown.containsKey(id) || tectonicCharge2Cooldown.get(id) <= now;

        if (!charge1Ready && !charge2Ready) return;

        if (isInShoppingPhase(player)) {
            sendShoppingPhaseRestriction(player, "gamestate.cannot-use-custom-armor-shopping");
            return;
        }

        Location impact = player.getLocation().clone().add(0, 0.1, 0);
        Material feet = player.getLocation().getBlock().getType();

        if (feet == Material.WATER || feet == Material.LAVA || feet == Material.COBWEB) {
            return;
        }

        event.setCancelled(true);
        double radius = cfg.getTectonicCapRadius();
        double slowRadius = cfg.getTectonicCapSlowRadius();
        double damage = cfg.getTectonicCapDamage()
                * (1.0 + fallDistance / cfg.getTectonicCapDamageScaleBlocks() * cfg.getTectonicCapDamageScalePercent() / 100.0);

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 15) {
                    cancel();
                    return;
                }
                ParticleUtils.circle(impact, slowRadius, ringPoints(slowRadius), 0, Color.fromRGB(80, 45, 20), 2);
                ParticleUtils.circle(impact, radius, ringPoints(radius), 0, Color.fromRGB(180, 120, 60), 2);
            }
        }, 0L, 2L);

        SoundUtils.playAt(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);

        for (LivingEntity target : AbilityTargets.enemiesNear(player, player.getLocation(), radius, radius, radius)) {
            double distance = horizontalDistance(target.getLocation(), impact);
            if (distance > radius) continue;

            target.damage(damage, player);

            Vector knockback = target.getLocation()
                    .toVector()
                    .subtract(impact.toVector())
                    .normalize()
                    .setY(cfg.getTectonicCapKnockbackUpward())
                    .multiply(cfg.getTectonicCapKnockbackHorizontal());

            target.setVelocity(knockback);
            if (distance <= slowRadius) {
                int slowDurationTicks = cfg.getTectonicCapSlowDurationSeconds() * 20;
                AbilityTargets.applyEffect(target, PotionEffectType.SLOWNESS, slowDurationTicks, cfg.getTectonicCapSlowAmplifier(), false, true);
            }
        }

        long cooldownEnd = System.currentTimeMillis() + (cfg.getTectonicCapRechargeSeconds() * 1000L);
        if (charge1Ready) {
            tectonicCharge1Cooldown.put(id, cooldownEnd);
        } else {
            tectonicCharge2Cooldown.put(id, cooldownEnd);
        }
    }

    private static int ringPoints(double radius) {
        return (int) Math.ceil(radius * RING_POINTS_PER_BLOCK);
    }

    /**
     * Distance on the ground plane, matching the flat rings that show the slam's ranges.
     */
    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Readies both slam charges at once - the buy phase resets every cooldown.
     */
    public void resetCharges(UUID playerUuid) {
        tectonicCharge1Cooldown.remove(playerUuid);
        tectonicCharge2Cooldown.remove(playerUuid);
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
