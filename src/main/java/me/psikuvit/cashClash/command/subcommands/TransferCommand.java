package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.game.EconomyManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TransferCommand extends AbstractArgCommand {
    public TransferCommand() {
        super("transfer", Collections.emptyList(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, "transfer.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Messages.send(player, "generic.player-not-found-or-offline");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Messages.send(player, "generic.invalid-amount");
            return true;
        }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) { Messages.send(player, "generic.player-not-in-game"); return true; }

        CashClashPlayer senderCCP = session.getCashClashPlayer(player.getUniqueId());
        CashClashPlayer receiverCCP = session.getCashClashPlayer(target.getUniqueId());
        if (senderCCP == null || receiverCCP == null) {
            Messages.send(player, "generic.both-players-same-game");
            return true;
        }

        boolean ok = EconomyManager.transferMoney(senderCCP, receiverCCP, amount, session);
        if (!ok) {
            Messages.send(player, "transfer.insufficient-funds");
            return true;
        }

        Messages.send(player, "transfer.sent", "amount", String.valueOf(amount), "target", target.getName());
        Messages.send(target, "transfer.received",
                "amount", String.valueOf((long) (amount * (1 - EconomyManager.getTransferFee(session)))),
                "sender", player.getName());

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
        }

        if (args.length == 2) {
            return List.of("1000", "5000", "10000", "25000", "50000");
        }

        return Collections.emptyList();
    }
}
