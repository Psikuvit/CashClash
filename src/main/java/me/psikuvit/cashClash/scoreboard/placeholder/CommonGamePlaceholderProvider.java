package me.psikuvit.cashClash.scoreboard.placeholder;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.player.CashClashPlayer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides common game placeholders for all gamemodes
 * Handles: phase, time, coins, team data, player data, etc.
 */
public class CommonGamePlaceholderProvider implements PlaceholderProvider {

    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>();

    static {
        // Time placeholders
        SUPPORTED_PLACEHOLDERS.add("phase");
        SUPPORTED_PLACEHOLDERS.add("state");
        SUPPORTED_PLACEHOLDERS.add("phase_number");
        SUPPORTED_PLACEHOLDERS.add("time");
        SUPPORTED_PLACEHOLDERS.add("time_seconds");

        // Team coin placeholders
        SUPPORTED_PLACEHOLDERS.add("teamRed_coins");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_coins");

        // Player team data
        SUPPORTED_PLACEHOLDERS.add("your_team");
        SUPPORTED_PLACEHOLDERS.add("your_team_coins");
        SUPPORTED_PLACEHOLDERS.add("your_team_ready");
        SUPPORTED_PLACEHOLDERS.add("enemy_team");
        SUPPORTED_PLACEHOLDERS.add("enemy_team_coins");
        SUPPORTED_PLACEHOLDERS.add("enemy_team_ready");

        // Player-specific data
        SUPPORTED_PLACEHOLDERS.add("player_coins");
        SUPPORTED_PLACEHOLDERS.add("player_kills");
        SUPPORTED_PLACEHOLDERS.add("player_lives");
        SUPPORTED_PLACEHOLDERS.add("player_deaths");
        SUPPORTED_PLACEHOLDERS.add("kill_streak");

        // Round data
        SUPPORTED_PLACEHOLDERS.add("round_kills");
        SUPPORTED_PLACEHOLDERS.add("teamRed_alive");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_alive");
        SUPPORTED_PLACEHOLDERS.add("your_team_alive");
        SUPPORTED_PLACEHOLDERS.add("enemy_team_alive");
        SUPPORTED_PLACEHOLDERS.add("round");
        SUPPORTED_PLACEHOLDERS.add("players");

        // Round wins (objectives completed)
        SUPPORTED_PLACEHOLDERS.add("round_won");
        SUPPORTED_PLACEHOLDERS.add("your_team_wins");
        SUPPORTED_PLACEHOLDERS.add("enemy_team_wins");

        // CTF captures
        SUPPORTED_PLACEHOLDERS.add("teamRed_captures");
        SUPPORTED_PLACEHOLDERS.add("teamBlue_captures");

        // Sudden death heart timer
        SUPPORTED_PLACEHOLDERS.add("player_heart_timer");
        SUPPORTED_PLACEHOLDERS.add("sudden_death_timer");
        SUPPORTED_PLACEHOLDERS.add("sudden_death_cycle");
    }

    private final GameSession session;

    public CommonGamePlaceholderProvider(GameSession session) {
        this.session = session;
    }

    @Override
    public String getValue(String placeholder, Player player) {
        if (session == null) {
            return getDefaultValue(placeholder);
        }

        return switch (placeholder) {
            // Time placeholders
            case "phase", "state" -> getPhase(session.getState());
            case "phase_number", "round" -> String.valueOf(session.getCurrentRound());
            case "time" -> formatTime(session.getTimeRemaining());
            case "time_seconds" -> String.valueOf(session.getTimeRemaining());

            // Team coin placeholders
            case "teamRed_coins" -> formatCoins(session.getTeamRedCoins());
            case "teamBlue_coins" -> formatCoins(session.getTeamBlueCoins());

            // Player team data
            case "your_team", "your_team_coins", "your_team_ready",
                 "enemy_team", "enemy_team_coins", "enemy_team_ready" ->
                    getPlayerTeamData(placeholder, player, session);

            // Player-specific data
            case "player_coins", "player_kills", "player_lives", "player_deaths", "kill_streak" ->
                    getPlayerData(placeholder, player, session);

            // Round data
            case "round_kills", "teamRed_alive", "teamBlue_alive", "your_team_alive", "enemy_team_alive" ->
                    getRoundData(placeholder, player, session);

            case "players" -> String.valueOf(session.getPlayers().size());

            // Round wins
            case "round_won" -> session.getRoundWins(1) + " - " + session.getRoundWins(2);
            case "your_team_wins" -> {
                Team playerTeam = session.getPlayerTeam(player);
                if (playerTeam == null) {
                    yield "0";
                }
                yield String.valueOf(session.getRoundWins(playerTeam.getTeamNumber()));
            }
            case "enemy_team_wins" -> {
                Team playerTeam = session.getPlayerTeam(player);
                if (playerTeam == null) {
                    yield "0";
                }
                Team enemyTeam = session.getOpposingTeam(playerTeam);
                yield String.valueOf(session.getRoundWins(enemyTeam.getTeamNumber()));
            }

            // CTF captures
            case "teamRed_captures" -> String.valueOf(session.getRoundWins(1));
            case "teamBlue_captures" -> String.valueOf(session.getRoundWins(2));

            // Sudden death heart timer - placeholder for use in CTF
            case "player_heart_timer" -> formatMillis(session.getGamemode() == null ? -1 : session.getGamemode().getExtraHeartRemainingMs(player.getUniqueId()));
            case "sudden_death_timer" -> {
                int remaining = session.getGamemode() == null ? -1 : session.getGamemode().getSuddenDeathTimerRemainingSeconds();
                yield remaining < 0 ? "" : formatTime(remaining);
            }
            case "sudden_death_cycle" -> {
                int cycle = session.getGamemode() == null ? 0 : session.getGamemode().getSuddenDeathCycle();
                yield cycle <= 0 ? "" : String.valueOf(cycle);
            }

            default -> null;
        };
    }

    @Override
    public boolean handles(String placeholder) {
        return SUPPORTED_PLACEHOLDERS.contains(placeholder);
    }

    private String getPhase(GameState state) {
        if (state == null) return "Unknown";
        String name = state.name();
        if (name.contains("SHOPPING")) return "Shopping";
        if (name.contains("COMBAT")) return "Combat";
        if (name.equals("WAITING")) return "Waiting";
        return "Ending";
    }

    private String getPlayerTeamData(String placeholder, Player player, GameSession session) {
        Team playerTeam = session.getPlayerTeam(player);

        if (playerTeam == null) {
            return getDefaultPlayerTeamValue(placeholder);
        }

        return switch (placeholder) {
            case "your_team" -> String.valueOf(playerTeam.getTeamNumber());
            case "your_team_coins" -> {
                long coins = playerTeam.getTeamNumber() == 1 ? session.getTeamRedCoins() : session.getTeamBlueCoins();
                yield formatCoins(coins);
            }
            case "your_team_ready" -> playerTeam.isTeamReady() ? "Yes" : "No";
            case "enemy_team" -> {
                Team enemyTeam = session.getOpposingTeam(playerTeam);
                yield String.valueOf(enemyTeam.getTeamNumber());
            }
            case "enemy_team_coins" -> {
                Team enemyTeam = session.getOpposingTeam(playerTeam);
                long coins = enemyTeam.getTeamNumber() == 1 ? session.getTeamRedCoins() : session.getTeamBlueCoins();
                yield formatCoins(coins);
            }
            case "enemy_team_ready" -> {
                Team enemyTeam = session.getOpposingTeam(playerTeam);
                yield enemyTeam.isTeamReady() ? "Yes" : "No";
            }
            default -> null;
        };
    }

    private String getDefaultPlayerTeamValue(String placeholder) {
        return switch (placeholder) {
            case "your_team", "enemy_team", "your_team_ready", "enemy_team_ready" -> "?";
            case "your_team_coins", "enemy_team_coins" -> "0";
            default -> null;
        };
    }

    private String getPlayerData(String placeholder, Player player, GameSession session) {
        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());

        if (ccp == null) {
            return getDefaultPlayerValue(placeholder);
        }

        return switch (placeholder) {
            case "player_coins" -> formatCoins(ccp.getCoins());
            case "player_kills" -> String.valueOf(ccp.getTotalKills());
            case "player_lives" -> String.valueOf(ccp.getLives());
            case "player_deaths" -> String.valueOf(ccp.getDeathsThisRound());
            case "kill_streak" -> String.valueOf(ccp.getKillStreak());
            default -> null;
        };
    }

    private String getDefaultPlayerValue(String placeholder) {
        return switch (placeholder) {
            case "player_coins", "player_kills", "player_lives", "player_deaths", "kill_streak" -> "0";
            default -> null;
        };
    }

    private String getRoundData(String placeholder, Player player, GameSession session) {
        RoundData roundData = session.getCurrentRoundData();

        if (roundData == null) {
            return getDefaultRoundValue(placeholder);
        }

        return switch (placeholder) {
            case "round_kills" -> String.valueOf(roundData.getKills(player.getUniqueId()));
            case "teamRed_alive" -> {
                Team teamRed = session.getTeamRed();
                long alive = teamRed.getPlayers().stream().filter(roundData::isAlive).count();
                yield String.valueOf(alive);
            }
            case "teamBlue_alive" -> {
                Team teamBlue = session.getTeamBlue();
                long alive = teamBlue.getPlayers().stream().filter(roundData::isAlive).count();
                yield String.valueOf(alive);
            }
            case "your_team_alive", "enemy_team_alive" -> {
                Team playerTeam = session.getPlayerTeam(player);
                if (playerTeam == null) {
                    yield "0";
                }
                if (placeholder.equals("your_team_alive")) {
                    long alive = playerTeam.getPlayers().stream().filter(roundData::isAlive).count();
                    yield String.valueOf(alive);
                } else {
                    Team enemyTeam = session.getOpposingTeam(playerTeam);
                    long alive = enemyTeam.getPlayers().stream().filter(roundData::isAlive).count();
                    yield String.valueOf(alive);
                }
            }
            default -> null;
        };
    }

    private String getDefaultRoundValue(String placeholder) {
        return switch (placeholder) {
            case "round_kills", "teamRed_alive", "teamBlue_alive", "your_team_alive", "enemy_team_alive" -> "0";
            default -> null;
        };
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    private String formatMillis(long millis) {
        if (millis <= 0) {
            return "0:00";
        }
        return formatTime((int) Math.ceil(millis / 1000.0));
    }

    private String formatCoins(long coins) {
        return String.format("%,d", coins);
    }

    private String getDefaultValue(String placeholder) {
        return switch (placeholder) {
            case "time" -> "0:00";
            case "time_seconds", "phase_number", "round", "players", "teamRed_coins", "teamBlue_coins",
                 "your_team_coins", "enemy_team_coins", "player_coins", "your_team_wins", "enemy_team_wins" -> "0";
            case "phase", "state" -> "Unknown";
            case "round_won" -> "0 - 0";
            default -> null;
        };
    }
}

