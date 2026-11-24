package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.TeamColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/**
 * Simple manager to centralize tab-list behavior per context (lobby / game session).
 *
 * It keeps tab appearance consistent by setting the player's playerListName component
 * and ensuring the current scoreboard contains team entries for coloring.
 */
public final class TabListManager implements Shutdownable {

    private static final List<String> SESSION_PREFIXES = List.of("[R] ", "[B] ", "[?] ");

    private final GameManager gameManager;
    private BukkitTask leftoverCheck;

    public TabListManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Starts the once-a-second check for game tab names left on players out of every game
     * ({@link #resetLeftoverSessionNames}).
     */
    public void start() {
        leftoverCheck = SchedulerUtils.runTaskTimer(this::resetLeftoverSessionNames, 20L, 20L);
    }

    /**
     * Configure a player to show the lobby-style tab: their display name in gold, with no team
     * prefix or team colour left over from a game - including on the boards of anyone still in
     * that game.
     */
    public void setPlayerToLobby(Player player) {
        if (player == null || !player.isOnline()) return;

        player.playerListName(Component.text().color(NamedTextColor.GOLD).append(player.displayName()).build());
        TeamColorUtils.removeFromTeamColors(player);
    }

    /**
     * Configure a player to the session tab: [R]/[B] and their name in their team's colour. A
     * custom tab name isn't recoloured by scoreboard teams, so it stays in the team colour
     * whatever team a viewer's board puts the player in - e.g. teammates' green outline team.
     */
    public void setPlayerToSession(Player player, GameSession session) {
        if (player == null || session == null || !player.isOnline()) return;

        int teamNumber = 0;
        if (session.getTeamRed().hasPlayer(player.getUniqueId())) teamNumber = 1;
        else if (session.getTeamBlue().hasPlayer(player.getUniqueId())) teamNumber = 2;

        String prefix = teamNumber == 1 ? "[R] " : teamNumber == 2 ? "[B] " : "[?] ";
        NamedTextColor color = teamNumber == 1 ? NamedTextColor.RED : teamNumber == 2 ? NamedTextColor.BLUE : NamedTextColor.GRAY;
        Component comp = Component.text().color(color).append(Component.text(prefix)).append(player.displayName()).build();
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

    /**
     * Anyone out of every game whose tab name still starts with a game's [R]/[B]/[?] gets the
     * lobby look back. Every way out of a game resets it already; this catches whatever puts a
     * game name back on a player after that, within a second.
     */
    private void resetLeftoverSessionNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (gameManager.getPlayerSession(player) != null) continue;

            String shown = PlainTextComponentSerializer.plainText().serialize(player.playerListName());
            if (SESSION_PREFIXES.stream().noneMatch(shown::startsWith)) continue;

            Messages.debug(player, Messages.DebugCategory.GAME, "Game tab name left on a player out of every game: \"" + shown + "\" - reset");
            setPlayerToLobby(player);
        }
    }

    @Override
    public void shutdown() {
        if (leftoverCheck != null) leftoverCheck.cancel();
    }
}
