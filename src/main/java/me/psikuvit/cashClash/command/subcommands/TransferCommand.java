package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.manager.EconomyManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class TransferCommand extends AbstractArgCommand {
    public TransferCommand() {
        super("transfer", List.of(), null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, "<red>Usage: /cc transfer <player> <amount></red>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Messages.send(player, "<red>Player not found or not online.</red>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Messages.send(player, "<red>Invalid amount.</red>");
            return true;
        }

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) { Messages.send(player, "<red>You're not in a game.</red>"); return true; }

        CashClashPlayer senderCCP = session.getCashClashPlayer(player.getUniqueId());
        CashClashPlayer receiverCCP = session.getCashClashPlayer(target.getUniqueId());
        if (senderCCP == null || receiverCCP == null) {
            Messages.send(player, "<red>Both players must be in the same game.</red>");
            return true;
        }

        boolean ok = EconomyManager.transferMoney(senderCCP, receiverCCP, amount, session);
        if (!ok) {
            Messages.send(player, "<red>You don't have enough coins.</red>");
            return true;
        }

        Messages.send(player, "<green>Transferred " +
                amount + " coins to " + target.getName()
                + " (fee applied).</green>");
        Messages.send(target, "<green>You received " +
                (long)(amount * (1 - EconomyManager.getTransferFee(session)))
                + " coins from " + player.getName() + "</green>");

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();

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

        return List.of();
    }
}
