package me.psikuvit.cashClash.scoreboard;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.scoreboard.context.ContextType;
import me.psikuvit.cashClash.scoreboard.context.ScoreboardContext;
import me.psikuvit.cashClash.scoreboard.placeholder.PlaceholderRegistry;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Factory class for selecting and providing appropriate scoreboard contexts
 * Auto-detects player state (lobby or game) and creates appropriate context
 */
public class ScoreboardProvider {

    private static final ScoreboardContext LOBBY_CONTEXT = new ScoreboardContext() {
        @Override
        public Component getTitle(Player player, GameSession session) {
            String titleRaw = CashClashPlugin.getInstance().getConfigManager().getLobbyScoreboardTitle();
            return Messages.parse(fillPlaceholders(titleRaw, player, session));
        }

        @Override
        public List<String> getLines(Player player, GameSession session) {
            return CashClashPlugin.getInstance().getConfigManager().getLobbyScoreboardLines();
        }

        @Override
        public String fillPlaceholders(String line, Player player, GameSession session) {
            PlaceholderRegistry registry = PlaceholderRegistry.forLobby();
            return registry.fillPlaceholders(line, player);
        }

        @Override
        public ContextType getContextType() {
            return ContextType.LOBBY;
        }
    };

    /**
     * Get the appropriate scoreboard context for a player
     */
    public static ScoreboardContext getContext(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);

        if (session == null) {
            return LOBBY_CONTEXT;
        }

        // Check if in sudden death
        boolean inSuddenDeath = session.getGamemode().getSuddenDeathManager().isInSuddenDeath();

        return new ScoreboardContext() {
            @Override
            public Component getTitle(Player player, GameSession session) {
                String configTitle;
                if (inSuddenDeath) {
                    configTitle = switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> CashClashPlugin.getInstance().getConfigManager().getCTFSuddenDeathScoreboardTitle();
                        case PROTECT_THE_PRESIDENT -> CashClashPlugin.getInstance().getConfigManager().getPTPSuddenDeathScoreboardTitle();
                        case KILL_CONFIRM -> CashClashPlugin.getInstance().getConfigManager().getKCSuddenDeathScoreboardTitle();
                    };
                } else {
                    configTitle = switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> CashClashPlugin.getInstance().getConfigManager().getCTFScoreboardTitle();
                        case PROTECT_THE_PRESIDENT -> CashClashPlugin.getInstance().getConfigManager().getPTPScoreboardTitle();
                        case KILL_CONFIRM -> CashClashPlugin.getInstance().getConfigManager().getKCScoreboardTitle();
                    };
                }
                String filled = fillPlaceholders(configTitle, player, session);
                return Messages.parse(filled);
            }

            @Override
            public List<String> getLines(Player player, GameSession session) {
                if (inSuddenDeath) {
                    return switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> CashClashPlugin.getInstance().getConfigManager().getCTFSuddenDeathScoreboardLines();
                        case PROTECT_THE_PRESIDENT -> CashClashPlugin.getInstance().getConfigManager().getPTPSuddenDeathScoreboardLines();
                        case KILL_CONFIRM -> CashClashPlugin.getInstance().getConfigManager().getKCSuddenDeathScoreboardLines();
                    };
                } else {
                    return switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> CashClashPlugin.getInstance().getConfigManager().getCTFScoreboardLines();
                        case PROTECT_THE_PRESIDENT -> CashClashPlugin.getInstance().getConfigManager().getPTPScoreboardLines();
                        case KILL_CONFIRM -> CashClashPlugin.getInstance().getConfigManager().getKCScoreboardLines();
                    };
                }
            }

            @Override
            public String fillPlaceholders(String line, Player player, GameSession session) {
                if (session == null || player == null) {
                    return line;
                }
                PlaceholderRegistry registry = PlaceholderRegistry.forGameSession(session, player);
                return registry.fillPlaceholders(line, player);
            }

            @Override
            public ContextType getContextType() {
                if (inSuddenDeath) {
                    return switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> ContextType.CTF_SUDDEN_DEATH;
                        case PROTECT_THE_PRESIDENT -> ContextType.PTP_SUDDEN_DEATH;
                        case KILL_CONFIRM -> ContextType.KILL_CONFIRM_SUDDEN_DEATH;
                    };
                } else {
                    return switch (session.getGamemode().getType()) {
                        case CAPTURE_THE_FLAG -> ContextType.CTF;
                        case PROTECT_THE_PRESIDENT -> ContextType.PTP;
                        case KILL_CONFIRM -> ContextType.KILL_CONFIRM;
                    };
                }
            }
        };
    }

    /**
     * Check if a player's context changed
     */
    public static boolean hasContextChanged(Player player, ContextType previousContext) {
        ScoreboardContext currentContext = getContext(player);

        if (previousContext == null) {
            return true;
        }

        return previousContext != currentContext.getContextType();
    }
}

