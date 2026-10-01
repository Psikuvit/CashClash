package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * BlazeBite Crossbows - each player's shots alternate between Glacier (frostbite, then frozen
 * solid on a second hit) and Volcano (Magma Storm fire explosion) arrows.
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

    // Players frozen solid, and the task that thaws them
    private final Map<UUID, BukkitTask> frozenSolidThawTasks;

    public BlazebiteHandler(MythicItemManager manager) {
        super(manager);
        this.lastShotVolcano = new ConcurrentHashMap<>();
        this.glacierFrostbiteExpiry = new ConcurrentHashMap<>();
        this.glacierParticleTasks = new ConcurrentHashMap<>();
        this.frozenSolidThawTasks = new ConcurrentHashMap<>();
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
     * Glacier arrow frostbites the player it hits, or freezes them solid if they're already
     * frostbitten. A Volcano arrow sets off a Magma Storm - a fire/explosion AOE - and leaves any
     * frostbite in place, so Glacier, Volcano, Glacier on one target still freezes them solid.
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
     * Glacier mode: the first hit frostbites the player it hits - particles and sounds only, no
     * slowdown. A second hit while they're still frostbitten freezes them solid: no moving or
     * jumping for {@code freeze-duration-seconds}. A hit on someone already frozen solid does
     * nothing more.
     */
    private void handleGlacier(Player shooter, Entity hitEntity) {
        if (!(hitEntity instanceof Player victim)) return;

        UUID victimId = victim.getUniqueId();
        if (frozenSolidThawTasks.containsKey(victimId)) return;

        long now = System.currentTimeMillis();
        Long frostbiteExpiry = glacierFrostbiteExpiry.get(victimId);

        if (frostbiteExpiry != null && frostbiteExpiry > now) {
            int freezeTicks = cfg.getBlazebiteFreezeDurationTicks();
            glacierFrostbiteExpiry.remove(victimId);
            startGlacierParticles(victimId, ParticleUtils::freezeParticles, freezeTicks);
            freezeSolid(victim, freezeTicks);

            Messages.debug(shooter, "BLAZEBITE: Glacier double hit on " + victim.getName() + " - frozen solid for " + (freezeTicks / 20) + "s");
            Messages.send(victim, "mythic.you-are-frozen");
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
     * Pins the victim in place by zeroing their movement speed and jump strength, then thaws them
     * after {@code durationTicks}. Attribute modifiers rather than Slowness 255 + Jump Boost 128:
     * since 1.20.5 effect amplifiers no longer wrap around, so Jump Boost 128 launches the player
     * instead of grounding them. Transient, so a crash mid-freeze can't save a frozen player.
     */
    private void freezeSolid(Player victim, int durationTicks) {
        UUID victimId = victim.getUniqueId();
        zeroAttribute(victim, Attribute.MOVEMENT_SPEED, Keys.BLAZEBITE_FREEZE_SPEED);
        zeroAttribute(victim, Attribute.JUMP_STRENGTH, Keys.BLAZEBITE_FREEZE_JUMP);

        BukkitTask thawTask = SchedulerUtils.runTaskLater(() -> thaw(victimId), durationTicks);
        frozenSolidThawTasks.put(victimId, thawTask);
        manager.trackTask(victimId, thawTask);
    }

    private void zeroAttribute(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(key);
        instance.addTransientModifier(new AttributeModifier(key, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    /**
     * Lifts a freeze solid, if the player is frozen. Safe to call for a player who's offline.
     */
    private void thaw(UUID playerId) {
        BukkitTask task = frozenSolidThawTasks.remove(playerId);
        if (task != null && !task.isCancelled()) task.cancel();

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(Keys.BLAZEBITE_FREEZE_SPEED);
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(Keys.BLAZEBITE_FREEZE_JUMP);
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

    private void stopGlacierParticles(UUID victimId) {
        BukkitTask task = glacierParticleTasks.remove(victimId);
        if (task != null) task.cancel();
    }

    /**
     * Magma Storm mode: fire/explosion AOE damage to every enemy in the blast.
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
            hitCount++;
        }
        Messages.debug(shooter, "BLAZEBITE: Magma Storm explosion hit " + hitCount + " enemies, radius: " + radius);
    }

    @Override
    public void cleanup() {
        lastShotVolcano.clear();
        glacierFrostbiteExpiry.clear();

        glacierParticleTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        glacierParticleTasks.clear();

        frozenSolidThawTasks.keySet().forEach(this::thaw);
    }

    @Override
    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        lastShotVolcano.remove(uuid);
        glacierFrostbiteExpiry.remove(uuid);
        stopGlacierParticles(uuid);
        thaw(uuid);
    }
}
