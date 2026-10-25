package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.items.mythic.HudSegment;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.AbilityTargets;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deathmauler's Outfit - kill healing, absorption after avoiding damage,
 * and a Soul Burst ring when attacking at low health (custom-armor.deathmauler.soul-burst).
 */
public class DeathmaulerSetHandler extends ArmorSetHandler {

    public DeathmaulerSetHandler(CustomArmorManager manager) {
        super(manager);
    }

    public boolean hasDeathmaulerSet(Player player) {
        boolean hasChest = false, hasLegs = false;
        for (CustomArmorItem ca : getEquippedCustomArmor(player)) {
            if (ca == CustomArmorItem.DEATHMAULER_CHESTPLATE) hasChest = true;
            if (ca == CustomArmorItem.DEATHMAULER_LEGGINGS) hasLegs = true;
        }
        return hasChest && hasLegs;
    }

    @Override
    public List<HudSegment> hudSegments(Player player) {
        if (!hasDeathmaulerSet(player)) return List.of();
        List<HudSegment> segments = new ArrayList<>();
        addCooldownSegment(segments, player.getUniqueId(), CooldownManager.Keys.DEATHMAULER_SOUL_BURST, "deathmauler");
        return segments;
    }

    public void onPlayerKill(Player killer, GameSession session) {
        if (!hasDeathmaulerSet(killer)) return;

        CashClashPlayer.heal(killer, cfg.getDeathmaulerKillHealAmount());

        Messages.send(killer, "armor.deathmauler-heal");

        // Dark hearts + smoke pulses rising from the kill
        for (int i = 0; i < 3; i++) {
            SchedulerUtils.runTaskLater(() -> {
                if (!killer.isOnline()) return;
                ParticleUtils.deathmaulerHeal(killer.getLocation());
            }, i * 8L);
            SoundUtils.play(killer, Sound.ENTITY_WITHER_HURT, 0.6f, 0.6f);
        }
    }

    public void onDeathmaulerDamageTaken(Player p) {
        if (!hasDeathmaulerSet(p)) return;
        UUID id = p.getUniqueId();
        cooldownManager.setTimestamp(id, CooldownManager.Keys.DEATHMAULER_DAMAGE);

        int delaySeconds = cfg.getDeathmaulerAbsorptionDelay();
        // Schedule absorption check after configured delay without damage
        SchedulerUtils.runTaskLater(() -> {
            if (!cooldownManager.hasTimePassedSeconds(id, CooldownManager.Keys.DEATHMAULER_DAMAGE, delaySeconds)) {
                return;
            }
            CashClashPlayer.applyEffect(p, PotionEffectType.ABSORPTION,
                    cfg.getDeathmaulerAbsorptionDurationSeconds() * 20, cfg.getDeathmaulerAbsorptionLevel());
            Messages.send(p, "armor.deathmauler-absorption");
        }, delaySeconds * 20L);
    }

    public void tryDeathmaulerSoulBurst(Player attacker, LivingEntity victim) {
        if (!hasDeathmaulerSet(attacker) || victim == null) return;
        UUID id = attacker.getUniqueId();

        // Use centralized health system for correct max health
        double max = CashClashPlayer.getMaxHealth(attacker);
        if (attacker.getHealth() > max * cfg.getDeathmaulerSoulBurstLowHealthPercent() / 100.0) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST)) return;

        if (!isFullyChargedMelee(attacker)) return;

        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST, cfg.getDeathmaulerSoulBurstCooldown());
        Messages.send(attacker, "armor.soul-burst");
        SoundUtils.play(attacker, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);

        Location center = attacker.getLocation();
        Set<UUID> hitPlayers = ConcurrentHashMap.newKeySet();
        double maxRadius = cfg.getDeathmaulerSoulBurstRadius();
        double drain = cfg.getDeathmaulerSoulBurstDrainPerEnemy();
        double heal = cfg.getDeathmaulerSoulBurstHealPerEnemy();

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private double radius = 0.5;

            @Override
            public void run() {
                if (!attacker.isOnline() || attacker.isDead()) {
                    cancel();
                    return;
                }

                radius += 0.5;
                if (radius > maxRadius) {
                    cancel();
                    int enemies = hitPlayers.size();
                    Messages.send(attacker, enemies == 1 ? "armor.soul-burst-enemy" : "armor.soul-burst-enemies",
                            "count", String.valueOf(enemies));
                    return;
                }

                ParticleUtils.soulBurstRing(center, radius);

                for (LivingEntity target : AbilityTargets.enemiesNear(attacker, center, radius, radius, radius)) {
                    if (hitPlayers.contains(target.getUniqueId())) continue;

                    hitPlayers.add(target.getUniqueId());

                    if (target instanceof Player victimPlayer) {
                        CashClashPlayer.setHealth(victimPlayer, victimPlayer.getHealth() - drain);
                    } else {
                        target.setHealth(Math.max(0.0, target.getHealth() - drain));
                    }
                    CashClashPlayer.heal(attacker, heal);

                    ParticleUtils.hitFeedback(target.getLocation(), 10, 0.2);
                }
            }
        }, 0L, 2L);
    }

    @Override
    public void cleanup() {
        // No persistent state
    }

    @Override
    public void resetRoundTracking() {
        // No persistent state
    }
}
