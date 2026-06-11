package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.GamemodeManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PTPCommand extends AbstractArgCommand {

    public PTPCommand() {
        super("ptp", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            Messages.send(sender, "generic.not-in-game");
            return true;
        }

        GamemodeManager.getInstance().setNextGamemode(session.getSessionId(), GamemodeType.PROTECT_THE_PRESIDENT);
        sender.sendMessage(Messages.parse("<green>Next gamemode for this session set to Protect the President.</green>"));
        return true;
    }
}
