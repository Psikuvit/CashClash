package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.GuideBooks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Standalone /guidebook command - gives back the gamemode guide book: the current game's, or the
 * one named ({@code ctf}, {@code kc}, {@code ptp}). Anyone can use it.
 */
public class GuideBookCommand extends Command {

    private static final Map<String, GamemodeType> MODES = Map.of(
            "ctf", GamemodeType.CAPTURE_THE_FLAG,
            "kc", GamemodeType.KILL_CONFIRM,
            "ptp", GamemodeType.PROTECT_THE_PRESIDENT);

    public GuideBookCommand() {
        super("guidebook");
        setDescription("Get the gamemode guide book back");
        setUsage("/<command> [ctf|kc|ptp]");
        setPermission("cashclash.use");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull @NonNull String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return true;
        }

        GamemodeType type = args.length > 0 ? MODES.get(args[0].toLowerCase(Locale.ROOT)) : currentGamemode(player);
        if (type == null) {
            Messages.send(player, "guide-book.usage");
            return true;
        }

        GuideBooks.give(player, type);
        Messages.send(player, "guide-book.given", "gamemode", type.getDisplayName());
        return true;
    }

    private GamemodeType currentGamemode(Player player) {
        GameSession session = CashClashPlugin.getInstance().getGameManager().getPlayerSession(player);
        return session != null && session.getGamemode() != null ? session.getGamemode().getType() : null;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NonNull [] args) {
        if (args.length != 1) return List.of();
        String last = args[0].toLowerCase(Locale.ROOT);
        return MODES.keySet().stream().filter(mode -> mode.startsWith(last)).sorted().toList();
    }
}
