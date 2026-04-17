package me.psikuvit.cashClash.command;

import me.psikuvit.cashClash.command.subcommands.ArenaCommand;
import me.psikuvit.cashClash.command.subcommands.ArenasCommand;
import me.psikuvit.cashClash.command.subcommands.BlockDisplayCommand;
import me.psikuvit.cashClash.command.subcommands.ChatCommand;
import me.psikuvit.cashClash.command.subcommands.CoinsCommand;
import me.psikuvit.cashClash.command.subcommands.DebugCommand;
import me.psikuvit.cashClash.command.subcommands.ForceNextRoundCommand;
import me.psikuvit.cashClash.command.subcommands.ForceStartCommand;
import me.psikuvit.cashClash.command.subcommands.ForfeitCommand;
import me.psikuvit.cashClash.command.subcommands.JoinCommand;
import me.psikuvit.cashClash.command.subcommands.LayoutCommand;
import me.psikuvit.cashClash.command.subcommands.LeaveCommand;
import me.psikuvit.cashClash.command.subcommands.MythicsCommand;
import me.psikuvit.cashClash.command.subcommands.ReloadCommand;
import me.psikuvit.cashClash.command.subcommands.SelectKitCommand;
import me.psikuvit.cashClash.command.subcommands.SetLobbyCommand;
import me.psikuvit.cashClash.command.subcommands.ShopCommand;
import me.psikuvit.cashClash.command.subcommands.SpawnNPCCommand;
import me.psikuvit.cashClash.command.subcommands.StatsCommand;
import me.psikuvit.cashClash.command.subcommands.StopCommand;
import me.psikuvit.cashClash.command.subcommands.TemplateCommand;
import me.psikuvit.cashClash.config.MessagesConfig;
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

        //chat commands
        registerSubcommand(new ChatCommand());

        // Cash Quake event commands
        //registerSubcommand(new LotteryCommand());
        //registerSubcommand(new PayTaxCommand());

        // Admin / control commands
        registerSubcommand(new StopCommand());
        registerSubcommand(new ForceStartCommand());
        registerSubcommand(new ForceNextRoundCommand());
        registerSubcommand(new SelectKitCommand());
        registerSubcommand(new SetLobbyCommand());
        registerSubcommand(new ReloadCommand());
        registerSubcommand(new DebugCommand());
        registerSubcommand(new SpawnNPCCommand());

        // Arena/template admin commands
        registerSubcommand(new ArenasCommand());
        registerSubcommand(new TemplateCommand());
        registerSubcommand(new ArenaCommand());
        registerSubcommand(new StatsCommand());
        registerSubcommand(new CoinsCommand());
        registerSubcommand(new MythicsCommand());
        registerSubcommand(new BlockDisplayCommand());
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
            Messages.send(sender, MessagesConfig.getInstance().getRaw("command.no-permission"));
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
        MessagesConfig msgs = MessagesConfig.getInstance();
        Messages.send(sender, msgs.getRaw("command.help-title"));
        Messages.send(sender, msgs.getRaw("command.help-arenas"));
        Messages.send(sender, msgs.getRaw("command.help-join"));
        Messages.send(sender, msgs.getRaw("command.help-leave"));
        Messages.send(sender, msgs.getRaw("command.help-stats"));
        Messages.send(sender, msgs.getRaw("command.help-forfeit"));
        Messages.send(sender, msgs.getRaw("command.help-transfer"));
        Messages.send(sender, msgs.getRaw("command.help-layout"));
        if (sender.hasPermission("cashclash.admin")) {
            Messages.send(sender, msgs.getRaw("command.help-admin-section"));
            Messages.send(sender, msgs.getRaw("command.help-arena"));
            Messages.send(sender, msgs.getRaw("command.help-template"));
            Messages.send(sender, msgs.getRaw("command.help-blockdisplay"));
            Messages.send(sender, msgs.getRaw("command.help-stop"));
            Messages.send(sender, msgs.getRaw("command.help-forcestart"));
            Messages.send(sender, msgs.getRaw("command.help-setlobby"));
            Messages.send(sender, msgs.getRaw("command.help-reload"));
            Messages.send(sender, msgs.getRaw("command.help-debug"));
        }
        Messages.send(sender, msgs.getRaw("command.help-shop"));
    }
}
