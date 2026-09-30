package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.CooldownManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlazeBite Crossbows - each player's shots alternate between Glacier (frostbite/freeze) and
 * Volcano (Magma Storm fire explosion) arrows.
 */
public class BlazebiteHandler extends MythicItemHandler {

    public static final String GLACIER_MODE = "glacier";
    public static final String VOLCANO_MODE = "volcano";

    // Whether each player's last shot was a Volcano arrow (absent = next shot is Glacier)
    private final Map<UUID, Boolean> lastShotVolcano;

    // BlazeBite Glacier frozen players tracking (UUID -> expiration timestamp)
    private final Map<UUID, Long> glacierFrozenPlayers;

    // Glacier frostbite particle tasks (UUID -> particle task)
    private final Map<UUID, BukkitTask> glacierFrostbiteParticleTasks;

    public BlazebiteHandler(MythicItemManager manager) {
        super(manager);
        this.lastShotVolcano = new ConcurrentHashMap<>();
        this.glacierFrozenPlayers = new ConcurrentHashMap<>();
        this.glacierFrostbiteParticleTasks = new ConcurrentHashMap<>();
    }

    /**
     * The mode of the player's next shot - Glacier and Volcano take turns, starting with Glacier.
     */
    public String nextShotMode(Player player) {
        boolean volcano = !lastShotVolcano.getOrDefault(player.getUniqueId(), true);
        lastShotVolcano.put(player.getUniqueId(), volcano);
        return volcano ? VOLCANO_MODE : GLACIER_MODE;
    }

    /**
     * Handle BlazeBite hit effects for the mode the arrow was shot with, wherever it lands. A
     * Glacier arrow frostbites/freezes the player it hits. A Volcano arrow sets off a Magma Storm
     * - a fire/explosion AOE that also cleanses the freezing effect off any frozen players caught
     * in the blast, rather than applying freeze itself.
     */
    public void handleBlazebiteHit(Player shooter, Entity hitEntity, Location hitLoc, String mode) {
        World world = hitLoc.getWorld();
        if (world == null) return;

        Messages.debug(shooter, "BLAZEBITE: " + mode + " arrow hit");

        if (VOLCANO_MODE.equals(mode)) {
            handleMagmaStorm(shooter, hitEntity, hitLoc, world);
        } else {
            handleGlacier(shooter, hitEntity);
        }
    }

    /**
     * Glacier mode: first hit applies frostbite for 5 seconds. Second hit while frostbitten
     * freezes the player in place for 3 seconds and locks the shooter out of firing again for
     * a few seconds.
     */
    private void handleGlacier(Player shooter, Entity hitEntity) {
        {
            if (hitEntity instanceof Player victim) {
                UUID victimId = victim.getUniqueId();
                long currentTime = System.currentTimeMillis();

                // Check if player is already frozen (hit while frozen)
                boolean alreadyFrozen = glacierFrozenPlayers.containsKey(victimId)
                        && glacierFrozenPlayers.get(victimId) > currentTime;

                if (alreadyFrozen) {
                    // FREEZE IN PLACE - Apply max slowness (level 255 = completely frozen) for 3 seconds
                    int freezeInPlaceDuration = cfg.getBlazebiteMaxSlownessDuration();
                    CashClashPlayer.applyEffect(victim, PotionEffectType.SLOWNESS, freezeInPlaceDuration, 255, false, true);
                    CashClashPlayer.applyEffect(victim, PotionEffectType.JUMP_BOOST, freezeInPlaceDuration, 128, false, true);

                    Messages.debug(shooter, "BLAZEBITE: Glacier DOUBLE HIT on " + victim.getName() + " - FROZEN IN PLACE for " + (freezeInPlaceDuration / 20) + "s");
                    Messages.send(victim, "mythic.you-are-frozen");

                    SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
                    SoundUtils.play(victim, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);

                    // Continuous freeze particles above head
                    final UUID victimUUID = victimId;
                    BukkitTask particleTask = SchedulerUtils.runTaskTimer(() -> {
                        Player frozenPlayer = Bukkit.getPlayer(victimUUID);
                        if (frozenPlayer == null || !frozenPlayer.isOnline()) return;
                        ParticleUtils.freezeParticles(frozenPlayer.getLocation());
                    }, 0L, 5L);

                    // Cancel particle task after freeze duration
                    final BukkitTask taskToCancel = particleTask;
                    SchedulerUtils.runTaskLater(() -> {
                        if (taskToCancel != null && !taskToCancel.isCancelled()) {
                            taskToCancel.cancel();
                        }
                    }, freezeInPlaceDuration);

                    manager.trackTask(victimId, particleTask);
                    glacierFrozenPlayers.remove(victimId);

                    // Lock the shooter out of firing again for a few seconds after freezing someone solid
                    UUID shooterId = shooter.getUniqueId();
                    cooldownManager.setCooldownSeconds(shooterId, CooldownManager.Keys.BLAZEBITE_FREEZE_LOCKOUT, cfg.getBlazebiteFreezeLockoutSeconds());
                    Messages.send(shooter, "mythic.blazebite-froze-target", "cooldown_seconds",
                            String.valueOf(cfg.getBlazebiteFreezeLockoutSeconds()));
                } else {
                    // FIRST HIT - Apply frostbite for 5 seconds
                    int frostbiteDuration = cfg.getBlazebiteFreezeDuration();
                    CashClashPlayer.applyEffect(victim, PotionEffectType.SLOWNESS, frostbiteDuration, 0, false, true);

                    Messages.debug(shooter, "BLAZEBITE: Glacier hit " + victim.getName() + " - Frostbite for " + (frostbiteDuration / 20) + "s");
                    ParticleUtils.glacierFrost(victim.getLocation());
                    SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);

                    int freezeTicks = 140 + frostbiteDuration;
                    victim.setFreezeTicks(freezeTicks);

                    // Cancel any existing frostbite particle task
                    BukkitTask existingTask = glacierFrostbiteParticleTasks.remove(victimId);
                    if (existingTask != null && !existingTask.isCancelled()) {
                        existingTask.cancel();
                    }

                    // Frostbite particles during initial freeze
                    final UUID victimUUID = victimId;
                    BukkitTask frostbiteParticleTask = SchedulerUtils.runTaskTimer(() -> {
                        Player frostbittenPlayer = Bukkit.getPlayer(victimUUID);
                        if (frostbittenPlayer == null || !frostbittenPlayer.isOnline()) return;
                        ParticleUtils.frostbiteParticles(frostbittenPlayer.getLocation());
                    }, 0L, 5L);

                    glacierFrostbiteParticleTasks.put(victimId, frostbiteParticleTask);

                    final BukkitTask taskToCancel = frostbiteParticleTask;
                    SchedulerUtils.runTaskLater(() -> {
                        if (taskToCancel != null && !taskToCancel.isCancelled()) {
                            taskToCancel.cancel();
                        }
                        glacierFrostbiteParticleTasks.remove(victimUUID);
                    }, frostbiteDuration);

                    manager.trackTask(victimId, frostbiteParticleTask);

                    long expirationTime = currentTime + (frostbiteDuration / 20 * 1000L);
                    glacierFrozenPlayers.put(victimId, expirationTime);
                }
            }
        }

    }

    /**
     * Magma Storm mode: fire/explosion AOE damage, and cleanses the freezing effect off any
     * frozen/frostbitten players it hits instead of applying freeze itself.
     */
    private void handleMagmaStorm(Player shooter, Entity hitEntity, Location hitLoc, World world) {
        ParticleUtils.volcanoExplosion(hitLoc);
        ParticleUtils.volcanoFlameBurst(hitLoc);
        SoundUtils.playAt(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(shooter);
        Team shooterTeam = session != null ? session.getPlayerTeam(shooter) : null;
        int radius = cfg.getBlazebiteVolcanoRadius();
        int fireTicks = cfg.getBlazebiteFireDuration();
        int hitCount = 0;

        for (Entity entity : world.getNearbyEntities(hitLoc, radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.equals(shooter)) continue;

            if (session != null) {
                Team targetTeam = session.getPlayerTeam(target);
                if (targetTeam != null && shooterTeam != null &&
                    targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
            }

            double damage = entity.equals(hitEntity) ? cfg.getBlazebiteVolcanoDirectDamage() : cfg.getBlazebiteVolcanoSplashDamage();
            target.damage(damage, shooter);
            target.setFireTicks(fireTicks);
            cleanseFreeze(target);
            hitCount++;
        }
        Messages.debug(shooter, "BLAZEBITE: Magma Storm explosion hit " + hitCount + " enemies, radius: " + radius);
    }

    /**
     * Removes the Glacier freeze/frostbite state from a player - clears the tracked slowness
     * levels applied by {@link #handleGlacier}, stops any running frostbite/freeze particle
     * task, and forgets the frozen-tracking entry so a later shot starts fresh at "first hit".
     */
    private void cleanseFreeze(Player target) {
        UUID targetId = target.getUniqueId();
        if (!glacierFrozenPlayers.containsKey(targetId)
                && !glacierFrostbiteParticleTasks.containsKey(targetId)
                && !CashClashPlayer.hasEffect(target, PotionEffectType.SLOWNESS)) {
            return;
        }

        glacierFrozenPlayers.remove(targetId);
        CashClashPlayer.removeEffect(target, PotionEffectType.SLOWNESS);
        CashClashPlayer.removeEffect(target, PotionEffectType.JUMP_BOOST);
        target.setFreezeTicks(0);

        BukkitTask task = glacierFrostbiteParticleTasks.remove(targetId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        Messages.send(target, "mythic.blazebite-freeze-cleansed");
        SoundUtils.play(target, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
    }

    @Override
    public void cleanup() {
        lastShotVolcano.clear();
        glacierFrozenPlayers.clear();

        // Cancel and clear frostbite particle tasks
        glacierFrostbiteParticleTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        glacierFrostbiteParticleTasks.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        lastShotVolcano.remove(uuid);
        glacierFrozenPlayers.remove(uuid);

        BukkitTask task = glacierFrostbiteParticleTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }
}
