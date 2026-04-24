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
            Messages.send(sender, "You don't have permission to use template admin commands.");
            return true;
        }

        if (args.length < 1) {
            Messages.send(sender, "Usage: /cc template <register|set|setspawn|list|tp|show> ...");
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
            default -> Messages.send(sender, "Unknown template action. Use register, set, setspawn, list, tp or show.");
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
                for (String t : List.of("spectator", "teamred", "teamblue", "shop", "villager", "ctf")) if (t.startsWith(last)) out.add(t);
            }
            return out;
        }

        if (args.length == 4 && action.equals("set")) {
            String type = args[2].toLowerCase(Locale.ROOT);
            switch (type) {
                case "teamred", "teamblue" -> {
                    for (String idx : List.of("1", "2", "3", "4")) if (idx.startsWith(last)) out.add(idx);
                }
                case "shop" -> {
                    for (String t : List.of("teamred", "teamblue")) if (t.startsWith(last)) out.add(t);
                }
                case "ctf" -> {
                    for (String t : List.of("red", "blue")) if (t.startsWith(last)) out.add(t);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }

    // Implementation methods pulled from legacy command
    private void templateRegister(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "Usage: /cc template register <templateId> <worldName>");
            return;
        }

        String id = args[1];
        String worldName = args[2];

        if (ArenaManager.getInstance().getTemplate(id) != null) {
            Messages.send(sender, "Template with ID '" + id + "' already exists.");
            return;
        }

        boolean ok = ArenaManager.getInstance().registerTemplate(id, worldName);
        if (ok) Messages.send(sender, "Registered template '" + id + "' to world '" + worldName + "'");
        else Messages.send(sender, "Failed to register template. Check world name and server logs.");
    }

    private void templateSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Only players can use this command.");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "Usage: /cc template setspawn <templateId>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "Template not found: " + id);
            return;
        }
        if (!player.getWorld().equals(tpl.getWorld())) {
            Messages.send(player, "Please teleport to the template world first.");
            return;
        }

        tpl.setSpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "Lobby spawn for template '" + id + "' set to your current location.");
        ArenaManager.getInstance().saveTemplate(id);
    }

    private void templateTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Only players can use this command.");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "Usage: /cc template tp <templateId>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "Template not found: " + id);
            return;
        }

        World w = tpl.getWorld();
        if (w == null) {
            Messages.send(player, "Template world is not loaded: " + id);
            return;
        }

        Location target = tpl.getLobbySpawn();
        if (target == null) target = w.getSpawnLocation();

        Location to = LocationUtils.copyToWorld(target, w);
        player.teleport(to);
        Messages.send(player, "Teleported to template '" + id + "'");
    }

    private void templateList(CommandSender sender) {
        var templates = ArenaManager.getInstance().getAllTemplates();
        if (templates.isEmpty()) {
            Messages.send(sender, "No templates registered.");
            return;
        }

        Messages.send(sender, "=== Templates ===");
        templates.forEach((id, tpl) -> {
            String worldName = "(unloaded)";
            if (tpl != null && tpl.getWorld() != null) worldName = tpl.getWorld().getName();
            String status = tpl != null && tpl.isConfigured() ? "configured" : "incomplete";
            Messages.send(sender, id + " - World: " + worldName + " (" + status + ")");
        });
    }

    private void templateShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Only players can use this command.");
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "Usage: /cc template show <templateId>");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "Template not found: " + id);
            return;
        }

        Messages.send(player, "=== Template: " + tpl.getId() + " ===");
        String worldName = tpl.getWorld() != null ? tpl.getWorld().getName() : "(unloaded)";
        Messages.send(player, "World: " + worldName);
        Messages.send(player, "Lobby: " + (tpl.getLobbySpawn() == null ? "unset" : formatLoc(tpl.getLobbySpawn())));
        Messages.send(player, "Spectator: " + (tpl.getSpectatorSpawn() == null ? "unset" : formatLoc(tpl.getSpectatorSpawn())));
        Messages.send(player, "Shops: " + tpl.getVillagersSpawnPoint().size());

        for (int i = 0; i < 3; i++) {
            Messages.send(player, "teamRed spawn #" + (i + 1) + ": " + (tpl.getTeamRedSpawn(i) == null ? "unset" : formatLoc(tpl.getTeamRedSpawn(i))));
            Messages.send(player, "teamBlue spawn #" + (i + 1) + ": " + (tpl.getTeamBlueSpawn(i) == null ? "unset" : formatLoc(tpl.getTeamBlueSpawn(i))));
        }

        Messages.send(player, "Shop teamRed: " + (tpl.getTeamRedShopSpawn() == null ? "unset" : formatLoc(tpl.getTeamRedShopSpawn())));
        Messages.send(player, "Shop teamBlue: " + (tpl.getTeamBlueShopSpawn() == null ? "unset" : formatLoc(tpl.getTeamBlueShopSpawn())));

        // Show CTF flag locations
        Messages.send(player, "Red Flag: " + (tpl.getRedFlagLoc() == null ? "unset" : formatLoc(tpl.getRedFlagLoc())));
        Messages.send(player, "Blue Flag: " + (tpl.getBlueFlagLoc() == null ? "unset" : formatLoc(tpl.getBlueFlagLoc())));
    }

    private void templateSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Only players can use this command.");
            return;
        }

        if (args.length < 3) {
            Messages.send(player, "Usage: /cc template set <templateId> <spectator|teamRed|teamBlue|shop|villager|ctf> [index/team]");
            return;
        }

        String templateId = args[1];
        String type = args[2].toLowerCase(Locale.ROOT);

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        if (tpl == null || tpl.getWorld() == null) {
            Messages.send(player, "Template not found or world not loaded: " + templateId);
            return;
        }

        World tplWorld = tpl.getWorld();
        if (!player.getWorld().equals(tplWorld)) {
            Messages.send(player, "Please teleport to the template world first: /cc template tp " + templateId);
            return;
        }

        Location stored = LocationUtils.copyToWorld(player.getLocation(), tplWorld);

        switch (type) {
            case "spectator" -> {
                tpl.setSpectatorSpawn(stored);
                Messages.send(player, "Spectator spawn set for template '" + templateId + "'");
            }
            case "shop" -> {
                if (args.length < 4) {
                    Messages.send(player, "Usage: /cc template set <templateId> shop <teamRed|teamBlue>");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("teamred".equals(team)) {
                    tpl.setTeamRedShopSpawn(stored);
                    Messages.send(player, "Set shop spawn for team Red on template '" + templateId + "'");
                } else if ("teamblue".equals(team)) {
                    tpl.setTeamBlueShopSpawn(stored);
                    Messages.send(player, "Set shop spawn for team Blue on template '" + templateId + "'");
                } else {
                    Messages.send(player, "Invalid shop team. Use teamRed or teamBlue.");
                    return;
                }
            }
            case "ctf" -> {
                if (args.length < 4) {
                    Messages.send(player, "Usage: /cc template set <templateId> ctf <red|blue>");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("red".equals(team)) {
                    tpl.setRedFlagLoc(stored);
                    Messages.send(player, "Set CTF Red flag location for template '" + templateId + "'");
                } else if ("blue".equals(team)) {
                    tpl.setBlueFlagLoc(stored);
                    Messages.send(player, "Set CTF Blue flag location for template '" + templateId + "'");
                } else {
                    Messages.send(player, "Invalid CTF team. Use red or blue.");
                    return;
                }
            }
            case "teamred", "teamblue" -> {
                if (args.length < 4) {
                    Messages.send(player, "Usage: /cc template set <templateId> " + type + " <1|2|3|4>");
                    return;
                }
                int idx;
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    Messages.send(player, "Index must be 1, 2, 3, or 4");
                    return;
                }

                if (idx < 1 || idx > 4) {
                    Messages.send(player, "Index must be 1, 2, 3, or 4");
                    return;
                }
                if ("teamred".equals(type)) tpl.setTeamRedSpawn(idx - 1, stored);
                else tpl.setTeamBlueSpawn(idx - 1, stored);
                Messages.send(player, "Set " + type + " spawn #" + idx + " for template '" + templateId + "'");
            }
            case "villager" -> {
                tpl.addVillagerSpawnPoint(stored);
                Messages.send(player, "Added villager/shop spawn for template '" + templateId + "'");
            }
            default -> Messages.send(player, "Unknown spawn type: " + type);
        }

        ArenaManager.getInstance().saveTemplate(templateId);
    }

    private String formatLoc(Location l) {
        if (l == null) return "unset";
        String w = l.getWorld() != null ? l.getWorld().getName() : "(null)";
        return w + " [x=" + Math.round(l.getX()) + ", y=" + Math.round(l.getY()) + ", z=" + Math.round(l.getZ()) + "]";
    }
}
