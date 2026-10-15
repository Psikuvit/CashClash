package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PTPCommand extends AbstractArgCommand {

    public PTPCommand() {
        super("ptp", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        CashClashPlugin.getInstance().getQueueManager().setNextGamemode(GamemodeType.PROTECT_THE_PRESIDENT);
        Messages.send(sender, "admin.next-gamemode-set", "gamemode", GamemodeType.PROTECT_THE_PRESIDENT.getDisplayName());
        return true;
    }
}
