package me.psikuvit.cashClash.util.effects;

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

    private static final String TEAM_RED_NAME = "cc_team_red";
    private static final String TEAM_BLUE_NAME = "cc_team_blue";

    private TeamColorUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Setup red/blue teams on a scoreboard for tab list and nametag coloring.
     *
     * @param board   the scoreboard to setup teams on
     */
    private static void setupTeams(Scoreboard board) {
        Team existingRed = board.getTeam(TEAM_RED_NAME);
        if (existingRed != null) existingRed.unregister();

        Team existingBlue = board.getTeam(TEAM_BLUE_NAME);
        if (existingBlue != null) existingBlue.unregister();

        Team teamRed = board.registerNewTeam(TEAM_RED_NAME);
        teamRed.color(NamedTextColor.RED);
        teamRed.prefix(Component.empty());

        Team teamBlue = board.registerNewTeam(TEAM_BLUE_NAME);
        teamBlue.color(NamedTextColor.BLUE);
        teamBlue.prefix(Component.empty());
    }

    /**
     * Assign all players from a GameSession to their respective scoreboard teams.
     *
     * @param board   the scoreboard containing the teams
     * @param session the game session with player team assignments
     */
    public static void assignPlayersToTeams(Scoreboard board, GameSession session) {
        if (board == null || session == null) return;
        setupTeams(board);

        Team tRed = board.getTeam(TEAM_RED_NAME);
        Team tBlue = board.getTeam(TEAM_BLUE_NAME);

        if (tRed == null || tBlue == null) {
            return;
        }

        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            String entry = p.getName();

            tRed.removeEntry(entry);
            tBlue.removeEntry(entry);

            if (session.getTeamRed().hasPlayer(uuid)) {
                tRed.addEntry(entry);
            } else if (session.getTeamBlue().hasPlayer(uuid)) {
                tBlue.addEntry(entry);
            }
        }
    }
}

