package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.game.ctf.FlagBannerUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /cc clearbanners - removes CTF flag banners left behind in the world the sender is in (the
 * lobby world when run from the console). Refused inside a running game's world, where the
 * banners are the live flags.
 */
public class ClearBannersCommand extends AbstractArgCommand {

    public ClearBannersCommand() {
        super("clearbanners", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        World world = targetWorld(sender);
        if (world == null) {
            Messages.send(sender, "lobby.clear-banners-no-world");
            return true;
        }

        for (GameSession session : CashClashPlugin.getInstance().getGameManager().getActiveSessions()) {
            if (world.equals(session.getGameWorld())) {
                Messages.send(sender, "lobby.clear-banners-in-game");
                return true;
            }
        }

        int removed = FlagBannerUtils.removeBannerEntities(world);
        Messages.send(sender, "lobby.clear-banners-done", "count", String.valueOf(removed), "world", world.getName());
        return true;
    }

    private World targetWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        Location lobby = CashClashPlugin.getInstance().getArenaManager().getServerLobbySpawn();
        return lobby != null ? lobby.getWorld() : null;
    }
}
