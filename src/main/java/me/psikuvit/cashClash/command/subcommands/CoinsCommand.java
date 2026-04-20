package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collections;

public class CoinsCommand extends AbstractArgCommand {

    public CoinsCommand() {
        super("coins", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NonNull @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "generic.player-not-in-game");
            return true;
        }

        if (session.getState() == GameState.WAITING) {
            Messages.send(sender, "generic.game-started");
            return true;
        }

        long amount;

        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            Messages.send(sender, "generic.invalid-amount-number");
            return true;
        }

        session.getCashClashPlayer(player.getUniqueId()).addCoins(amount);


        return true;
    }
}
