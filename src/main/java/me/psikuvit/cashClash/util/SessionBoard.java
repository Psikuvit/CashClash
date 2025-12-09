package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Objects;

/**
 * SessionBoard is a standalone utility that manages a scoreboard instance
 * for a single GameSession. It handles periodic updates and cleanup.
 */
public class SessionBoard {

    private final UUID sessionId;
    public final Scoreboard scoreboard;
    private Objective objective;
    private BukkitTask task;

    private final Map<UUID, Scoreboard> playerScoreboards;
    private final List<Scoreboard> playerScoreboardList;

    public SessionBoard(UUID sessionId, Scoreboard scoreboard) {
        this.sessionId = sessionId;
        this.scoreboard = scoreboard;
        this.playerScoreboards = new HashMap<>();
        this.playerScoreboardList = new ArrayList<>();
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled()) return;
        task = SchedulerUtils.runTaskTimer(this::update, 0, 20L);
    }

    public synchronized void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (objective != null) {
            objective.unregister();
            objective = null;
        }

        // unregister and clear per-player scoreboards
        playerScoreboards.forEach((uuid, sb) -> {
            String objName = "cc_sidebar_" + sessionId.toString().substring(0, 8) + "_" + uuid.toString().substring(0, 8);
            Objective o = sb.getObjective(objName);
            if (o != null) o.unregister();

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline())
                p.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());
        });

        playerScoreboards.clear();
        playerScoreboardList.clear();
    }

    /**
     * Update scoreboard contents. Safe to call from scheduler task.
     */
    public synchronized void update() {
        GameSession session = GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst().orElse(null);

        if (session == null) {
            cancel();
            return;
        }

        String titleRaw = ConfigManager.getInstance().getScoreboardTitle();
        Component titleComp = Messages.parse(titleRaw);

        List<String> lines = ConfigManager.getInstance().getScoreboardLines();

        if (!playerScoreboards.isEmpty()) {
            // update each player's individual scoreboard
            playerScoreboards.forEach((viewerUuid, sb) -> {
                String objName = "cc_sidebar_" + sessionId.toString().substring(0, 8) + "_" + viewerUuid.toString().substring(0, 8);
                Objective o = sb.getObjective(objName);
                if (o != null) o.unregister();

                o = sb.registerNewObjective(objName, Criteria.DUMMY, titleComp);
                o.setDisplaySlot(DisplaySlot.SIDEBAR);

                int score = lines.size();
                for (int i = 0; i < lines.size(); i++) {
                    String raw = lines.get(i);

                    String filled = fillPlaceholders(raw, session, viewerUuid);
                    if (filled.length() > 40) filled = filled.substring(0, 40);

                    filled = makeUnique(filled, i);
                    o.getScore(filled).setScore(score--);
                }

                Player p = Bukkit.getPlayer(viewerUuid);
                if (p != null && p.isOnline()) p.setScoreboard(sb);
            });
        }
    }

    private String makeUnique(String base, int idx) {
        return base + "§" + (char)('a' + (idx % 26));
    }

    private String fillPlaceholders(String template, GameSession session, UUID viewer) {
        if (template == null) return "";
        String t = template;
        t = t.replace("{round}", String.valueOf(session.getCurrentRound()));
        t = t.replace("{state}", String.valueOf(session.getState()));

        t = t.replace("{team1_coins}", String.valueOf(session.getTeam1Coins()));
        t = t.replace("{team2_coins}", String.valueOf(session.getTeam2Coins()));
        // time placeholder (seconds remaining in current phase)
        t = t.replace("{time}", String.valueOf(session.getTimeRemaining()));

        if (t.contains("{player_coins}") || t.contains("{player_kills}") || t.contains("{player_lives}")) {
            CashClashPlayer p = null;
            if (viewer != null) p = session.getCashClashPlayer(viewer);
            if (p == null) {
                Optional<UUID> any = session.getPlayers().stream().findFirst();
                if (any.isPresent()) p = session.getCashClashPlayer(any.get());
            }
            if (p != null) {
                t = t.replace("{player_coins}", String.valueOf(p.getCoins()));
                t = t.replace("{player_kills}", String.valueOf(p.getTotalKills()));
                t = t.replace("{player_lives}", String.valueOf(p.getLives()));
            } else {
                t = t.replace("{player_coins}", "0");
                t = t.replace("{player_kills}", "0");
                t = t.replace("{player_lives}", "0");
            }
        }

        return t;
    }
}
