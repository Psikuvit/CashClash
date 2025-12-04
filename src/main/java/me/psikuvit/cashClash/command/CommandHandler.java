package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.command.subcommands.*;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class CommandHandler extends Command {

    private final Map<String, AbstractArgCommand> subcommands = new LinkedHashMap<>();

    public CommandHandler() {
        super("cashclash");
        setAliases(List.of("cc"));

        // Register built-in subcommands centrally in the handler so the main plugin class
        // doesn't need to know about every subcommand implementation.
        registerDefaults();
    }

    private void registerDefaults() {
        // Core user commands
        registerSubcommand(new JoinCommand());
        registerSubcommand(new LeaveCommand());
        registerSubcommand(new ShopCommand());
        registerSubcommand(new StatsCommand());
        registerSubcommand(new ForfeitCommand());

        // Admin / control commands
        registerSubcommand(new StopCommand());
        registerSubcommand(new TransferCommand());
        registerSubcommand(new SetLobbyCommand());

        // Arena/template admin commands
        registerSubcommand(new ArenasCommand());
        registerSubcommand(new TemplateCommand());
        registerSubcommand(new ArenaCommand());
        registerSubcommand(new StatsCommand());
    }

    public void registerSubcommand(AbstractArgCommand cmd) {
        subcommands.put(cmd.getName().toLowerCase(Locale.ROOT), cmd);
        for (String a : cmd.getAliases()) subcommands.put(a.toLowerCase(Locale.ROOT), cmd);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        AbstractArgCommand sub = subcommands.get(token);
        if (sub == null) {
            sendHelp(sender);
            return true;
        }

        String perm = sub.getPermission();
        if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
            Messages.send(sender, "<red>You don't have permission to use this command.</red>");
            return true;
        }

        // slice args
        String[] subArgs = args.length == 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        return sub.onCommand(sender, subArgs);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return Collections.emptyList();
        String token = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (AbstractArgCommand c : new LinkedHashSet<>(subcommands.values())) {
                if (c.getPermission() != null && !c.getPermission().isBlank() && !sender.hasPermission(c.getPermission())) continue;
                if (c.getName().toLowerCase(Locale.ROOT).startsWith(token)) out.add(c.getName());
            }
            return out;
        }

        AbstractArgCommand sub = subcommands.get(token);
        if (sub == null) return Collections.emptyList();

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return sub.onTabComplete(sender, subArgs);
    }

    private void sendHelp(CommandSender sender) {
        Messages.send(sender, "<gold><bold>=== Cash Clash Commands ===</bold></gold>");
        Messages.send(sender, "<yellow>/cc arenas <gray>- Browse and join arenas</gray>");
        Messages.send(sender, "<yellow>/cc join <gray>- Join a game (quick match)</gray>");
        Messages.send(sender, "<yellow>/cc leave <gray>- Leave your current game</gray>");
        Messages.send(sender, "<yellow>/cc stats <gray>- View your stats</gray>");
        Messages.send(sender, "<yellow>/cc forfeit <gray>- Vote to forfeit the round</gray>");
        Messages.send(sender, "<yellow>/cc transfer <gray>- Transfer money to a teammate</gray>");
        Messages.send(sender, "<yellow>/cc arena <gray>- Arena admin actions (tp,set,assign)</gray>");
        Messages.send(sender, "<yellow>/cc template <gray>- Template world management (admin)</gray>");
        Messages.send(sender, "<yellow>/cc stop <gray>- Stop an ongoing game (admin)</gray>");
        Messages.send(sender, "<yellow>/cc setlobby <gray>- Set the lobby spawn point (admin)</gray>");
        Messages.send(sender, "<yellow>/cc shop <gray>- Open the in-game shop</gray>");
        Messages.send(sender, "<yellow>/cc stats- <gray>- View or reset player stats</gray>");
    }
}
