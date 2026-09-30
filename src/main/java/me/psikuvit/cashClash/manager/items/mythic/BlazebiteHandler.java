package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * BlazeBite Crossbows - each player's shots alternate between Glacier (cosmetic frostbite/freeze)
 * and Volcano (Magma Storm fire explosion) arrows.
 */
public class BlazebiteHandler extends MythicItemHandler {

    public static final String GLACIER_MODE = "glacier";
    public static final String VOLCANO_MODE = "volcano";

    // Whether each player's last shot was a Volcano arrow (absent = next shot is Glacier)
    private final Map<UUID, Boolean> lastShotVolcano;

    // When each Glacier-hit player's frostbite wears off (UUID -> expiration timestamp)
    private final Map<UUID, Long> glacierFrostbiteExpiry;

    // The frostbite or freeze particle loop running on each Glacier-hit player
    private final Map<UUID, BukkitTask> glacierParticleTasks;

    public BlazebiteHandler(MythicItemManager manager) {
        super(manager);
        this.lastShotVolcano = new ConcurrentHashMap<>();
        this.glacierFrostbiteExpiry = new ConcurrentHashMap<>();
        this.glacierParticleTasks = new ConcurrentHashMap<>();
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
     * Glacier arrow frosts over the player it hits (cosmetic only). A Volcano arrow sets off a
     * Magma Storm - a fire/explosion AOE that also thaws any frosted players caught in the blast.
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
     * Glacier mode, purely cosmetic: the first hit frostbites the player it hits, and a second
     * hit while they're still frostbitten freezes them. Both are particles and sounds only -
     * neither slows the player down.
     */
    private void handleGlacier(Player shooter, Entity hitEntity) {
        if (!(hitEntity instanceof Player victim)) return;

        UUID victimId = victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long frostbiteExpiry = glacierFrostbiteExpiry.get(victimId);

        if (frostbiteExpiry != null && frostbiteExpiry > now) {
            int freezeTicks = cfg.getBlazebiteFreezeDurationTicks();
            glacierFrostbiteExpiry.remove(victimId);
            startGlacierParticles(victimId, ParticleUtils::freezeParticles, freezeTicks);

            Messages.debug(shooter, "BLAZEBITE: Glacier double hit on " + victim.getName() + " - frozen for " + (freezeTicks / 20) + "s");
            SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            SoundUtils.play(victim, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);
            return;
        }

        int frostbiteTicks = cfg.getBlazebiteFrostbiteDurationTicks();
        glacierFrostbiteExpiry.put(victimId, now + frostbiteTicks * 50L);
        startGlacierParticles(victimId, ParticleUtils::frostbiteParticles, frostbiteTicks);

        Messages.debug(shooter, "BLAZEBITE: Glacier hit " + victim.getName() + " - frostbite for " + (frostbiteTicks / 20) + "s");
        ParticleUtils.glacierFrost(victim.getLocation());
        SoundUtils.play(victim, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
    }

    /**
     * Replaces whatever Glacier particle loop is running on the player with a new one that
     * lasts {@code durationTicks}.
     */
    private void startGlacierParticles(UUID victimId, Consumer<Location> effect, int durationTicks) {
        stopGlacierParticles(victimId);

        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            Player target = Bukkit.getPlayer(victimId);
            if (target != null && target.isOnline()) effect.accept(target.getLocation());
        }, 0L, 5L);
        glacierParticleTasks.put(victimId, task);
        manager.trackTask(victimId, task);

        SchedulerUtils.runTaskLater(() -> {
            if (glacierParticleTasks.remove(victimId, task)) task.cancel();
        }, durationTicks);
    }

    /**
     * @return true if a Glacier particle loop was running on the player
     */
    private boolean stopGlacierParticles(UUID victimId) {
        BukkitTask task = glacierParticleTasks.remove(victimId);
        if (task == null) return false;
        task.cancel();
        return true;
    }

    /**
     * Magma Storm mode: fire/explosion AOE damage, and thaws any frostbitten/frozen players it
     * hits instead of applying freeze itself.
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
     * Thaws a Glacier-hit player - stops their frostbite/freeze particles and forgets the
     * frostbite, so a later Glacier shot starts fresh at "first hit". Only touches Glacier's own
     * state, never potion effects, so slowness from other sources (flag carrier, Tectonic Cap,
     * Orb of Gravitation) survives the blast.
     */
    private void cleanseFreeze(Player target) {
        UUID targetId = target.getUniqueId();
        glacierFrostbiteExpiry.remove(targetId);
        if (!stopGlacierParticles(targetId)) return;

        Messages.send(target, "mythic.blazebite-freeze-cleansed");
        SoundUtils.play(target, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
    }

    @Override
    public void cleanup() {
        lastShotVolcano.clear();
        glacierFrostbiteExpiry.clear();

        glacierParticleTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        glacierParticleTasks.clear();
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        lastShotVolcano.remove(uuid);
        glacierFrostbiteExpiry.remove(uuid);
        stopGlacierParticles(uuid);
    }
}
