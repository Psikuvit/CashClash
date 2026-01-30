package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ArenaCommand extends AbstractArgCommand {

    public ArenaCommand() {
        super("arena", Collections.emptyList(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cashclash.admin")) {
            Messages.send(sender, "<red>You don't have permission to use arena admin commands.</red>");
            return true;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return true;
        }

        if (args.length < 1) {
            Messages.send(player, "<red>Usage: /cc arena <tp|assign> ...</red>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "tp" -> arenaTp(player, args);
            case "assign" -> arenaAssign(player, args);
            case "name" -> arenaName(player, args);
            default -> Messages.send(player, "<red>Unknown arena action. Use tp or assign.</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) return out;
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            for (String act : List.of("tp", "assign", "name")) if (act.startsWith(last)) out.add(act);
            return out;
        }

        if (args.length == 2) {
            out.addAll(ArenaManager.getInstance().getAllArenas().values().stream()
                    .map(Arena::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(last))
                    .toList());
            return out;
        }

        if (args.length == 3 && "assign".equals(args[0].toLowerCase(Locale.ROOT))) {
            out.addAll(ArenaManager.getInstance().getAllTemplates().keySet().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(last))
                    .toList());
        }

        return out;
    }

    private void arenaName(Player player, String[] args) {
        if (args.length < 3) {
            return;
        }
        String id = args[1];
        String newName = args[2];
        Arena arena = ArenaManager.getInstance().getArena(id);
        if (arena == null) {
            Messages.send(player, "<red>Arena not found: " + id + "</red>");
            return;
        }
        arena.setName(newName);
    }

    private void arenaTp(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "<red>Usage: /cc arena tp <arenaNumber|arenaName></red>");
            return;
        }

        String id = args[1];
        Arena arena = ArenaManager.getInstance().getArena(id);
        if (arena == null) {
            Messages.send(player, "<red>Arena not found: " + id + "</red>");
            return;
        }

        TemplateWorld tplObj = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tplObj == null || tplObj.getWorld() == null) {
            Messages.send(player, "<red>Template world not available for this arena.</red>");
            return;
        }

        World tplWorld = tplObj.getWorld();
        Location target = tplObj.getLobbySpawn();
        if (target == null) target = tplWorld.getSpawnLocation();

        player.teleport(target);
        Messages.send(player, "<green>Teleported to arena: " + arena.getName() + "</green>");
    }

    private void arenaAssign(Player player, String[] args) {
        if (args.length < 3) { Messages.send(player, "<red>Usage: /cc arena assign <arenaName> <templateId></red>"); return; }
        String arenaId = args[1];
        String templateId = args[2];

        Arena arena = ArenaManager.getInstance().getArena(arenaId);
        if (arena == null) {
            Messages.send(player, "<red>Arena not found: " + arenaId + "</red>");
            return;
        }

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        if (tpl == null) {
            Messages.send(player, "<red>Template not found: " + templateId + "</red>");
            return;
        }

        arena.setTemplateId(templateId);
        Integer num = ArenaManager.getInstance().getArenaNumber(arena);
        if (num != null) ArenaManager.getInstance().saveArena(num);
        Messages.send(player, "<green>Assigned template '" + templateId + "' to " + arena.getName() + "</green>");
    }
}

