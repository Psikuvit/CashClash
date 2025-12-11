package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.game.GameSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Utility class for managing team colors in scoreboards (tab list and nametags).
 */
public final class TeamColorUtils {

    private static final String TEAM1_NAME = "cc_team1";
    private static final String TEAM2_NAME = "cc_team2";

    private TeamColorUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Setup red/blue teams on a scoreboard for tab list and nametag coloring.
     *
     * @param board   the scoreboard to setup teams on
     */
    private static void setupTeams(Scoreboard board) {
        if (board == null) {
            return;
        }

        Team existing1 = board.getTeam(TEAM1_NAME);
        if (existing1 != null) {
            existing1.unregister();
        }

        Team existing2 = board.getTeam(TEAM2_NAME);
        if (existing2 != null) {
            existing2.unregister();
        }

        Team team1 = board.registerNewTeam(TEAM1_NAME);
        team1.color(NamedTextColor.RED);
        team1.prefix(Component.empty());

        Team team2 = board.registerNewTeam(TEAM2_NAME);
        team2.color(NamedTextColor.BLUE);
        team2.prefix(Component.empty());
    }

    /**
     * Assign all players from a GameSession to their respective scoreboard teams.
     *
     * @param board   the scoreboard containing the teams
     * @param session the game session with player team assignments
     */
    public static void assignPlayersToTeams(Scoreboard board, GameSession session) {
        setupTeams(board);

        Team t1 = board.getTeam(TEAM1_NAME);
        Team t2 = board.getTeam(TEAM2_NAME);

        if (t1 == null || t2 == null) {
            return;
        }

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                continue;
            }

            String entry = p.getName();

            t1.removeEntry(entry);
            t2.removeEntry(entry);

            if (session.getTeam1().hasPlayer(uuid)) {
                t1.addEntry(entry);
            } else if (session.getTeam2().hasPlayer(uuid)) {
                t2.addEntry(entry);
            }
        }
    }
}

