package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.event.PlayerBackToGameEvent;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.enums.TeamColor;
import me.psikuvit.cashClash.util.game.SpawnZoneUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Respawn timers: how long a death keeps a player spectating ({@code respawn-delay} plus the
 * seconds of the spawn zone they died in), the countdown title, and the respawn itself. Each
 * timer is kept as a deadline, so a player who leaves while it runs comes back to whatever is
 * left of it ({@link #resumeAfterRejoin}).
 */
public class RespawnManager implements Shutdownable {

    // A respawn countdown title outlives its one-second tick slightly, so the next number lands first
    private static final long RESPAWN_TITLE_STAY_MS = 1200L;

    private final ConfigManager configManager;
    private final GameManager gameManager;
    // player -> when their respawn timer runs out (epoch millis)
    private final Map<UUID, Long> deadlines;
    private final Map<UUID, BukkitTask> respawnTasks;
    private final Map<UUID, BukkitTask> countdownTasks;
    private final Map<UUID, RoundPenalty> roundPenalties;

    public RespawnManager(ConfigManager configManager, GameManager gameManager) {
        this.configManager = configManager;
        this.gameManager = gameManager;
        this.roundPenalties = new ConcurrentHashMap<>();
        this.deadlines = new ConcurrentHashMap<>();
        this.respawnTasks = new ConcurrentHashMap<>();
        this.countdownTasks = new ConcurrentHashMap<>();
    }

    /**
     * Records a death at {@code deathLocation} and returns the respawn timer it gives: every death
     * in a round adds the seconds of the zone it happened in (own zone, neutral, enemy zone) to
     * the player's timer, which starts each round at {@code respawn-delay}. Each death adds only
     * what its own spot is worth - an earlier death's zone never changes what a later one adds.
     */
    public int recordDeath(Player player, GameSession session, Location deathLocation) {
        UUID uuid = player.getUniqueId();
        RoundPenalty earlier = roundPenalties.get(uuid);
        boolean sameRound = earlier != null && earlier.sessionId().equals(session.getSessionId())
                && earlier.round() == session.getCurrentRound();
        int added = (sameRound ? earlier.seconds() : 0) + spawnZoneDelay(player, session, deathLocation);

        roundPenalties.put(uuid, new RoundPenalty(session.getSessionId(), session.getCurrentRound(), added));
        return configManager.getRespawnDelay() + added;
    }

    /** The seconds a player's deaths in one round of one game have added to their timer. */
    private record RoundPenalty(UUID sessionId, int round, int seconds) {}

    /**
     * The seconds a death adds by where it happened: in your own team's zone, in the neutral zone
     * or in the enemy's zone. Height doesn't count, and a death on a line counts as inside its
     * zone. Nothing until both teams have both lines.
     */
    private int spawnZoneDelay(Player player, GameSession session, Location deathLocation) {
        TemplateWorld template = session.getArenaTemplate();
        Team team = session.getPlayerTeam(player);
        if (team == null || !SpawnZoneUtils.hasZones(template)) return 0;

        TeamColor own = TeamColor.fromTeamNumber(team.getTeamNumber());
        if (SpawnZoneUtils.isInZone(template, own, deathLocation)) return configManager.getRespawnDelayOwnZone();
        if (SpawnZoneUtils.isInZone(template, own.opposite(), deathLocation)) return configManager.getRespawnDelayEnemyZone();
        return configManager.getRespawnDelayNeutralZone();
    }

    /**
     * Starts a spectating player's respawn timer: the "respawning in" message, the countdown
     * title, and the respawn once it runs out.
     */
    public void startRespawnTimer(Player player, int seconds) {
        UUID uuid = player.getUniqueId();
        cancel(uuid);
        deadlines.put(uuid, System.currentTimeMillis() + seconds * 1000L);

        Messages.send(player, "listener.respawn-delay", "seconds", String.valueOf(seconds));
        track(countdownTasks, uuid, startCountdown(player, seconds));
        track(respawnTasks, uuid, SchedulerUtils.runTaskLater(() -> respawn(uuid), seconds * 20L));
    }

    private static void track(Map<UUID, BukkitTask> tasks, UUID uuid, BukkitTask task) {
        if (task != null) tasks.put(uuid, task);
    }

    /**
     * When the player's running respawn timer ends (epoch millis), or 0 if they aren't waiting on one.
     */
    public long getDeadline(UUID uuid) {
        return deadlines.getOrDefault(uuid, 0L);
    }

    /**
     * Stops a player's respawn timer, e.g. as they leave the game - {@link #getDeadline} has to be
     * read before this to carry it over to a rejoin.
     */
    public void cancel(UUID uuid) {
        deadlines.remove(uuid);
        BukkitTask respawn = respawnTasks.remove(uuid);
        if (respawn != null) respawn.cancel();
        BukkitTask countdown = countdownTasks.remove(uuid);
        if (countdown != null) countdown.cancel();
    }

    /**
     * Puts a player rejoining during the combat round they left in back where leaving left them,
     * so leaving can't skip a respawn timer: a player who left while waiting on one is put back
     * spectating where they were, for whatever is left of it (none left: false, they come back at
     * the spawn as usual). Leaving alive counts as a normal death where they stood. Out of lives,
     * or in a round nobody respawns in, they spectate for the rest of it.
     *
     * @return whether they were put back spectating, rather than at their spawn
     */
    public boolean resumeAfterRejoin(Player player, GameSession session, RejoinData data, CashClashPlayer ccp) {
        if (session.getState() != GameState.COMBAT || session.isRoundEnding()) return false;
        if (data.leftRound() != session.getCurrentRound() || data.leftAt() == null) return false;

        Location spot = data.leftAt();
        boolean waiting = data.respawnAt() > 0;
        boolean leftAlive = !waiting && data.lives() > 0;
        if (leftAlive) ccp.handleDeath();

        Gamemode gamemode = session.getGamemode();
        boolean respawnAllowed = gamemode == null || gamemode.allowsRespawn();
        if (ccp.getLives() <= 0 || !respawnAllowed) {
            ccp.setLives(0);
            spectate(player, spot, leftAlive);
            if (leftAlive) Messages.sendTitle(player, "listener.you-died-title", null);
            Messages.send(player, respawnAllowed ? "listener.out-of-lives" : "listener.no-respawn-final-stand");
            return true;
        }

        int seconds;
        if (waiting) {
            long leftMs = data.respawnAt() - System.currentTimeMillis();
            if (leftMs <= 0) return false;
            seconds = (int) Math.ceil(leftMs / 1000.0);
        } else {
            seconds = recordDeath(player, session, spot);
        }

        spectate(player, spot, leftAlive);
        startRespawnTimer(player, seconds);
        return true;
    }

    private void spectate(Player player, Location spot, boolean rise) {
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spot);
        if (rise) riseSpectator(player);
    }

    /**
     * "Respawning in N seconds" as a title, counting down once a second until the respawn. Each
     * title stays a little past the next tick and never fades in, so the count reads as one
     * steady title; it stops if the round's combat ends first.
     */
    private BukkitTask startCountdown(Player player, int seconds) {
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(RESPAWN_TITLE_STAY_MS),
                Duration.ofMillis(configManager.getDefaultTitleFadeOutMs()));

        return SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                GameSession session = gameManager.getPlayerSession(player);
                if (remaining <= 0 || !player.isOnline() || session == null || session.getState() != GameState.COMBAT
                        || session.isRoundEnding() || (session.getGamemode() != null && !session.getGamemode().allowsRespawn())) {
                    cancel();
                    return;
                }
                String titleKey = remaining == 1 ? "listener.respawn-countdown-title-one" : "listener.respawn-countdown-title";
                Messages.sendTitle(player, titleKey, null, times, "seconds", String.valueOf(remaining));
                remaining--;
            }
        }, 0L, 20L);
    }

    private void respawn(UUID uuid) {
        respawnTasks.remove(uuid);
        countdownTasks.remove(uuid);
        deadlines.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        GameSession session = gameManager.getPlayerSession(player);
        if (session == null) return;

        if (session.getState() != GameState.COMBAT || session.isRoundEnding()) {
            Messages.send(player, "listener.round-ended-no-respawn");
            return;
        }

        Gamemode gamemode = session.getGamemode();
        if (gamemode != null && !gamemode.allowsRespawn()) {
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) ccp.setLives(0);
            Messages.send(player, "listener.no-respawn-final-stand");
            return;
        }

        int respawnProtectionSec = configManager.getRespawnProtection();
        Messages.debug(player, "RESPAWN", "Respawning " + player.getName() + " with " + respawnProtectionSec + "s protection");

        Location spawnLocation = session.getSpawnForPlayer(uuid);
        if (spawnLocation != null) player.teleport(spawnLocation);

        CashClashPlayer ccp = session.getCashClashPlayer(uuid);
        if (ccp != null) {
            ccp.applyHealth();
            ccp.healToFull();
            ccp.setRespawnProtection(respawnProtectionSec * 1000L);
        }
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        player.clearTitle();
        Messages.send(player, "listener.respawned");

        Bukkit.getPluginManager().callEvent(new PlayerBackToGameEvent(player));
    }

    /**
     * Rises a freshly-spectating player a short distance straight up, instead of dropping them
     * motionless at the spectator location - fast at the start and easing off toward the top, so
     * it reads as an upward boost rather than a slow elevator ride, and gets them up to a
     * spectating vantage point over the fight quickly. Spectators aren't subject to normal
     * velocity/gravity, so this is animated via incremental teleports rather than
     * {@code setVelocity}. Y is driven off the original death location so it isn't thrown off by
     * the player freely flying around mid-animation (spectators can move); X/Z/facing are read
     * fresh each tick so their own movement/looking still works normally. Self-cancels if the
     * player leaves spectator mode (e.g. respawned) before it finishes.
     */
    public void riseSpectator(Player player) {
        double totalHeight = configManager.getDeathSpectatorRiseHeight();
        int totalTicks = configManager.getDeathSpectatorRiseTicks();
        double baseY = player.getLocation().getY();

        SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getGameMode() != GameMode.SPECTATOR || ticks >= totalTicks) {
                    cancel();
                    return;
                }
                ticks++;
                double progress = (double) ticks / totalTicks;
                // Ease-out (1 - (1-x)^2): most of the climb happens in the first few ticks, then
                // eases off - a boost, not a steady lift.
                double eased = 1 - Math.pow(1 - progress, 2);
                Location current = player.getLocation();
                current.setY(baseY + totalHeight * eased);
                player.teleport(current);
            }
        }, 0L, 1L);
    }

    @Override
    public void shutdown() {
        respawnTasks.values().forEach(BukkitTask::cancel);
        countdownTasks.values().forEach(BukkitTask::cancel);
        respawnTasks.clear();
        countdownTasks.clear();
        deadlines.clear();
        roundPenalties.clear();
    }
}
