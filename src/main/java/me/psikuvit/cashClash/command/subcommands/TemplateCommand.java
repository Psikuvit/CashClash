package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TemplateCommand extends AbstractArgCommand {
    public TemplateCommand() {
        super("template", List.of(), "cashclash.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cashclash.admin")) {
            Messages.send(sender, "<red>You don't have permission to use template admin commands.</red>");
            return true;
        }

        if (args.length < 1) {
            Messages.send(sender, "<red>Usage: /cc template <register|set|setspawn|list|tp|show> ...</red>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "register" -> templateRegister(sender, args);
            case "setspawn" -> templateSetSpawn(sender, args);
            case "set" -> templateSet(sender, args);
            case "show" -> templateShow(sender, args);
            case "list" -> templateList(sender);
            case "tp" -> templateTeleport(sender, args);
            default -> Messages.send(sender, "<red>Unknown template action. Use register, set, setspawn, list, tp or show.</red>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            for (String a : List.of("register", "setspawn", "set", "list", "tp", "show")) if (a.startsWith(last)) out.add(a);
            return out;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (action.equals("setspawn") || action.equals("tp") || action.equals("show")) {
                out.addAll(ArenaManager.getInstance().getAllTemplates().keySet().stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(last))
                        .toList());
            } else if (action.equals("set")) {
                out.addAll(ArenaManager.getInstance().getAllTemplates().values().stream().map(TemplateWorld::getId)
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(last)).toList());
            }
            return out;
        }

        if (args.length == 3) {
            if (action.equals("register")) {
                out.addAll(Bukkit.getWorlds().stream().map(World::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(last)).toList());
            } else if (action.equals("set")) {
                for (String t : List.of("spectator", "team1", "team2", "shop", "villager")) if (t.startsWith(last)) out.add(t);
            }
            return out;
        }

        if (args.length == 4 && action.equals("set")) {
            String type = args[2].toLowerCase(Locale.ROOT);
            if ("team1".equals(type) || "team2".equals(type)) {
                for (String idx : List.of("1","2","3","4")) if (idx.startsWith(last)) out.add(idx);
            } else if ("shop".equals(type)) {
                for (String t : List.of("team1","team2")) if (t.startsWith(last)) out.add(t);
            }
            return out;
        }

        return List.of();
    }

    // Implementation methods pulled from legacy command
    private void templateRegister(CommandSender sender, String[] args) {
        if (args.length < 3) { Messages.send(sender, "<red>Usage: /cc template register <templateId> <worldName></red>"); return; }
        String id = args[1];
        String worldName = args[2];

        boolean ok = ArenaManager.getInstance().registerTemplate(id, worldName);
        if (ok) Messages.send(sender, "<green>Registered template '" + id + "' -> world '" + worldName + "'</green>");
        else Messages.send(sender, "<red>Failed to register template. Check world name and server logs.</red>");
    }

    private void templateSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this.</red>");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "<red>Usage: /cc template setspawn <templateId></red>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "<red>Template not found: " + id + "</red>");
            return;
        }
        if (!player.getWorld().equals(tpl.getWorld())) {
            Messages.send(player, "<red>Please teleport to the template world first.</red>");
            return;
        }

        tpl.setSpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "<green>Set lobby spawn for template '" + id + "'</green>");
        ArenaManager.getInstance().saveTemplate(id);
    }

    private void templateTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this command.</red>");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "<red>Usage: /cc template tp <templateId></red>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) { Messages.send(player, "<red>Template not found: " + id + "</red>"); return; }

        World w = tpl.getWorld();
        if (w == null) {
            Messages.send(player, "<red>Template world is not loaded: " + id + "</red>");
            return;
        }

        Location target = tpl.getLobbySpawn();
        if (target == null) target = w.getSpawnLocation();

        Location to = LocationUtils.copyToWorld(target, w);
        player.teleport(to);
        Messages.send(player, "<green>Teleported to template '" + id + "' (world: " + w.getName() + ")</green>");
    }

    private void templateList(CommandSender sender) {
        var templates = ArenaManager.getInstance().getAllTemplates();
        if (templates.isEmpty()) {
            Messages.send(sender, "<yellow>No templates registered.</yellow>");
            return;
        }

        Messages.send(sender, "<gold>=== Templates ===</gold>");
        templates.forEach((id, tpl) -> Messages.send(sender,
                "<yellow>" + id + "</yellow> -> <gray>" + tpl.getWorld().getName() + "</gray> " + (tpl.isConfigured() ? "<green>(configured)" : "<red>(incomplete)")));
    }

    private void templateShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use this.</red>"); return; }
        if (args.length < 2) { Messages.send(player, "<red>Usage: /cc template show <templateId></red>"); return; }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) { Messages.send(player, "<red>Template not found: " + id + "</red>"); return; }

        Messages.send(player, "<gold>=== Template: " + tpl.getId() + " Positions ===</gold>");
        Messages.send(player, "<yellow>World: </yellow><gray>" + tpl.getWorld().getName() + "</gray>");
        Messages.send(player, "<yellow>Lobby spawn: </yellow>" + (tpl.getLobbySpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getLobbySpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Spectator: </yellow>" + (tpl.getSpectatorSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getSpectatorSpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Shops: </yellow><gray>" + (tpl.getVillagersSpawnPoint().size() + "</gray>") );

        for (int i = 0; i < 3; i++) {
            Messages.send(player, "<yellow>Team1 spawn #" + (i+1) + ": </yellow>" + (tpl.getTeam1Spawn(i) == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeam1Spawn(i)) + "</gray>"));
            Messages.send(player, "<yellow>Team2 spawn #" + (i+1) + ": </yellow>" + (tpl.getTeam2Spawn(i) == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeam2Spawn(i)) + "</gray>"));
        }

        Messages.send(player, "<yellow>Shop Team1: </yellow>" + (tpl.getTeam1ShopSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeam1ShopSpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Shop Team2: </yellow>" + (tpl.getTeam2ShopSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeam2ShopSpawn()) + "</gray>"));
    }

    private void templateSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, "<red>Only players can use this.</red>"); return; }

        if (args.length < 3) {
            Messages.send(player, "<red>Usage: /cc template set <templateId> <spectator|team1|team2|shop|villager> [index/team]</red>");
            return;
        }

        String templateId = args[1];
        String type = args[2].toLowerCase(Locale.ROOT);

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        if (tpl == null || tpl.getWorld() == null) {
            Messages.send(player, "<red>Template not found or world not loaded: " + templateId + "</red>");
            return;
        }

        World tplWorld = tpl.getWorld();
        if (!player.getWorld().equals(tplWorld)) {
            Messages.send(player, "<red>Please teleport to the template world first: /cc template tp " + templateId + "</red>");
            return;
        }

        Location stored = LocationUtils.copyToWorld(player.getLocation(), tplWorld);

        switch (type) {
            case "spectator" -> {
                tpl.setSpectatorSpawn(stored);
                Messages.send(player, "<green>Spectator spawn set for template '" + templateId + "'</green>");
            }
            case "shop" -> {
                if (args.length < 4) {
                    Messages.send(player, "<red>Usage: /cc template set <templateId> shop <team1|team2></red>");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("team1".equals(team)) {
                    tpl.setTeam1ShopSpawn(stored);
                    Messages.send(player, "<green>Set shop spawn for team1 on template '" + templateId + "'</green>");
                } else if ("team2".equals(team)) {
                    tpl.setTeam2ShopSpawn(stored);
                    Messages.send(player, "<green>Set shop spawn for team2 on template '" + templateId + "'</green>");
                } else {
                    Messages.send(player, "<red>Invalid shop team. Use team1 or team2.</red>");
                    return;
                }
            }
            case "team1", "team2" -> {
                if (args.length < 4) { Messages.send(player, "<red>Usage: /cc template set <templateId> " + type + " <1|2|3></red>"); return; }
                int idx;
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    Messages.send(player, "<red>Index must be 1,2,3 or 4</red>");
                    return;
                }

                if (idx < 1 || idx > 4) {
                    Messages.send(player, "<red>Index must be 1,2,3 or 4</red>");
                    return;
                }
                if ("team1".equals(type)) tpl.setTeam1Spawn(idx - 1, stored); else tpl.setTeam2Spawn(idx - 1, stored);
                Messages.send(player, "<green>Set " + type + " spawn #" + idx + " for template '" + templateId + "'</green>");
            }
            case "villager" -> {
                tpl.addVillagerSpawnPoint(stored);
                Messages.send(player, "<green>Added villager/shop spawn for template '" + templateId + "'</green>");
            }
            default -> Messages.send(player, "<red>Unknown spawn type: " + type + "</red>");
        }

        ArenaManager.getInstance().saveTemplate(templateId);
    }

    private String formatLoc(Location l) {
        if (l == null) return "unset";
        String w = l.getWorld() != null ? l.getWorld().getName() : "(null)";
        return w + " [x=" + Math.round(l.getX()) + ", y=" + Math.round(l.getY()) + ", z=" + Math.round(l.getZ()) + "]";
    }
}

