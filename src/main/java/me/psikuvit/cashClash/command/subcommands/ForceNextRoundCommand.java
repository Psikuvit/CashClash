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
    public boolean onCommand(@NotNull CommandSender sender, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) {
            Messages.send(sender, "generic.not-in-game");
            return true;
        }

        GameState state = session.getState();
        if (state == GameState.WAITING) {
            Messages.send(sender, "admin.forcenextround-not-started");
            return true;
        }

        if (state == GameState.ENDING) {
            Messages.send(sender, "admin.forcenextround-already-ending");
            return true;
        }

        int currentRound = session.getCurrentRound();
        int totalRounds = ConfigManager.getInstance().getTotalRounds();

        if (currentRound >= totalRounds) {
            Messages.send(sender, "admin.forcenextround-is-final-round");
            session.end();
            Messages.broadcast(session.getPlayers(), "admin.forcenextround-broadcast-game-ended");
            return true;
        }

        int nextRound = currentRound + 1;
        Messages.broadcast(session.getPlayers(), "admin.forcenextround-broadcast-advance",
                "next_round", String.valueOf(nextRound));

        session.nextRound();
        Messages.send(sender, "admin.forcenextround-success", "round", String.valueOf(nextRound));

        return true;
    }
}
