package me.psikuvit.cashClash.manager.items.armor;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deathmauler's Outfit - kill healing, absorption after avoiding damage,
 * and a Soul Burst ring when attacking below half health.
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

    public void onPlayerKill(Player killer, GameSession session) {
        if (!hasDeathmaulerSet(killer)) return;

        // Heal 4 hearts (8 HP) through the centralized health system
        CashClashPlayer.heal(killer, 8.0);

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
            CashClashPlayer.applyEffect(p, PotionEffectType.ABSORPTION, 60 * 20, 0);
            Messages.send(p, "armor.deathmauler-absorption");
        }, delaySeconds * 20L);
    }

    public void tryDeathmaulerSoulBurst(Player attacker, Player victim, GameSession session) {
        if (!hasDeathmaulerSet(attacker) || victim == null) return;
        UUID id = attacker.getUniqueId();

        // Use centralized health system for correct max health
        double max = CashClashPlayer.getMaxHealth(attacker);
        if (attacker.getHealth() > max * 0.5) return;

        if (cooldownManager.isOnCooldown(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST)) return;

        if (!isFullyChargedMelee(attacker)) return;

        cooldownManager.setCooldownSeconds(id, CooldownManager.Keys.DEATHMAULER_SOUL_BURST, 35);
        Messages.send(attacker, "armor.soul-burst");
        SoundUtils.play(attacker, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);

        Location center = attacker.getLocation();
        Set<UUID> hitPlayers = ConcurrentHashMap.newKeySet();

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private double radius = 0.5;

            @Override
            public void run() {
                if (!attacker.isOnline() || attacker.isDead()) {
                    cancel();
                    return;
                }

                radius += 0.5;
                if (radius >= 6.0) {
                    cancel();
                    int enemies = hitPlayers.size();
                    Messages.send(attacker, enemies == 1 ? "armor.soul-burst-enemy" : "armor.soul-burst-enemies",
                            "count", String.valueOf(enemies));
                    return;
                }

                ParticleUtils.soulBurstRing(center, radius);

                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(attacker)) continue;
                    if (hitPlayers.contains(target.getUniqueId())) continue;

                    if (session != null) {
                        Team aTeam = session.getPlayerTeam(attacker);
                        Team tTeam = session.getPlayerTeam(target);
                        if (tTeam != null && aTeam == tTeam) continue;
                    }

                    hitPlayers.add(target.getUniqueId());

                    CashClashPlayer.setHealth(target, target.getHealth() - 3.0);
                    CashClashPlayer.heal(attacker, 3.0);

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
