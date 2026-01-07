package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.command.subcommands.ArenaCommand;
import me.psikuvit.cashClash.command.subcommands.ArenasCommand;
import me.psikuvit.cashClash.command.subcommands.CoinsCommand;
import me.psikuvit.cashClash.command.subcommands.DebugCommand;
import me.psikuvit.cashClash.command.subcommands.ForceStartCommand;
import me.psikuvit.cashClash.command.subcommands.ForfeitCommand;
import me.psikuvit.cashClash.command.subcommands.JoinCommand;
import me.psikuvit.cashClash.command.subcommands.LayoutCommand;
import me.psikuvit.cashClash.command.subcommands.LeaveCommand;
import me.psikuvit.cashClash.command.subcommands.LotteryCommand;
import me.psikuvit.cashClash.command.subcommands.MythicsCommand;
import me.psikuvit.cashClash.command.subcommands.PayTaxCommand;
import me.psikuvit.cashClash.command.subcommands.ReloadCommand;
import me.psikuvit.cashClash.command.subcommands.SetLobbyCommand;
import me.psikuvit.cashClash.command.subcommands.ShopCommand;
import me.psikuvit.cashClash.command.subcommands.StatsCommand;
import me.psikuvit.cashClash.command.subcommands.StopCommand;
import me.psikuvit.cashClash.command.subcommands.TemplateCommand;
import me.psikuvit.cashClash.command.subcommands.TransferCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class CommandHandler extends Command {

    private final Map<String, AbstractArgCommand> subcommands;

    public CommandHandler() {
        super("cashclash");
        this.subcommands = new LinkedHashMap<>();
        setAliases(List.of("cc"));

        registerDefaults();
    }

    private void registerDefaults() {
        // Core user commands
        registerSubcommand(new JoinCommand());
        registerSubcommand(new LeaveCommand());
        registerSubcommand(new ShopCommand());
        registerSubcommand(new StatsCommand());
        registerSubcommand(new ForfeitCommand());
        registerSubcommand(new LayoutCommand());

        // Cash Quake event commands
        registerSubcommand(new LotteryCommand());
        registerSubcommand(new PayTaxCommand());

        // Admin / control commands
        registerSubcommand(new StopCommand());
        registerSubcommand(new ForceStartCommand());
        registerSubcommand(new TransferCommand());
        registerSubcommand(new SetLobbyCommand());
        registerSubcommand(new ReloadCommand());
        registerSubcommand(new DebugCommand());

        // Arena/template admin commands
        registerSubcommand(new ArenasCommand());
        registerSubcommand(new TemplateCommand());
        registerSubcommand(new ArenaCommand());
        registerSubcommand(new StatsCommand());
        registerSubcommand(new CoinsCommand());
        registerSubcommand(new MythicsCommand());
    }

    public void registerSubcommand(AbstractArgCommand cmd) {
        subcommands.put(cmd.getName().toLowerCase(Locale.ROOT), cmd);
        for (String a : cmd.getAliases()) subcommands.put(a.toLowerCase(Locale.ROOT), cmd);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String s, @NotNull String @NonNull [] args) {
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
        Messages.debug(sender instanceof Player ? (Player) sender : null, "COMMAND", "Executing subcommand: " + token + " with args: " + Arrays.toString(subArgs));
        return sub.onCommand(sender, subArgs);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String @NonNull [] args) {
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
        Messages.send(sender, "<yellow>/cc layout <gray>- Customize your kit item layout</gray>");
        if (sender.hasPermission("cashclash.admin")) {
            Messages.send(sender, "<gray>--- Admin Commands ---</gray>");
            Messages.send(sender, "<yellow>/cc arena <gray>- Arena admin actions (tp,set,assign)</gray>");
            Messages.send(sender, "<yellow>/cc template <gray>- Template world management</gray>");
            Messages.send(sender, "<yellow>/cc stop <gray>- Stop an ongoing game</gray>");
            Messages.send(sender, "<yellow>/cc forcestart <gray>- Force start a game immediately</gray>");
            Messages.send(sender, "<yellow>/cc setlobby <gray>- Set the lobby spawn point</gray>");
            Messages.send(sender, "<yellow>/cc reload [config|shop|items|all] <gray>- Reload configs</gray>");
            Messages.send(sender, "<yellow>/cc debug <gray>- Toggle debug mode</gray>");
        }
        Messages.send(sender, "<yellow>/cc shop <gray>- Open the in-game shop</gray>");
    }
}
