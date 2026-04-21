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
            Messages.send(sender, "generic.permission-template-admin");
            return true;
        }

        if (args.length < 1) {
            Messages.send(sender, "template.main-usage");
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
            default -> Messages.send(sender, "template.invalid-action");
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
            Messages.send(sender, "template.register-usage");
            return;
        }

        String id = args[1];
        String worldName = args[2];

        if (ArenaManager.getInstance().getTemplate(id) != null) {
            Messages.send(sender, "template.already-exists", "template_id", id);
            return;
        }

        boolean ok = ArenaManager.getInstance().registerTemplate(id, worldName);
        if (ok) Messages.send(sender, "template.register-success", "template_id", id, "world_name", worldName);
        else Messages.send(sender, "template.register-failed");
    }

    private void templateSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "template.setspawn-usage");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "template.not-found", "template_id", id);
            return;
        }
        if (!player.getWorld().equals(tpl.getWorld())) {
            Messages.send(player, "template.player-not-in-template");
            return;
        }

        tpl.setSpawn(LocationUtils.clone(player.getLocation()));
        Messages.send(player, "template.setspawn-success", "template_id", id);
        ArenaManager.getInstance().saveTemplate(id);
    }

    private void templateTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return;
        }

        if (args.length < 2) {
            Messages.send(player, "template.tp-usage");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) { Messages.send(player, "template.not-found", "template_id", id); return; }

        World w = tpl.getWorld();
        if (w == null) {
            Messages.send(player, "template.world-not-loaded", "template_id", id);
            return;
        }

        Location target = tpl.getLobbySpawn();
        if (target == null) target = w.getSpawnLocation();

        Location to = LocationUtils.copyToWorld(target, w);
        player.teleport(to);
        Messages.send(player, "template.tp-success", "template_id", id);
    }

    private void templateList(CommandSender sender) {
        var templates = ArenaManager.getInstance().getAllTemplates();
        if (templates.isEmpty()) {
            Messages.send(sender, "template.list-empty");
            return;
        }

        Messages.send(sender, "template.list-title");
        templates.forEach((id, tpl) -> {
            String worldName = "(unloaded)";
            if (tpl != null && tpl.getWorld() != null) worldName = tpl.getWorld().getName();
            String status = tpl != null && tpl.isConfigured() ? "configured" : "incomplete";
            Messages.send(sender, "template.list-item-with-status", "template_id", id, "world_name", worldName, "status", status);
        });
    }

    private void templateShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return;
        }
        if (args.length < 2) {
            Messages.send(player, "template.show-usage");
            return;
        }

        String id = args[1];
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "template.not-found", "template_id", id);
            return;
        }

        Messages.send(player, "template.show-title", "template_id", tpl.getId());
        String worldName = tpl.getWorld() != null ? tpl.getWorld().getName() : "(unloaded)";
        Messages.send(player, "template.show-world", "world_name", worldName);
        Messages.send(player, "template.show-lobby", "location", tpl.getLobbySpawn() == null ? "unset" : formatLoc(tpl.getLobbySpawn()));
        Messages.send(player, "template.show-spectator", "location", tpl.getSpectatorSpawn() == null ? "unset" : formatLoc(tpl.getSpectatorSpawn()));
        Messages.send(player, "template.show-shops", "shop_info", String.valueOf(tpl.getVillagersSpawnPoint().size()));

        for (int i = 0; i < 3; i++) {
            Messages.send(player, "template.show-team-red-spawn", "index", String.valueOf(i + 1), "location", tpl.getTeamRedSpawn(i) == null ? "unset" : formatLoc(tpl.getTeamRedSpawn(i)));
            Messages.send(player, "template.show-team-blue-spawn", "index", String.valueOf(i + 1), "location", tpl.getTeamBlueSpawn(i) == null ? "unset" : formatLoc(tpl.getTeamBlueSpawn(i)));
        }

        Messages.send(player, "template.show-shop-red", "location", tpl.getTeamRedShopSpawn() == null ? "unset" : formatLoc(tpl.getTeamRedShopSpawn()));
        Messages.send(player, "template.show-shop-blue", "location", tpl.getTeamBlueShopSpawn() == null ? "unset" : formatLoc(tpl.getTeamBlueShopSpawn()));
    }

    private void templateSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return;
        }

        if (args.length < 3) {
            Messages.send(player, "template.set-usage");
            return;
        }

        String templateId = args[1];
        String type = args[2].toLowerCase(Locale.ROOT);

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(templateId);
        if (tpl == null || tpl.getWorld() == null) {
            Messages.send(player, "template.not-found-or-not-loaded", "template_id", templateId);
            return;
        }

        World tplWorld = tpl.getWorld();
        if (!player.getWorld().equals(tplWorld)) {
            Messages.send(player, "template.player-not-in-template-with-id", "template_id", templateId);
            return;
        }

        Location stored = LocationUtils.copyToWorld(player.getLocation(), tplWorld);

        switch (type) {
            case "spectator" -> {
                tpl.setSpectatorSpawn(stored);
                Messages.send(player, "template.set-spectator-success", "template_id", templateId);
            }
            case "shop" -> {
                if (args.length < 4) {
                    Messages.send(player, "template.set-shop-usage");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("teamred".equals(team)) {
                    tpl.setTeamRedShopSpawn(stored);
                    Messages.send(player, "template.set-shop-success", "team", "Red", "template_id", templateId);
                } else if ("teamblue".equals(team)) {
                    tpl.setTeamBlueShopSpawn(stored);
                    Messages.send(player, "template.set-shop-success", "team", "Blue", "template_id", templateId);
                } else {
                    Messages.send(player, "template.invalid-shop-team");
                    return;
                }
            }
            case "teamred", "teamblue" -> {
                if (args.length < 4) { Messages.send(player, "template.set-team-usage", "type", type); return; }
                int idx;
                try {
                    idx = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    Messages.send(player, "generic.invalid-index");
                    return;
                }

                if (idx < 1 || idx > 4) {
                    Messages.send(player, "generic.invalid-index");
                    return;
                }
                if ("teamred".equals(type)) tpl.setTeamRedSpawn(idx - 1, stored);
                else tpl.setTeamBlueSpawn(idx - 1, stored);
                Messages.send(player, "template.set-success", "spawn_type", type, "index", String.valueOf(idx), "template_id", templateId);
            }
            case "villager" -> {
                tpl.addVillagerSpawnPoint(stored);
                Messages.send(player, "template.set-villager-success", "template_id", templateId);
            }
            default -> Messages.send(player, "template.invalid-spawn-type", "spawn_type", type);
        }

        ArenaManager.getInstance().saveTemplate(templateId);
    }

    private String formatLoc(Location l) {
        if (l == null) return "unset";
        String w = l.getWorld() != null ? l.getWorld().getName() : "(null)";
        return w + " [x=" + Math.round(l.getX()) + ", y=" + Math.round(l.getY()) + ", z=" + Math.round(l.getZ()) + "]";
    }
}
