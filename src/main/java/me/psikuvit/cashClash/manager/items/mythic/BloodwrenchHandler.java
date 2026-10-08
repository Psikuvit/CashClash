package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.HealingMarkUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BloodWrench Crossbow - counts the hits its wielder lands on players: one set hit releases a
 * lingering blood bubble, a later one a blood tornado, and then the count starts over. Every
 * other hit is a plain crossbow hit.
 */
public class BloodwrenchHandler extends MythicItemHandler {

    // Hits each wielder has landed so far in the current bubble/tornado cycle
    private final Map<UUID, Integer> landedHits;

    // When each wielder's last blood tornado went off, so the HUD can hold the full count briefly
    private final Map<UUID, Long> lastTornadoAt;

    public BloodwrenchHandler(MythicItemManager manager) {
        super(manager);
        this.landedHits = new ConcurrentHashMap<>();
        this.lastTornadoAt = new ConcurrentHashMap<>();
    }

    /**
     * Counts a BloodWrench arrow that hit a player: the bubble-on-hit'th hit releases a blood
     * bubble where it landed, the tornado-on-hit'th a blood tornado, after which the count starts
     * over.
     */
    public void onHitLanded(Player shooter, Location hitLocation) {
        UUID uuid = shooter.getUniqueId();
        int hits = landedHits.merge(uuid, 1, Integer::sum);

        if (hits >= cfg.getBloodwrenchTornadoOnHit()) {
            landedHits.remove(uuid);
            lastTornadoAt.put(uuid, System.currentTimeMillis());
            releaseBloodTornado(shooter, hitLocation);
        } else if (hits == cfg.getBloodwrenchBubbleOnHit()) {
            releaseBloodBubble(shooter, hitLocation);
        }
    }

    /** Light red, to read as blood against Soul Katana's blue mark. */
    private static final Color HEAL_MARK_RING = Color.fromRGB(255, 90, 90);
    private static final Color HEAL_MARK_WISP = Color.fromRGB(255, 160, 160);

    /**
     * Blocks a player's healing for as long as they stay in a Blood Sphere - the only Bloodwrench
     * zone that negates healing (Blood Vortex doesn't). Called every tick to refresh the debuff,
     * so the "healing blocked" announcement is gated on the player not already being debuffed -
     * otherwise standing in a sphere would spam chat once per tick. The mark itself is told to
     * announce the recovery when it lapses.
     */
    private void applyHealNegation(Player target, int durationSeconds) {
        if (!CashClashPlayer.isHealingReduced(target)) {
            Messages.send(target, "mythic.bloodwrench-healing-blocked");
            SoundUtils.play(target, Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f);
        }

        CashClashPlayer.reduceHealing(target, 0.0, durationSeconds);
        HealingMarkUtils.show(target, HEAL_MARK_RING, HEAL_MARK_WISP, null, "mythic.bloodwrench-healing-restored");
    }

    /**
     * Blood bubble - a lingering blood sphere with a burst of damage on release, blocking the
     * healing of enemies inside it and costing them {@code sphere-damage-per-second} each second.
     */
    private void releaseBloodBubble(Player shooter, Location hitLocation) {
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
        double sphereDensity = cfg.getBloodwrenchSphereParticleDensity();
        double damagePerSecond = cfg.getBloodwrenchSphereDamagePerSecond();
        BukkitTask sphereTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;
                // This runs every half second, so every other run is a full second
                boolean damageTick = tick % 2 == 0;

                ParticleUtils.bloodSphereShell(hitLocation, sphereRadius, tick, sphereDensity);

                // Heal-negation zone: enemies only, refreshed every tick while inside - naturally
                // decays `healNegationDuration` seconds after the target leaves the sphere since
                // nothing refreshes it anymore.
                for (Entity entity : world.getNearbyEntities(hitLocation, sphereRadius, sphereRadius, sphereRadius)) {
                    if (!(entity instanceof Player target)) continue;
                    if (target.equals(shooter)) continue;
                    if (session != null) {
                        Team targetTeam = session.getPlayerTeam(target);
                        if (targetTeam != null && shooterTeam != null &&
                            targetTeam.getTeamNumber() == shooterTeam.getTeamNumber()) continue;
                    }

                    applyHealNegation(target, healNegationDuration);
                    if (damageTick && damagePerSecond > 0) {
                        target.damage(damagePerSecond, shooter);
                    }
                }
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
     * Blood tornado - a blood vortex that levitates and damages enemies inside, healing the
     * wielder for part of the damage.
     */
    private void releaseBloodTornado(Player shooter, Location hitLocation) {
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
        int vortexStrands = cfg.getBloodwrenchVortexParticleStrands();
        int vortexDensity = cfg.getBloodwrenchVortexParticleDensity();

        BukkitTask vortexTask = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;

                ParticleUtils.bloodVortexSpiral(hitLocation, radius, tick, vortexStrands, vortexDensity);

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

                   CashClashPlayer.applyEffect(target, PotionEffectType.LEVITATION, 30, cfg.getBloodwrenchVortexLevitationLevel() - 1);
                    Vector preDamageVelocity = target.getVelocity();
                    target.damage(damagePerTick, shooter);
                    target.setVelocity(preDamageVelocity);
                    totalDamageDealt += damagePerTick;
                    Messages.debug(shooter, "BLOODWRENCH: Vortex affecting " + target.getName() + " for " + damagePerTick + " damage");
                }

                // Wielder heals for a percentage of the damage the vortex dealt this batch -
                // reduced by their own active healing-reduction debuff (e.g. Soul Katana).
                if (totalDamageDealt > 0) {
                    CashClashPlayer.heal(shooter, totalDamageDealt * selfHealPercent);
                    ParticleUtils.bloodHealPulse(shooter);
                }
                }
            }
        }, 0L, 2L);

        // Cancel after duration
        SchedulerUtils.runTaskLater(() -> {
            Objects.requireNonNull(vortexTask).cancel();
            Messages.debug(shooter, "BLOODWRENCH: Blood vortex expired");
            SoundUtils.playAt(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        }, durationTicks);

        manager.trackTask(shooter.getUniqueId(), vortexTask);
    }

    /**
     * The hit counter, e.g. {@code 2/7}: red on the bubble hit, and held at the full count in red
     * for a moment after the tornado before it starts over.
     */
    @Override
    public List<HudSegment> hudSegments(Player player) {
        UUID uuid = player.getUniqueId();
        int max = cfg.getBloodwrenchTornadoOnHit();
        Long tornadoAt = lastTornadoAt.get(uuid);
        boolean showingFull = tornadoAt != null && System.currentTimeMillis() - tornadoAt < cfg.getMythicHudFullCountDisplayMs();
        int hits = showingFull ? max : landedHits.getOrDefault(uuid, 0);
        boolean alert = showingFull || hits == cfg.getBloodwrenchBubbleOnHit();
        return List.of(new HudSegment(hudText("bloodwrench-hits", "hits", String.valueOf(hits), "max", String.valueOf(max)), alert));
    }

    @Override
    public void cleanup() {
        landedHits.clear();
        lastTornadoAt.clear();
    }

    /**
     * The hit count is kept through a death (this runs on death as well as on quit), so a
     * respawned wielder picks up where they left off; it only resets with the game.
     */
    @Override
    public void cleanupPlayer(Player player) {
        lastTornadoAt.remove(player.getUniqueId());
    }
}
