package me.psikuvit.cashClash.command.subcommands;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.arena.BlockRegion;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.command.AbstractArgCommand;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.enums.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TemplateCommand extends AbstractArgCommand {

    // Each admin's two selected corners (pos1, pos2) for spawn rooms and doors
    private final Map<UUID, Location[]> selections = new HashMap<>();

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
            // Spawn room setup is paused along with the spawn rooms
            //case "pos1" -> templateSelect(sender, 0);
            //case "pos2" -> templateSelect(sender, 1);
            //case "cleardoors" -> templateClearDoors(sender, args);
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
                out.addAll(CashClashPlugin.getInstance().getArenaManager().getAllTemplates().keySet().stream()
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(last))
                        .toList());
            } else if (action.equals("set")) {
                out.addAll(CashClashPlugin.getInstance().getArenaManager().getAllTemplates().values().stream().map(TemplateWorld::getId)
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

    private void templateRegister(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "template.register-usage");
            return;
        }

        String id = args[1];
        String worldName = args[2];

        if (CashClashPlugin.getInstance().getArenaManager().getTemplate(id) != null) {
            Messages.send(sender, "template.already-exists", "template_id", id);
            return;
        }

        boolean ok = CashClashPlugin.getInstance().getArenaManager().registerTemplate(id, worldName);
        if (ok) {
            Messages.send(sender, "template.register-success", "template_id", id, "world_name", worldName);
        } else {
            Messages.send(sender, "template.register-failed");
        }
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
        TemplateWorld tpl = CashClashPlugin.getInstance().getArenaManager().getTemplate(id);
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
        CashClashPlugin.getInstance().getArenaManager().saveTemplate(id);
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
        TemplateWorld tpl = CashClashPlugin.getInstance().getArenaManager().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "template.not-found", "template_id", id);
            return;
        }

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
        var templates = CashClashPlugin.getInstance().getArenaManager().getAllTemplates();
        if (templates.isEmpty()) {
            Messages.send(sender, "template.list-empty");
            return;
        }

        Messages.send(sender, "template.list-title");
        templates.forEach((id, tpl) -> {
            String worldName = "(unloaded)";
            if (tpl != null && tpl.getWorld() != null) worldName = tpl.getWorld().getName();
            String status = tpl != null && tpl.isConfigured() ? "<green>configured</green>" : "<red>incomplete</red>";
            Messages.send(sender, "template.list-item-with-status",
                    "template_id", id, "world_name", worldName, "status", status);
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
        TemplateWorld tpl = CashClashPlugin.getInstance().getArenaManager().getTemplate(id);
        if (tpl == null) {
            Messages.send(player, "template.not-found", "template_id", id);
            return;
        }

        Messages.send(player, "template.show-title", "template_id", tpl.getId());
        String worldName = tpl.getWorld() != null ? tpl.getWorld().getName() : "(unloaded)";
        Messages.send(player, "template.show-world", "world_name", worldName);
        Messages.send(player, "template.show-lobby", "location", formatLoc(tpl.getLobbySpawn()));
        Messages.send(player, "template.show-spectator", "location", formatLoc(tpl.getSpectatorSpawn()));
        Messages.send(player, "template.show-shops", "shop_info", String.valueOf(tpl.getVillagersSpawnPoint().size()));

        for (int i = 0; i < 3; i++) {
            String idx = String.valueOf(i + 1);
            Messages.send(player, "template.show-team-red-spawn", "index", idx, "location", formatLoc(tpl.getTeamRedSpawn(i)));
            Messages.send(player, "template.show-team-blue-spawn", "index", idx, "location", formatLoc(tpl.getTeamBlueSpawn(i)));
        }

        Messages.send(player, "template.show-shop-red", "location", formatLoc(tpl.getTeamRedShopSpawn()));
        Messages.send(player, "template.show-shop-blue", "location", formatLoc(tpl.getTeamBlueShopSpawn()));

        Messages.send(player, "template.show-red-flag", "location", formatLoc(tpl.getRedFlagLoc()));
        Messages.send(player, "template.show-blue-flag", "location", formatLoc(tpl.getBlueFlagLoc()));

        // Spawn rooms are paused
        //for (TeamColor team : TeamColor.values()) {
        //    BlockRegion room = tpl.getSpawnRoom(team);
        //    Messages.send(player, "template.show-spawn-room", "team", team.getDisplayName(),
        //            "region", room != null ? room.toString() : "unset",
        //            "doors", String.valueOf(tpl.getSpawnRoomDoors(team).size()));
        //}
    }

    // ==================== SPAWN ROOM SETUP (paused - the commands are commented out above) ====================

    /**
     * pos1/pos2: marks a corner of a spawn room or door at the block the player stands in.
     */
    private void templateSelect(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "command.only-players");
            return;
        }

        Location corner = player.getLocation().getBlock().getLocation();
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[index] = corner;
        Messages.send(player, "template.pos-set", "index", String.valueOf(index + 1),
                "x", String.valueOf(corner.getBlockX()), "y", String.valueOf(corner.getBlockY()), "z", String.valueOf(corner.getBlockZ()));
    }

    /**
     * The player's pos1/pos2 selection as a region in the given world, or null (after telling
     * them why) when it's incomplete or in another world.
     */
    private BlockRegion selectedRegion(Player player, World world, String templateId) {
        Location[] corners = selections.get(player.getUniqueId());
        if (corners == null || corners[0] == null || corners[1] == null) {
            Messages.send(player, "template.selection-missing");
            return null;
        }
        if (!world.equals(corners[0].getWorld()) || !world.equals(corners[1].getWorld())) {
            Messages.send(player, "template.selection-wrong-world", "template_id", templateId);
            return null;
        }
        return BlockRegion.of(corners[0], corners[1]);
    }

    private static TeamColor parseRoomTeam(String arg) {
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "red", "teamred" -> TeamColor.RED;
            case "blue", "teamblue" -> TeamColor.BLUE;
            default -> null;
        };
    }

    private void templateClearDoors(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "template.cleardoors-usage");
            return;
        }

        String templateId = args[1];
        TemplateWorld tpl = CashClashPlugin.getInstance().getArenaManager().getTemplate(templateId);
        if (tpl == null) {
            Messages.send(sender, "template.not-found", "template_id", templateId);
            return;
        }
        TeamColor team = parseRoomTeam(args[2]);
        if (team == null) {
            Messages.send(sender, "template.invalid-room-team");
            return;
        }

        tpl.clearSpawnRoomDoors(team);
        CashClashPlugin.getInstance().getArenaManager().saveTemplate(templateId);
        Messages.send(sender, "template.cleardoors-success", "team", team.getDisplayName(), "template_id", templateId);
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

        TemplateWorld tpl = CashClashPlugin.getInstance().getArenaManager().getTemplate(templateId);
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
                    Messages.send(player, "template.set-shop-red-success", "template_id", templateId);
                } else if ("teamblue".equals(team)) {
                    tpl.setTeamBlueShopSpawn(stored);
                    Messages.send(player, "template.set-shop-blue-success", "template_id", templateId);
                } else {
                    Messages.send(player, "template.invalid-shop-team");
                    return;
                }
            }
            case "ctf" -> {
                if (args.length < 4) {
                    Messages.send(player, "template.set-ctf-usage");
                    return;
                }
                String team = args[3].toLowerCase(Locale.ROOT);

                if ("red".equals(team)) {
                    tpl.setRedFlagLoc(stored);
                    Messages.send(player, "template.set-ctf-red-success", "template_id", templateId);
                } else if ("blue".equals(team)) {
                    tpl.setBlueFlagLoc(stored);
                    Messages.send(player, "template.set-ctf-blue-success", "template_id", templateId);
                } else {
                    Messages.send(player, "template.invalid-ctf-team");
                    return;
                }
            }
            case "teamred", "teamblue" -> {
                if (args.length < 4) {
                    Messages.send(player, "template.set-spawn-index-usage", "spawn_type", type);
                    return;
                }
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
                Messages.send(player, "template.set-success",
                        "spawn_type", type, "index", String.valueOf(idx), "template_id", templateId);
            }
            case "villager" -> {
                tpl.addVillagerSpawnPoint(stored);
                Messages.send(player, "template.set-villager-success", "template_id", templateId);
            }
            // Spawn rooms are paused
            //case "spawnroom", "door" -> {
            //    TeamColor team = args.length < 4 ? null : parseRoomTeam(args[3]);
            //    if (team == null) {
            //        Messages.send(player, type.equals("door") ? "template.set-door-usage" : "template.set-room-usage");
            //        return;
            //    }
            //    BlockRegion region = selectedRegion(player, tplWorld, templateId);
            //    if (region == null) return;
            //
            //    if (type.equals("spawnroom")) {
            //        tpl.setSpawnRoom(team, region);
            //        Messages.send(player, "template.set-room-success", "team", team.getDisplayName(),
            //                "template_id", templateId, "region", region.toString());
            //    } else {
            //        tpl.addSpawnRoomDoor(team, region);
            //        Messages.send(player, "template.set-door-success", "team", team.getDisplayName(),
            //                "template_id", templateId, "region", region.toString(),
            //                "count", String.valueOf(tpl.getSpawnRoomDoors(team).size()));
            //    }
            //}
            default -> Messages.send(player, "template.invalid-spawn-type", "spawn_type", type);
        }

        CashClashPlugin.getInstance().getArenaManager().saveTemplate(templateId);
    }

    private String formatLoc(Location l) {
        if (l == null) return "unset";
        String w = l.getWorld() != null ? l.getWorld().getName() : "(null)";
        return w + " [x=" + Math.round(l.getX()) + ", y=" + Math.round(l.getY()) + ", z=" + Math.round(l.getZ()) + "]";
    }
}
