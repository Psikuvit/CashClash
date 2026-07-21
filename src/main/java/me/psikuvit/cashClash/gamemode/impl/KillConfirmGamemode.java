package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.gamemode.FinalStandManager;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.kc.KCZoneUtils;
import me.psikuvit.cashClash.util.game.kc.KCZoneValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kill Confirm Gamemode.
 * Every kill scores a point immediately. Every death also spawns a capture zone at the death
 * location - the killer's team can confirm it for a bonus, the victim's team can deny it. A
 * player's 3rd kill in an uninterrupted streak spawns a Money Tag (or, in sudden death, a Heart
 * Tag) instead of a plain nametag. First team to {@value #WIN_CONDITION} combined points wins
 * the round.
 */
public class KillConfirmGamemode extends Gamemode {

    private static final int WIN_CONDITION = 16;
    private static final int TRIPLE_KILL_STREAK = 3;
    private static final long ZONE_ACTIVATION_DELAY_MS = 500;
    private static final long ZONE_LIFESPAN_MS = 9000;
    private static final long CAPTURE_DURATION_MS = 4000;
    private static final long FINAL_STAND_CAPTURE_DURATION_MS = 2000;
    private static final long MONEY_BONUS = 15000;
    private static final long HEART_BONUS_DURATION_MS = 45 * 1000;

    private final Map<Integer, Integer> teamScore;
    private final Map<Integer, Integer> suddenDeathCycleScore;
    private final List<KCZone> activeZones;
    private final Set<UUID> tripleKillAwarded; // guards the streak-3 zone firing more than once per streak

    private final SuddenDeathManager suddenDeathManager;
    private final FinalStandManager finalStandManager;
    private BukkitTask zoneTickTask;
    private int suddenDeathWinningTeam;

    public KillConfirmGamemode(GameSession session) {
        super(session, GamemodeType.KILL_CONFIRM);

        this.teamScore = new HashMap<>(2);
        this.suddenDeathCycleScore = new HashMap<>(2);
        this.activeZones = new ArrayList<>();
        this.tripleKillAwarded = new HashSet<>();
        this.suddenDeathManager = new SuddenDeathManager(session, this);
        this.finalStandManager = new FinalStandManager(session, this);
        this.zoneTickTask = null;
        this.suddenDeathWinningTeam = 0;

        teamScore.put(1, 0);
        teamScore.put(2, 0);
        suddenDeathCycleScore.put(1, 0);
        suddenDeathCycleScore.put(2, 0);
    }

    // ========= OVERRIDDEN METHODS =========

    @Override
    public void onGameStart() {
        Messages.debug("[KC] Gamemode started");
        Messages.broadcast(session.getPlayers(), "gamemode-kc.game-started");
        Messages.broadcast(session.getPlayers(), "gamemode-kc.objective");
        Messages.broadcast(session.getPlayers(), "gamemode-kc.bonus-info");
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[KC] Combat phase started");
        if (suddenDeathManager.isInSuddenDeath()) {
            suddenDeathCycleScore.put(1, 0);
            suddenDeathCycleScore.put(2, 0);
            Messages.debug("[KC] Sudden death cycle started - score counters reset");
        }

        tripleKillAwarded.clear();
        zoneTickTask = SchedulerUtils.runTaskTimer(this::tickCaptureZones, 0, 5);
    }

    @Override
    public void onRoundEnd() {
        cancelTask(zoneTickTask);
        zoneTickTask = null;
        clearAllZones();

        suddenDeathManager.resetForNewRound();
        finalStandManager.cancel();
        teamScore.put(1, 0);
        teamScore.put(2, 0);
        suddenDeathCycleScore.put(1, 0);
        suddenDeathCycleScore.put(2, 0);
        suddenDeathWinningTeam = 0;
        tripleKillAwarded.clear();
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        if (killer == null) return;

        Team killerTeamObj = session.getPlayerTeam(killer);
        if (killerTeamObj == null) return;
        int killerTeam = killerTeamObj.getTeamNumber();

        awardKillPoint(killerTeam);

        // The point that just won the round doesn't spawn a confirm zone.
        if (checkGameWinner()) {
            return;
        }

        boolean tripleKill = false;
        CashClashPlayer killerCcp = session.getCashClashPlayer(killer.getUniqueId());
        if (killerCcp != null && killerCcp.getKillStreak() == TRIPLE_KILL_STREAK
                && tripleKillAwarded.add(killer.getUniqueId())) {
            tripleKill = true;
        }

        KCZone.ZoneKind kind;
        if (tripleKill) {
            kind = suddenDeathManager.isInSuddenDeath() ? KCZone.ZoneKind.HEART : KCZone.ZoneKind.MONEY;
        } else {
            kind = KCZone.ZoneKind.NAMETAG;
        }

        spawnConfirmZone(victim.getLocation(), killerTeam, victim.getName(), kind);
    }

    @Override
    public void onPlayerSpawn(Player player) {
        suddenDeathManager.onPlayerSpawn(player);
    }

    @Override
    public void onPlayerRemove(Player player) {
        UUID uuid = player.getUniqueId();
        for (KCZone zone : activeZones) {
            zone.getOccupantEntryTimestamps().remove(uuid);
        }
        ActionBarQueue.get().stopDisplay(player);
    }

    @Override
    public boolean checkGameWinner() {
        int score1 = teamScore.getOrDefault(1, 0);
        int score2 = teamScore.getOrDefault(2, 0);

        if (suddenDeathWinningTeam > 0) {
            return true;
        }

        if (!suddenDeathManager.isInSuddenDeath() && (score1 >= WIN_CONDITION || score2 >= WIN_CONDITION)) {
            return true;
        }

        return finalStandManager.isActive() && (score1 >= WIN_CONDITION || score2 >= WIN_CONDITION);
    }

    @Override
    public int getWinningTeam() {
        if (suddenDeathWinningTeam > 0) {
            return suddenDeathWinningTeam;
        }

        int score1 = teamScore.getOrDefault(1, 0);
        int score2 = teamScore.getOrDefault(2, 0);
        boolean eligible = !suddenDeathManager.isInSuddenDeath() || finalStandManager.isActive();

        if (eligible && score1 >= WIN_CONDITION) {
            return 1;
        } else if (eligible && score2 >= WIN_CONDITION) {
            return 2;
        }
        return 0;
    }

    @Override
    public void cleanup() {
        cancelTask(zoneTickTask);
        zoneTickTask = null;
        clearAllZones();

        finalStandManager.cancel();
        suddenDeathManager.cleanup();

        teamScore.clear();
        suddenDeathCycleScore.clear();
        tripleKillAwarded.clear();
    }

    @Override
    public String getRoundStartMessage() {
        return "<gold>Kill Confirm Round!</gold>";
    }

    @Override
    public String getBuyPhaseMessage() {
        return "<yellow>Confirm your kills for points, deny the enemy's! First team to " + WIN_CONDITION + " wins!</yellow>";
    }

    @Override
    public String getObjectiveShort() {
        return "Reach " + WIN_CONDITION + " Kills/Captures";
    }

    @Override
    public void onFinalStandActivated() {
        Messages.broadcast(session.getPlayers(), "gamemode-kc.final-stand-activated");
    }

    @Override
    public void onSuddenDeathCycleEnded() {
        int score1 = suddenDeathCycleScore.getOrDefault(1, 0);
        int score2 = suddenDeathCycleScore.getOrDefault(2, 0);

        if (score1 != score2) {
            suddenDeathWinningTeam = score1 > score2 ? 1 : 2;
            Messages.debug("[KC] Sudden death cycle winner determined: Team " + suddenDeathWinningTeam);
        } else {
            Messages.debug("[KC] Sudden death cycle tied " + score1 + "-" + score2 + "; restarting");
        }
    }

    @Override
    public void onSuddenDeathCycleRestart() {
        suddenDeathCycleScore.put(1, 0);
        suddenDeathCycleScore.put(2, 0);
        teamScore.put(1, 0);
        teamScore.put(2, 0);

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                ScoreboardManager.getInstance().updatePlayerScoreboard(p);
                Messages.send(p, "gamemode-kc.sudden-death-timer-start");
            }
        }

        Messages.broadcast(session.getPlayers(), "gamemode-kc.sudden-death-tied-restart");
    }

    @Override
    public boolean forceSuddenDeathForTesting() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return false;
        }
        prepareSuddenDeathRound();
        return true;
    }

    @Override
    public void prepareSuddenDeathRound() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return;
        }

        suddenDeathManager.enterSuddenDeath();
        Messages.broadcast(session.getPlayers(), "gamemode-kc.sudden-death");
        Messages.broadcast(session.getPlayers(), "gamemode-kc.sudden-death-timer-start");
    }

    @Override
    public SuddenDeathManager getSuddenDeathManager() {
        return suddenDeathManager;
    }

    @Override
    public FinalStandManager getFinalStandManager() {
        return finalStandManager;
    }

    // ========= PUBLIC ACCESSORS (for scoreboard placeholders) =========

    public int getTeamScore(int team) {
        return teamScore.getOrDefault(team, 0);
    }

    // ========= PRIVATE HELPERS =========

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private void awardKillPoint(int team) {
        teamScore.merge(team, 1, Integer::sum);
        if (suddenDeathManager.isInSuddenDeath()) {
            suddenDeathCycleScore.merge(team, 1, Integer::sum);
        }

        String teamName = team == 1 ? "Red" : "Blue";
        Messages.broadcast(session.getPlayers(), "gamemode-kc.kill-point",
                "team_name", teamName,
                "score", String.valueOf(teamScore.get(team)),
                "win_condition", String.valueOf(WIN_CONDITION));
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);
    }

    private void spawnConfirmZone(Location deathLoc, int killerTeam, String victimName, KCZone.ZoneKind kind) {
        if (deathLoc == null || deathLoc.getWorld() == null) return;

        long now = System.currentTimeMillis();
        long activatesAt = now + ZONE_ACTIVATION_DELAY_MS;
        long expiresAt = activatesAt + ZONE_LIFESPAN_MS;

        KCZone zone = new KCZone(deathLoc.clone(), killerTeam, victimName, kind, activatesAt, expiresAt);
        KCZoneUtils.spawnZoneEntities(zone);
        activeZones.add(zone);

        if (kind != KCZone.ZoneKind.NAMETAG) {
            String teamName = killerTeam == 1 ? "Red" : "Blue";
            Messages.broadcast(session.getPlayers(), "gamemode-kc.money-tag-spawned",
                    "player_name", victimName,
                    "team_name", teamName);
        }
    }

    private void clearAllZones() {
        for (KCZone zone : activeZones) {
            KCZoneUtils.despawnZoneEntities(zone);
            stopAllOccupantDisplays(zone);
        }
        activeZones.clear();
    }

    /**
     * Poll every active zone: track each occupant's own hold time, resolve the zone the moment
     * any single player finishes their hold (first of either team to do so wins the zone).
     */
    private void tickCaptureZones() {
        if (activeZones.isEmpty()) return;
        if (session.isSequenceLocked() || session.isActionsRestricted()) return;

        long now = System.currentTimeMillis();
        Iterator<KCZone> it = activeZones.iterator();
        while (it.hasNext()) {
            KCZone zone = it.next();

            if (zone.isPendingActivation(now)) continue;

            if (zone.isExpired(now)) {
                resolveZoneExpired(zone);
                it.remove();
                continue;
            }

            if (tickZoneOccupants(zone, now)) {
                it.remove();
            }
        }
    }

    /**
     * @return true if the zone was resolved (captured or denied) this tick
     */
    private boolean tickZoneOccupants(KCZone zone, long now) {
        long requiredMs = isFinalStandActive() ? FINAL_STAND_CAPTURE_DURATION_MS : CAPTURE_DURATION_MS;
        Map<UUID, Long> occupants = zone.getOccupantEntryTimestamps();
        boolean anyoneHolding = false;

        for (UUID uuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                occupants.remove(uuid);
                continue;
            }

            if (!KCZoneValidator.isPlayerInZone(player, zone.getCenter())) {
                if (occupants.remove(uuid) != null) {
                    ActionBarQueue.get().stopDisplay(player);
                    Messages.send(player, "gamemode-kc.zone-capture-cancelled", "victim_name", zone.getVictimName());
                }
                continue;
            }

            Long entry = occupants.get(uuid);
            if (entry == null) {
                entry = now;
                occupants.put(uuid, now);
                Messages.send(player, "gamemode-kc.zone-capturing", "victim_name", zone.getVictimName());
            }

            long elapsed = now - entry;
            if (elapsed >= requiredMs) {
                Team playerTeam = session.getPlayerTeam(player);
                if (playerTeam == null) continue;
                resolveZone(zone, player, playerTeam.getTeamNumber());
                return true;
            }

            anyoneHolding = true;
            long remainingMs = requiredMs - elapsed;
            ActionBarQueue.get().startCountdownTimer(player, remainingMs, 1,
                    secondsRemaining -> "<gold>Capturing: " + secondsRemaining + "s</gold>");
        }

        if (anyoneHolding) {
            KCZoneUtils.spawnCaptureProgressParticles(zone.getCenter());
        }

        return false;
    }

    private void resolveZone(KCZone zone, Player resolver, int resolvingTeam) {
        KCZoneUtils.despawnZoneEntities(zone);
        stopAllOccupantDisplays(zone);

        if (resolvingTeam == zone.getKillerTeam()) {
            resolveZoneCaptured(zone, resolver, resolvingTeam);
        } else {
            resolveZoneDenied(zone, resolver, resolvingTeam);
        }
    }

    private void resolveZoneCaptured(KCZone zone, Player capturer, int capturingTeam) {
        String teamName = capturingTeam == 1 ? "Red" : "Blue";
        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        if (zone.getKind() == KCZone.ZoneKind.NAMETAG) {
            teamScore.merge(capturingTeam, 1, Integer::sum);
            if (suddenDeathManager.isInSuddenDeath()) {
                suddenDeathCycleScore.merge(capturingTeam, 1, Integer::sum);
            }
            Messages.broadcast(session.getPlayers(), "gamemode-kc.tag-confirmed",
                    "player_name", capturer.getName(),
                    "team_name", teamName,
                    "score", String.valueOf(teamScore.get(capturingTeam)),
                    "win_condition", String.valueOf(WIN_CONDITION));
        } else {
            awardConfirmBonus(capturingTeam, zone.getKind() == KCZone.ZoneKind.HEART);
            Messages.broadcast(session.getPlayers(), "gamemode-kc.money-tag-confirmed",
                    "player_name", capturer.getName(),
                    "team_name", teamName);
        }
    }

    private void resolveZoneDenied(KCZone zone, Player denier, int denyingTeam) {
        String teamName = denyingTeam == 1 ? "Red" : "Blue";
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        Messages.broadcast(session.getPlayers(), "gamemode-kc.tag-denied",
                "player_name", denier.getName(),
                "team_name", teamName);
    }

    private void resolveZoneExpired(KCZone zone) {
        KCZoneUtils.despawnZoneEntities(zone);
        stopAllOccupantDisplays(zone);
    }

    private void stopAllOccupantDisplays(KCZone zone) {
        for (UUID uuid : zone.getOccupantEntryTimestamps().keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                ActionBarQueue.get().stopDisplay(p);
            }
        }
        zone.getOccupantEntryTimestamps().clear();
    }

    /**
     * Split the Money Tag bonus across the killer's team - money normally, or an extra heart
     * per player during sudden death.
     */
    private void awardConfirmBonus(int team, boolean heartVariant) {
        Team teamObj = team == 1 ? session.getTeamRed() : session.getTeamBlue();
        Set<UUID> members = teamObj.getPlayers();
        if (members.isEmpty()) return;

        long share = MONEY_BONUS / members.size();

        for (UUID uuid : members) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (heartVariant) {
                suddenDeathManager.applyExtraHeart(p, HEART_BONUS_DURATION_MS);
                Messages.send(p, "gamemode-kc.money-tag-bonus-heart");
            } else {
                CashClashPlayer ccp = session.getCashClashPlayer(uuid);
                if (ccp != null) {
                    ccp.addCoins(share);
                }
                Messages.send(p, "gamemode-kc.money-tag-bonus-coins", "amount", String.format("%,d", share));
            }
        }
    }
}
