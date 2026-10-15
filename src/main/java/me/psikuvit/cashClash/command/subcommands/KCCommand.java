package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class KCCommand extends AbstractArgCommand {

    public KCCommand() {
        super("kc", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        CashClashPlugin.getInstance().getQueueManager().setNextGamemode(GamemodeType.KILL_CONFIRM);
        Messages.send(sender, "admin.next-gamemode-set", "gamemode", GamemodeType.KILL_CONFIRM.getDisplayName());
        return true;
    }
}
