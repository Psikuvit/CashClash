package me.psikuvit.cashClash.scoreboard.context;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Abstract base class for game scoreboard contexts
 * Handles common game data (round, phase, time, coins, alive counts, etc)
 */
public abstract class GameScoreboardContext implements ScoreboardContext {

    @Override
    public Component getTitle(Player player, GameSession session) {
        if (session == null) {
            return Messages.parse("<gold><bold>Cash Clash</bold></gold>");
        }
        String titleRaw = ConfigManager.getInstance().getGameScoreboardTitle();
        return Messages.parse(fillPlaceholders(titleRaw, player, session));
    }

    @Override
    public List<String> getLines(Player player, GameSession session) {
        // Default to config lines, can be overridden by subclasses
        return ConfigManager.getInstance().getGameScoreboardLines();
    }

    @Override
    public String fillPlaceholders(String line, Player player, GameSession session) {
        if (session == null) {
            return line;
        }

        // Common game data - Round, Phase, Time
        line = line.replace("{round}", String.valueOf(session.getCurrentRound()));
        GameState state = session.getState();
        line = line.replace("{state}", getPhase(state));
        line = line.replace("{phase}", getPhase(state));
        line = line.replace("{phase_number}", String.valueOf(session.getCurrentRound()));

        int timeRemaining = session.getTimeRemaining();
        line = line.replace("{time}", formatTime(timeRemaining));
        line = line.replace("{time_seconds}", String.valueOf(timeRemaining));

        // Team coins
        line = line.replace("{teamRed_coins}", formatCoins(session.getTeamRedCoins()));
        line = line.replace("{teamBlue_coins}", formatCoins(session.getTeamBlueCoins()));

        // Player team data
        Team playerTeam = session.getPlayerTeam(player);
        if (playerTeam != null) {
            line = line.replace("{your_team}", String.valueOf(playerTeam.getTeamNumber()));
            long teamCoins = playerTeam.getTeamNumber() == 1 ? session.getTeamRedCoins() : session.getTeamBlueCoins();
            line = line.replace("{your_team_coins}", formatCoins(teamCoins));
            line = line.replace("{team_ready}", playerTeam.isTeamReady() ? "Yes" : "No");

            Team enemyTeam = session.getOpposingTeam(playerTeam);
            line = line.replace("{enemy_team}", String.valueOf(enemyTeam.getTeamNumber()));
            long enemyCoins = enemyTeam.getTeamNumber() == 1 ? session.getTeamRedCoins() : session.getTeamBlueCoins();
            line = line.replace("{enemy_team_coins}", formatCoins(enemyCoins));
            line = line.replace("{enemy_team_ready}", enemyTeam.isTeamReady() ? "Yes" : "No");
        } else {
            line = line.replace("{your_team}", "?");
            line = line.replace("{your_team_coins}", "0");
            line = line.replace("{team_ready}", "?");
            line = line.replace("{enemy_team}", "?");
            line = line.replace("{enemy_team_coins}", "0");
            line = line.replace("{enemy_team_ready}", "?");
        }

        // Player-specific data
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp != null) {
            line = line.replace("{player_coins}", formatCoins(ccp.getCoins()));
            line = line.replace("{player_kills}", String.valueOf(ccp.getTotalKills()));
            line = line.replace("{player_lives}", String.valueOf(ccp.getLives()));
            line = line.replace("{player_deaths}", String.valueOf(ccp.getDeathsThisRound()));
            line = line.replace("{kill_streak}", String.valueOf(ccp.getKillStreak()));
        } else {
            line = line.replace("{player_coins}", "0");
            line = line.replace("{player_kills}", "0");
            line = line.replace("{player_lives}", "0");
            line = line.replace("{player_deaths}", "0");
            line = line.replace("{kill_streak}", "0");
        }

        // Round-specific data
        RoundData roundData = session.getCurrentRoundData();
        if (roundData != null) {
            line = line.replace("{round_kills}", String.valueOf(roundData.getKills(player.getUniqueId())));

            Team teamRed = session.getTeamRed();
            Team teamBlue = session.getTeamBlue();
            long teamRedAlive = teamRed.getPlayers().stream().filter(roundData::isAlive).count();
            long teamBlueAlive = teamBlue.getPlayers().stream().filter(roundData::isAlive).count();
            line = line.replace("{teamRed_alive}", String.valueOf(teamRedAlive));
            line = line.replace("{teamBlue_alive}", String.valueOf(teamBlueAlive));

            if (playerTeam != null) {
                long yourAlive = playerTeam.getPlayers().stream().filter(roundData::isAlive).count();
                long enemyAlive = session.getOpposingTeam(playerTeam).getPlayers().stream().filter(roundData::isAlive).count();
                line = line.replace("{your_team_alive}", String.valueOf(yourAlive));
                line = line.replace("{enemy_team_alive}", String.valueOf(enemyAlive));
            } else {
                line = line.replace("{your_team_alive}", "0");
                line = line.replace("{enemy_team_alive}", "0");
            }
        } else {
            line = line.replace("{round_kills}", "0");
            line = line.replace("{teamRed_alive}", "0");
            line = line.replace("{teamBlue_alive}", "0");
            line = line.replace("{your_team_alive}", "0");
            line = line.replace("{enemy_team_alive}", "0");
        }

        line = line.replace("{players}", String.valueOf(session.getPlayers().size()));

        // Call subclass for gamemode-specific placeholders
        return fillGamemodeSpecificPlaceholders(line, player, session);
    }

    /**
     * Override in subclasses to add gamemode-specific placeholder filling
     */
    protected abstract String fillGamemodeSpecificPlaceholders(String line, Player player, GameSession session);

    /**
     * Get the phase type (Shopping or Combat)
     */
    private String getPhase(GameState state) {
        if (state == null) {
            return "Unknown";
        }
        String name = state.name();
        if (name.contains("SHOPPING")) {
            return "Shopping";
        } else if (name.contains("COMBAT")) {
            return "Combat";
        } else if (name.equals("WAITING")) {
            return "Waiting";
        } else {
            return "Ending";
        }
    }

    /**
     * Format time in MM:SS format
     */
    protected String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Format coins with commas
     */
    protected String formatCoins(long coins) {
        return String.format("%,d", coins);
    }

    @Override
    public ContextType getContextType() {
        return ContextType.GAME_DEFAULT;
    }
}

