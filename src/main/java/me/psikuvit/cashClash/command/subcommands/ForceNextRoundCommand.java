package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Admin command to force skip to the next round immediately.
 * Works during shopping or combat phases.
 */
public class ForceNextRoundCommand extends AbstractArgCommand {

    public ForceNextRoundCommand() {
        super("forcenextround", List.of("fnr", "nextround", "skipround"), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "<red>You're not in a game session. Join an arena first.</red>");
            return true;
        }

        GameState state = session.getState();
        if (state == GameState.WAITING) {
            Messages.send(sender, "<red>The game hasn't started yet! Use /cc forcestart instead.</red>");
            return true;
        }

        if (state == GameState.ENDING) {
            Messages.send(sender, "<red>The game is already ending.</red>");
            return true;
        }

        int currentRound = session.getCurrentRound();
        int totalRounds = ConfigManager.getInstance().getTotalRounds();

        if (currentRound >= totalRounds) {
            Messages.send(sender, "<yellow>This is the final round. Ending game instead.</yellow>");
            session.end();
            Messages.broadcast(session.getPlayers(), "<gold><bold>Game ended by an admin!</bold></gold>");
            return true;
        }

        // Force transition to next round
        Messages.broadcast(session.getPlayers(), "<gold><bold>Round skipped by an admin! Moving to Round " + (currentRound + 1) + "!</bold></gold>");

        // Trigger next round (this will handle cleanup and setup)
        session.nextRound();

        Messages.send(sender, "<green>Successfully skipped to round " + (currentRound + 1) + ".</green>");

        return true;
    }
}
