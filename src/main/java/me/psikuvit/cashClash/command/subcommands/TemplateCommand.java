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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TemplateCommand extends AbstractArgCommand {
    public TemplateCommand() {
        super("template", Collections.emptyList(), "cashclash.admin");
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
        if (args.length == 0) return Collections.emptyList();
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
                for (String t : List.of("spectator", "teamred", "teamblue", "shop", "villager")) if (t.startsWith(last)) out.add(t);
            }
            return out;
        }

        if (args.length == 4 && action.equals("set")) {
            String type = args[2].toLowerCase(Locale.ROOT);
            if ("teamred".equals(type) || "teamblue".equals(type)) {
                for (String idx : List.of("1","2","3","4")) if (idx.startsWith(last)) out.add(idx);
            } else if ("shop".equals(type)) {
                for (String t : List.of("teamred","teamblue")) if (t.startsWith(last)) out.add(t);
            }
            return out;
        }

        return Collections.emptyList();
    }

    // Implementation methods pulled from legacy command
    private void templateRegister(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "<red>Usage: /cc template register <templateId> <worldName></red>");
            return;
        }

        String id = args[1];
        String worldName = args[2];

        if (ArenaManager.getInstance().getTemplate(id) != null) {
            Messages.send(sender, "<red>Template with ID '" + id + "' already exists.</red>");
            return;
        }

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
        templates.forEach((id, tpl) -> {
            String worldName = "(unloaded)";
            if (tpl != null && tpl.getWorld() != null) worldName = tpl.getWorld().getName();
            String status = tpl != null && tpl.isConfigured() ? "<green>(configured)" : "<red>(incomplete)";
            Messages.send(sender, "<yellow>" + id + "</yellow> -> <gray>" + worldName + "</gray> " + status);
        });
    }

    private void templateShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this.</red>");
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "<red>Usage: /cc template show <templateId></red>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "<red>Template not found: " + id + "</red>");
            return;
        }

        Messages.send(player, "<gold>=== Template: " + tpl.getId() + " Positions ===</gold>");
        String worldName = tpl.getWorld() != null ? tpl.getWorld().getName() : "(unloaded)";
        Messages.send(player, "<yellow>World: </yellow><gray>" + worldName + "</gray>");
        Messages.send(player, "<yellow>Lobby spawn: </yellow>" + (tpl.getLobbySpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getLobbySpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Spectator: </yellow>" + (tpl.getSpectatorSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getSpectatorSpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Shops: </yellow><gray>" + (tpl.getVillagersSpawnPoint().size() + "</gray>") );

        for (int i = 0; i < 3; i++) {
            Messages.send(player, "<yellow>teamRed spawn #" + (i+1) + ": </yellow>" + (tpl.getTeamRedSpawn(i) == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeamRedSpawn(i)) + "</gray>"));
            Messages.send(player, "<yellow>teamBlue spawn #" + (i+1) + ": </yellow>" + (tpl.getTeamBlueSpawn(i) == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeamBlueSpawn(i)) + "</gray>"));
        }

        Messages.send(player, "<yellow>Shop teamRed: </yellow>" + (tpl.getTeamRedShopSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeamRedShopSpawn()) + "</gray>"));
        Messages.send(player, "<yellow>Shop teamBlue: </yellow>" + (tpl.getTeamBlueShopSpawn() == null ? "<red>unset</red>" : "<gray>" + formatLoc(tpl.getTeamBlueShopSpawn()) + "</gray>"));
    }

    private void templateSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Only players can use this.</red>");
            return;
        }

        if (args.length < 3) {
            Messages.send(player, "<red>Usage: /cc template set <templateId> <spectator|teamRed|teamBlue|shop|villager> [index/team]</red>");
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
                    Messages.send(player, "<red>Usage: /cc template set <templateId> shop <teamRed|teamBlue></red>");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("teamred".equals(team)) {
                    tpl.setTeamRedShopSpawn(stored);
                    Messages.send(player, "<green>Set shop spawn for team Red on template '" + templateId + "'</green>");
                } else if ("teamblue".equals(team)) {
                    tpl.setTeamBlueShopSpawn(stored);
                    Messages.send(player, "<green>Set shop spawn for team Blue on template '" + templateId + "'</green>");
                } else {
                    Messages.send(player, "<red>Invalid shop team. Use teamRed or teamBlue.</red>");
                    return;
                }
            }
            case "teamred", "teamblue" -> {
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
                if ("teamred".equals(type)) tpl.setTeamRedSpawn(idx - 1, stored);
                else tpl.setTeamBlueSpawn(idx - 1, stored);
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
