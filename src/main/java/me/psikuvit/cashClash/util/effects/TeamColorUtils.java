package me.psikuvit.cashClash.util.effects;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.enums.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Utility class for managing team colors in scoreboards (tab list and nametags).
 * Also handles glowing effect colors for teammate outlines.
 */
public final class TeamColorUtils {

    private static final String TEAM_RED_NAME = "cc_team_red";
    private static final String TEAM_BLUE_NAME = "cc_team_blue";
    private static final String TEAM_GLOW_GREEN_NAME = "cc_glow_green"; // For teammate outlines

    private TeamColorUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Setup red/blue teams on a scoreboard for tab list and nametag coloring.
     * Also setup green team for teammate glow outlines.
     *
     * @param board   the scoreboard to setup teams on
     */
    private static void setupTeams(Scoreboard board) {
        Team existingRed = board.getTeam(TEAM_RED_NAME);
        if (existingRed != null) existingRed.unregister();

        Team existingBlue = board.getTeam(TEAM_BLUE_NAME);
        if (existingBlue != null) existingBlue.unregister();

        Team existingGreen = board.getTeam(TEAM_GLOW_GREEN_NAME);
        if (existingGreen != null) existingGreen.unregister();

        Team teamRed = board.registerNewTeam(TEAM_RED_NAME);
        teamRed.color(NamedTextColor.RED);
        teamRed.prefix(Component.empty());

        Team teamBlue = board.registerNewTeam(TEAM_BLUE_NAME);
        teamBlue.color(NamedTextColor.BLUE);
        teamBlue.prefix(Component.empty());

        Team teamGreen = board.registerNewTeam(TEAM_GLOW_GREEN_NAME);
        teamGreen.color(NamedTextColor.GREEN);
        teamGreen.prefix(Component.empty());
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

    /**
     * Get the green team from the scoreboard for teammate glow outlines.
     * Creates it if it doesn't exist.
     */
    public static Team getGreenGlowTeam(Scoreboard board) {
        if (board == null) return null;

        Team greenTeam = board.getTeam(TEAM_GLOW_GREEN_NAME);
        if (greenTeam == null) {
            greenTeam = board.registerNewTeam(TEAM_GLOW_GREEN_NAME);
            greenTeam.color(NamedTextColor.GREEN);
            greenTeam.prefix(Component.empty());
        }
        return greenTeam;
    }

    /**
     * Add a player to the green glow team for teammate outlines.
     */
    public static void addToGreenGlowTeam(Scoreboard board, Player player) {
        if (board == null || player == null) return;
        Team greenTeam = getGreenGlowTeam(board);
        if (greenTeam != null) {
            greenTeam.addEntry(player.getName());
        }
    }

    /**
     * Takes a player out of the red, blue and green-glow teams on every online player's board,
     * so nobody - the player or anyone still in their old game - sees them in a team colour
     * once they're back in the lobby.
     */
    public static void removeFromTeamColors(Player player) {
        String entry = player.getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = viewer.getScoreboard();
            for (String teamName : new String[]{TEAM_RED_NAME, TEAM_BLUE_NAME, TEAM_GLOW_GREEN_NAME}) {
                Team team = board.getTeam(teamName);
                if (team != null) team.removeEntry(entry);
            }
        }
    }

    /**
     * A player's team colour for particles ({@link TeamColor#getParticleColor}), or
     * {@code fallback} when they aren't on a team in a game - the practice range, say.
     */
    public static Color particleColor(Player player, Color fallback) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (session == null) return fallback;

        UUID uuid = player.getUniqueId();
        if (session.getTeamRed().hasPlayer(uuid)) return TeamColor.RED.getParticleColor();
        if (session.getTeamBlue().hasPlayer(uuid)) return TeamColor.BLUE.getParticleColor();
        return fallback;
    }

    /**
     * Put a player back in their own team's colour on a scoreboard, taking them out of the green
     * glow team if they were in it (an entry belongs to one team per scoreboard).
     */
    public static void assignTeamColor(Scoreboard board, Player player, GameSession session) {
        if (board == null || player == null || session == null) return;

        UUID uuid = player.getUniqueId();
        String teamName = session.getTeamRed().hasPlayer(uuid) ? TEAM_RED_NAME
                : session.getTeamBlue().hasPlayer(uuid) ? TEAM_BLUE_NAME : null;
        Team team = teamName != null ? board.getTeam(teamName) : null;
        if (team != null) {
            team.addEntry(player.getName());
        }
    }
}

