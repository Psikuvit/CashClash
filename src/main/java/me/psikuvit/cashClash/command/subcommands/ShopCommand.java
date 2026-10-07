package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * /cc shop - admin access to the main shop at any point of a game, skipping the rules that keep
 * players to the villager (buy phase only, locked during PTP buff selection).
 */
public class ShopCommand extends AbstractArgCommand {
    public ShopCommand() {
        super("shop", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        var sess = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "generic.player-not-in-game");
            player.closeInventory();
            return true;
        }

        new ShopGUI(player).open();
        return true;
    }
}

