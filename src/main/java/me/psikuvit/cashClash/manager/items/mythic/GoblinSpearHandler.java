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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Goblin Spear - throwable trident with a magazine/reload system and a dash
 * charge ability that catches and drags enemies.
 */
public class GoblinSpearHandler extends MythicItemHandler {

    private final Map<UUID, Integer> goblinSpearShotsRemaining;

    // Goblin Spear charge state tracking (player -> list of caught players)
    private final Map<UUID, List<Player>> goblinSpearCharging;

    public GoblinSpearHandler(MythicItemManager manager) {
        super(manager);
        this.goblinSpearShotsRemaining = new ConcurrentHashMap<>();
        this.goblinSpearCharging = new ConcurrentHashMap<>();
    }

    /**
     * Handle Goblin Spear throw.
     * 8 shots per magazine, 15 second reload.
     * @return true if shot was successful, false if on reload cooldown
     */
    public boolean handleGoblinSpearThrow(Player player) {
        UUID uuid = player.getUniqueId();

        Messages.debug(player, "GOBLIN_SPEAR: Throw triggered");

        int shots = goblinSpearShotsRemaining.getOrDefault(uuid, cfg.getGoblinShotsPerMag());
        if (shots <= 0) {
            if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD)) {
                Messages.debug(player, "GOBLIN_SPEAR: Reloading - " + cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD) + "s");
                Messages.send(player, "mythic.goblin-spear-reloading", "{cooldown_seconds}", String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD)));
                return false;
            }
            goblinSpearShotsRemaining.put(uuid, cfg.getGoblinShotsPerMag());
            shots = cfg.getGoblinShotsPerMag();
            Messages.debug(player, "GOBLIN_SPEAR: Magazine reloaded to " + shots);
        }

        goblinSpearShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "GOBLIN_SPEAR: Shot fired! Remaining: " + (shots - 1));

        if (shots - 1 <= 0) {
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_RELOAD, cfg.getGoblinReloadCooldown());
            Messages.debug(player, "GOBLIN_SPEAR: Out of shots, reloading for " + cfg.getGoblinReloadCooldown() + "s");
            Messages.send(player, "mythic.goblin-spear-reload-start");
        }

        return true;
    }

    /**
     * Handle Goblin Spear hit.
     * Deals damage + Poison.
     * @param shooter The attacker
     * @param victim The victim
     * @param isMelee Whether this is a melee hit (prevents double damage)
     */
    public void handleGoblinSpearHit(Player shooter, LivingEntity victim, boolean isMelee) {
        Messages.debug(shooter, "GOBLIN_SPEAR: Hit " + victim.getName() + " (Melee: " + isMelee + ")");

        if (!isMelee) {
            victim.damage(cfg.getGoblinSpearDamage(), shooter);
        }

        if (victim instanceof Player victimPlayer) {
            CashClashPlayer.applyEffect(victimPlayer, PotionEffectType.POISON, cfg.getGoblinPoisonDuration(), cfg.getGoblinPoisonLevel(), false, true);
        } else {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, cfg.getGoblinPoisonDuration(), cfg.getGoblinPoisonLevel(), false, true));
        }

        if (!isMelee) {
            Messages.debug(shooter, "GOBLIN_SPEAR: Dealt " + cfg.getGoblinSpearDamage() + " damage + Poison " + (cfg.getGoblinPoisonLevel() + 1));
        } else {
            Messages.debug(shooter, "GOBLIN_SPEAR: Applied Poison " + (cfg.getGoblinPoisonLevel() + 1));
        }

        ParticleUtils.slime(victim.getLocation().add(0, 1, 0), 20, 0.5);
    }

    /**
     * Start Goblin Spear charge ability.
     * Player charges forward, catching enemies and dealing damage + poison when hitting a wall.
     */
    public void startGoblinSpearCharge(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove respawn protection when starting charge
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) {
                ccp.setRespawnProtection(0L);
            }
        }

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE);
            Messages.send(player, "mythic.charge-cooldown", "{remaining}", String.valueOf(remaining));
            return;
        }

        // Check if already charging
        if (goblinSpearCharging.containsKey(uuid)) {
            return;
        }

        Messages.debug(player, "GOBLIN_SPEAR: Charge started!");
        Messages.send(player, "mythic.charge-activated");
        SoundUtils.play(player, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.5f);

        // Initialize caught players list
        goblinSpearCharging.put(uuid, new ArrayList<>());

        // Get session for team checking
        Team playerTeam = session != null ? session.getPlayerTeam(player) : null;

        // Get charge direction
        Vector chargeDirection = player.getLocation().getDirection().setY(0).normalize();
        double chargeSpeed = cfg.getGoblinChargeSpeed();
        int maxDuration = cfg.getGoblinChargeMaxDuration();

        // Start charge runnable
        BukkitRunnable chargeRunnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxDuration) {
                    endCharge(player, false);
                    cancel();
                    return;
                }

                // Move player forward
                Vector velocity = chargeDirection.clone().multiply(chargeSpeed);
                velocity.setY(player.getVelocity().getY()); // Preserve Y velocity
                player.setVelocity(velocity);

                // Check for wall collision
                Location checkLoc = player.getLocation().add(chargeDirection.clone().multiply(0.5));
                if (checkLoc.getBlock().getType().isSolid()) {
                    endCharge(player, true);
                    cancel();
                    return;
                }

                // Check for nearby enemies to catch
                List<Player> caughtPlayers = goblinSpearCharging.get(uuid);
                for (Entity entity : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(player)) continue;
                    if (caughtPlayers.contains(target)) continue;

                    // Team check
                    if (session != null && playerTeam != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && targetTeam.getTeamNumber() == playerTeam.getTeamNumber()) {
                            continue;
                        }
                    }

                    // Catch the player
                    caughtPlayers.add(target);
                    Messages.debug(player, "GOBLIN_SPEAR: Caught " + target.getName());
                    SoundUtils.play(target, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                }

                // Drag caught players along
                Location dragLoc = player.getLocation().add(0, 0.5, 0);
                for (Player caught : caughtPlayers) {
                    if (caught.isOnline()) {
                        caught.teleport(dragLoc);
                    }
                }

                // Spawn particles
                ParticleUtils.crit(player.getLocation().add(0, 1, 0), 5, 0.3);

                ticks++;
            }
        };
        BukkitTask chargeTask = SchedulerUtils.runTaskTimer(chargeRunnable, 0L, 1L);

        // Track the task
        manager.trackTask(uuid, chargeTask);
    }

    /**
     * End Goblin Spear charge, applying damage if hit wall.
     */
    private void endCharge(Player player, boolean hitWall) {
        UUID uuid = player.getUniqueId();
        List<Player> caughtPlayers = goblinSpearCharging.remove(uuid);

        if (caughtPlayers == null || caughtPlayers.isEmpty()) {
            Messages.debug(player, "GOBLIN_SPEAR: Charge ended, no players caught");
        } else if (hitWall) {
            // Deal damage and poison to all caught players
            double damage = cfg.getGoblinChargeWallDamage();
            int poisonDuration = cfg.getGoblinChargePoisonDuration();
            int poisonLevel = cfg.getGoblinChargePoisonLevel();

            for (Player caught : caughtPlayers) {
                if (!caught.isOnline()) continue;

                caught.setNoDamageTicks(0);
                player.setNoDamageTicks(0);
                caught.setMaximumNoDamageTicks(0); // Ensure they can be damaged immediately
                player.setMaximumNoDamageTicks(0);
                caught.damage(damage, player);
                CashClashPlayer.applyEffect(caught, PotionEffectType.POISON, poisonDuration, poisonLevel, false, true);

                // Reset to default after damage is applied (vanilla is 20)
                SchedulerUtils.runTaskLater(() -> {
                    if (caught.isOnline()) caught.setMaximumNoDamageTicks(20);
                    if (player.isOnline()) player.setMaximumNoDamageTicks(20);
                }, 1L);

                // Visual effects
                ParticleUtils.damageIndicator(caught.getLocation().add(0, 1, 0), 20, 0.5);
                SoundUtils.play(caught, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);

                Messages.debug(player, "GOBLIN_SPEAR: Wall impact dealt " + damage + " damage + Poison to " + caught.getName());
            }

            Messages.send(player, "mythic.wall-impact", "{damage}", String.valueOf((int) damage), "{enemy_count}", String.valueOf(caughtPlayers.size()));
            SoundUtils.play(player, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
        } else {
            Messages.debug(player, "GOBLIN_SPEAR: Charge ended without wall impact");
        }

        // Set cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.GOBLIN_SPEAR_CHARGE, cfg.getGoblinChargeCooldown());
        Messages.debug(player, "GOBLIN_SPEAR: Charge cooldown set to " + cfg.getGoblinChargeCooldown() + "s");

        // Stop the player
        player.setVelocity(new Vector(0, 0, 0));
    }

    /**
     * Check if player is currently charging with Goblin Spear.
     */
    public boolean isGoblinSpearCharging(UUID playerId) {
        if (goblinSpearCharging.containsKey(playerId)) {
            return true;
        }
        for (List<Player> caught : goblinSpearCharging.values()) {
            for (Player p : caught) {
                if (p.getUniqueId().equals(playerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a player is caught in another player's Goblin Spear charge.
     */
    public boolean isCaughtInGoblinCharge(UUID playerId) {
        for (List<Player> caught : goblinSpearCharging.values()) {
            for (Player p : caught) {
                if (p.getUniqueId().equals(playerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the charger who caught the given victim.
     */
    public UUID getGoblinChargerOf(UUID victimId) {
        for (Map.Entry<UUID, List<Player>> entry : goblinSpearCharging.entrySet()) {
            for (Player p : entry.getValue()) {
                if (p.getUniqueId().equals(victimId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void cleanup() {
        goblinSpearShotsRemaining.clear();
        goblinSpearCharging.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        goblinSpearShotsRemaining.remove(uuid);

        // Remove from caught lists
        for (Map.Entry<UUID, List<Player>> entry : goblinSpearCharging.entrySet()) {
            entry.getValue().removeIf(p -> p.getUniqueId().equals(uuid));
        }

        // End charge if active
        if (goblinSpearCharging.containsKey(uuid)) {
            endCharge(player, false);
        }
    }
}
