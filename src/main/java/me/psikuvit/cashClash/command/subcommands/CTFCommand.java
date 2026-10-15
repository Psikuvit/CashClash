package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CTFCommand extends AbstractArgCommand {

    public CTFCommand() {
        super("ctf", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        CashClashPlugin.getInstance().getQueueManager().setNextGamemode(GamemodeType.CAPTURE_THE_FLAG);
        Messages.send(sender, "admin.next-gamemode-set", "gamemode", GamemodeType.CAPTURE_THE_FLAG.getDisplayName());
        return true;
    }
}
