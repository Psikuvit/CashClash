package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.custom.CustomItemManager;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BloodWrench Crossbow - dual-mode crossbow (Rapid Fire vs Supercharged) with
 * lingering blood spheres and a blood vortex.
 */
public class BloodwrenchHandler extends MythicItemHandler {

    // BloodWrench mode tracking (true = rapid fire, false = supercharged)
    private final Map<UUID, Boolean> bloodwrenchRapidMode;

    // BloodWrench rapid fire shots remaining (must fire all 3 before switching)
    private final Map<UUID, Integer> bloodwrenchRapidShotsRemaining;

    // BloodWrench rapid fire in progress (cannot switch modes while firing)
    private final Set<UUID> bloodwrenchRapidFiring;

    public BloodwrenchHandler(MythicItemManager manager) {
        super(manager);
        this.bloodwrenchRapidMode = new ConcurrentHashMap<>();
        this.bloodwrenchRapidShotsRemaining = new ConcurrentHashMap<>();
        this.bloodwrenchRapidFiring = ConcurrentHashMap.newKeySet();
    }

    /**
     * Toggle BloodWrench mode between Rapid Fire and Supercharged.
     * Cannot switch modes while rapid firing or on cooldown.
     * 1 second cooldown between toggles.
     */
    public void toggleBloodwrenchMode(Player player) {
        UUID uuid = player.getUniqueId();

        // Cannot switch while in rapid fire burst
        if (bloodwrenchRapidFiring.contains(uuid)) {
            Messages.send(player, "mythic.cannot-switch-modes");
            return;
        }

        // Check toggle cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE)) {
            Messages.send(player, "mythic.mode-switch-cooldown", "{remaining}", String.valueOf(cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE)));
            return;
        }

        // Toggle mode (default is rapid mode = true)
        boolean currentRapid = bloodwrenchRapidMode.getOrDefault(uuid, true);
        boolean newRapid = !currentRapid;
        bloodwrenchRapidMode.put(uuid, newRapid);

        // Set toggle cooldown
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_MODE_TOGGLE, cfg.getBloodwrenchModeToggleCooldown());

        String modeName = newRapid ? "Rapid Fire" : "Supercharged";
        Messages.send(player, "mythic.bloodwrench-mode", "{mode_name}", modeName);
        SoundUtils.play(player, Sound.BLOCK_LEVER_CLICK, 1.0f, newRapid ? 1.5f : 0.8f);
        Messages.debug(player, "BLOODWRENCH: Switched to " + (newRapid ? "Rapid Fire" : "Supercharged") + " mode");
    }

    /**
     * Check if BloodWrench is in Rapid Fire mode.
     */
    public boolean isBloodwrenchRapidMode(Player player) {
        return bloodwrenchRapidMode.getOrDefault(player.getUniqueId(), true);
    }

    /**
     * Handle BloodWrench shot based on current mode.
     */
    public boolean handleBloodwrenchShot(Player player) {
        boolean isRapid = isBloodwrenchRapidMode(player);

        Messages.debug(player, "BLOODWRENCH: Shot triggered (" + (isRapid ? "Rapid" : "Supercharged") + " mode)");

        if (isRapid) {
            return handleBloodwrenchRapidShot(player);
        } else {
            return handleBloodwrenchSuperchargedShot(player);
        }
    }

    /**
     * Handle Rapid Fire mode shot.
     * Player fires 3 blood shots. Once started, must complete all 3 before switching modes.
     * After 3 shots, cooldown begins.
     */
    private boolean handleBloodwrenchRapidShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if on reload cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD);
            Messages.send(player, "mythic.bloodwrench-reloading", "{remaining}", String.valueOf(remaining));
            Messages.debug(player, "BLOODWRENCH: Rapid reloading - " + remaining + "s");
            return false;
        }

        int shots = bloodwrenchRapidShotsRemaining.getOrDefault(uuid, cfg.getBloodwrenchRapidShots());

        // First shot starts the burst
        if (shots == cfg.getBloodwrenchRapidShots()) {
            bloodwrenchRapidFiring.add(uuid);
            Messages.debug(player, "BLOODWRENCH: Rapid fire burst started");
        }

        // Fire the shot
        bloodwrenchRapidShotsRemaining.put(uuid, shots - 1);
        Messages.debug(player, "BLOODWRENCH: Rapid shot fired! Remaining: " + (shots - 1));

        // Check if burst complete
        if (shots - 1 <= 0) {
            bloodwrenchRapidFiring.remove(uuid);
            bloodwrenchRapidShotsRemaining.remove(uuid);
            cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_RAPID_RELOAD, cfg.getBloodwrenchRapidReloadCooldown());
            Messages.send(player, "mythic.bloodwrench-reload-start");
            Messages.debug(player, "BLOODWRENCH: Rapid burst complete, reloading for " + cfg.getBloodwrenchRapidReloadCooldown() + "s");
        }

        return true;
    }

    /**
     * Handle Supercharged mode shot.
     * Single powerful shot creating a blood vortex.
     */
    private boolean handleBloodwrenchSuperchargedShot(Player player) {
        UUID uuid = player.getUniqueId();

        // Check cooldown
        if (cooldownManager.isOnCooldown(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN)) {
            long remaining = cooldownManager.getRemainingCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN);
            Messages.send(player, "mythic.bloodwrench-supercharged-cooldown", "{remaining}", String.valueOf(remaining));
            Messages.debug(player, "BLOODWRENCH: Supercharged on cooldown - " + remaining + "s");
            return false;
        }

        Messages.debug(player, "BLOODWRENCH: Supercharged shot fired!");
        cooldownManager.setCooldownSeconds(uuid, CooldownManager.Keys.BLOODWRENCH_SUPERCHARGE_COOLDOWN, cfg.getBloodwrenchSuperchargeCooldown());
        return true;
    }

    /**
     * Handle BloodWrench Rapid Fire hit - creates blood sphere.
     * Blood sphere gives Slowness I when inside and burst damage (nerfed grenade).
     */
    public void handleBloodwrenchRapidHit(Player shooter, Location hitLocation) {
        World world = hitLocation.getWorld();
        if (world == null) return;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(shooter);
        Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;

        Messages.debug(shooter, "BLOODWRENCH: Rapid hit - creating blood sphere at " + hitLocation);

        // Visual blood sphere
        double radius = cfg.getBloodwrenchSphereRadius();
        ParticleUtils.bloodSphere(hitLocation, radius, 50);
        SoundUtils.playAt(hitLocation, Sound.BLOCK_SLIME_BLOCK_BREAK, 1.0f, 0.5f);

        // Create blood sphere effect that lingers
        int durationTicks = cfg.getBloodwrenchSphereDuration();
        double damage = cfg.getBloodwrenchSphereDamage();

        // Initial burst damage (nerfed grenade - smaller radius, less damage)
        for (Entity entity : world.getNearbyEntities(hitLocation, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(shooter)) continue;

            if (session != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && shooterTeam != null &&
                    targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
            }

            target.damage(damage, shooter);
            Messages.debug(shooter, "BLOODWRENCH: Blood sphere damaged " + target.getName() + " for " + damage);
        }

        // Lingering sphere effect
        final double sphereRadius = radius;
        int healNegationDuration = cfg.getBloodwrenchHealNegationDuration();
        BukkitTask sphereTask = SchedulerUtils.runTaskTimer(() -> {
            // Particle effect
            ParticleUtils.bloodSphereLingering(hitLocation, sphereRadius);

            // Apply slowness + heal negation to enemies inside (everyone inside the sphere,
            // not just the shooter's opponents - the sphere doesn't distinguish team once it
            // has landed, matching its "negates healing while inside" description)
            for (Entity entity : world.getNearbyEntities(hitLocation, sphereRadius, sphereRadius, sphereRadius)) {
                if (!(entity instanceof Player target)) continue;

                // Refreshed every tick while inside - naturally decays `healNegationDuration`
                // seconds after the target leaves the sphere since nothing refreshes it anymore.
                CashClashPlugin.getInstance().getCustomItemManager().applyHealingReduction(target.getUniqueId(), 0.0, healNegationDuration);

                if (target.equals(shooter)) continue;
                if (session != null) {
                    Team targetTeam = session.getPlayerTeam(target);
                    if (targetTeam != null && shooterTeam != null &&
                        targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                }

                // Slowness I while inside sphere
                CashClashPlayer.applyEffect(target, PotionEffectType.SLOWNESS, 40, 0);
            }
        }, 0L, 10L);

        // Cancel after duration
        SchedulerUtils.runTaskLater(() -> {
            Objects.requireNonNull(sphereTask).cancel();
            Messages.debug(shooter, "BLOODWRENCH: Blood sphere expired");
        }, durationTicks);

        manager.trackTask(shooter.getUniqueId(), sphereTask);
    }

    /**
     * Handle BloodWrench Supercharged hit - creates blood vortex.
     * Vortex has red particles, gives Levitation and deals damage to enemies inside.
     */
    public void handleBloodwrenchSuperchargedHit(Player shooter, Location hitLocation) {
        World world = hitLocation.getWorld();
        if (world == null) return;

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(shooter);
        Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;

        Messages.debug(shooter, "BLOODWRENCH: Supercharged hit - creating blood vortex at " + hitLocation);
        Messages.send(shooter, "mythic.blood-vortex-activated");

        SoundUtils.playAt(hitLocation, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
        SoundUtils.playAt(hitLocation, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.2f);

        int durationTicks = cfg.getBloodwrenchVortexDuration();
        double radius = cfg.getBloodwrenchVortexRadius();
        double damagePerTick = cfg.getBloodwrenchVortexDamage();
        int healNegationDuration = cfg.getBloodwrenchHealNegationDuration();
        double selfHealPercent = cfg.getBloodwrenchVortexSelfHealPercent() / 100.0;

        // Vortex effect with spiraling particles
        BukkitTask vortexTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;

                // Spiraling red particles using helper method
                ParticleUtils.bloodVortexSpiral(hitLocation, radius, tick);

                // Heal-negation zone: refreshed every tick while inside, regardless of team -
                // decays naturally `healNegationDuration` seconds after a player leaves.
                for (Entity entity : world.getNearbyEntities(hitLocation, radius, radius + 2, radius)) {
                    if (entity instanceof Player inside) {
                        CashClashPlugin.getInstance().getCustomItemManager().applyHealingReduction(inside.getUniqueId(), 0.0, healNegationDuration);
                    }
                }

                // Apply effects every 10 ticks (0.5 seconds)
                if (tick % 10 == 0) {
                double totalDamageDealt = 0.0;
                for (Entity entity : world.getNearbyEntities(hitLocation, radius, radius + 2, radius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(shooter)) continue;

                    if (session != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && shooterTeam != null &&
                            targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                    }

                    // Levitation and damage while inside vortex
                    CashClashPlayer.applyEffect(target, PotionEffectType.LEVITATION, 30, cfg.getBloodwrenchVortexLevitationLevel() - 1);
                    target.damage(damagePerTick, shooter);
                    totalDamageDealt += damagePerTick;
                    Messages.debug(shooter, "BLOODWRENCH: Vortex affecting " + target.getName() + " for " + damagePerTick + " damage");
                }

                // Wielder heals for a percentage of the damage the vortex dealt this batch -
                // reduced by their own active healing-reduction debuff (e.g. Soul Katana).
                if (totalDamageDealt > 0) {
                    CashClashPlayer.heal(shooter, totalDamageDealt * selfHealPercent);
                }
                }
            }
        }, 0L, 2L);

        // Cancel after duration
        SchedulerUtils.runTaskLater(() -> {
            Objects.requireNonNull(vortexTask).cancel();
            Messages.debug(shooter, "BLOODWRENCH: Blood vortex expired");
            // Final burst effect
            ParticleUtils.bloodVortexExplosion(hitLocation, radius);
            SoundUtils.playAt(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        }, durationTicks);

        manager.trackTask(shooter.getUniqueId(), vortexTask);
    }

    /**
     * Check if player is currently in rapid fire burst (cannot switch modes).
     */
    public boolean isBloodwrenchRapidFiring(UUID playerId) {
        return bloodwrenchRapidFiring.contains(playerId);
    }

    @Override
    public void cleanup() {
        bloodwrenchRapidMode.clear();
        bloodwrenchRapidShotsRemaining.clear();
        bloodwrenchRapidFiring.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        bloodwrenchRapidMode.remove(uuid);
        bloodwrenchRapidShotsRemaining.remove(uuid);
        bloodwrenchRapidFiring.remove(uuid);
    }
}
