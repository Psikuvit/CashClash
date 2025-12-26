package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Simple manager to centralize tab-list behavior per context (lobby / game session).
 *
 * It keeps tab appearance consistent by setting the player's playerListName component
 * and ensuring the current scoreboard contains team entries for coloring.
 */
public final class TabListManager {

    private static TabListManager instance;

    private TabListManager() {}

    public static TabListManager getInstance() {
        if (instance == null) instance = new TabListManager();
        return instance;
    }

    /**
     * Configure a player to show the lobby-style tab.
     * This keeps their scoreboard (sidebar) but updates their playerListName and
     * ensures the scoreboard has at least an empty team setup.
     */
    public void setPlayerToLobby(Player player) {
        if (player == null || !player.isOnline()) return;

        Component comp = Messages.parse("<gold>" + player.getName() + "</gold>");
        player.playerListName(comp);

        Scoreboard board = player.getScoreboard();
        TeamColorUtils.assignPlayersToTeams(board, null);
    }

    /**
     * Configure a player to the session tab (team colored, session entries present).
     */
    public void setPlayerToSession(Player player, GameSession session) {
        if (player == null || session == null || !player.isOnline()) return;

        int teamNumber = 0;
        if (session.getTeam1().hasPlayer(player.getUniqueId())) teamNumber = 1;
        else if (session.getTeam2().hasPlayer(player.getUniqueId())) teamNumber = 2;

        String prefix = teamNumber == 1 ? "<red>[R] </red>" : teamNumber == 2 ? "<blue>[B] </blue>" : "<gray>[?] </gray>";
        Component comp = Messages.parse(prefix + player.getName());
        player.playerListName(comp);

        Scoreboard board = player.getScoreboard();
        TeamColorUtils.assignPlayersToTeams(board, session);
    }

    /**
     * Reset player list name to default (null) and remove session-specific decorations.
     */
    public void resetPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        player.playerListName(null);
    }
}

