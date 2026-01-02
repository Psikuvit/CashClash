package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.gui.builder.GuiBuilder;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for browsing and joining arenas.
 * Uses the GuiBuilder system for cleaner implementation.
 */
public class ArenaSelectionGUI {

    private static final String GUI_ID = "arena_selection";

    public static void openArenaGUI(Player player) {
        GuiBuilder builder = GuiBuilder.create(GUI_ID)
                .title("<gold><bold>Cash Clash - Select Arena</bold></gold>")
                .rows(3)
                .fill(Material.GRAY_STAINED_GLASS_PANE)
                .closeButton(22);

        // Add arenas to GUI (slots 11, 12, 13, 14, 15)
        for (int i = 1; i <= 5; i++) {
            Arena arena = ArenaManager.getInstance().getArena(i);
            if (arena != null) {
                int slot = 10 + i;
                builder.button(slot, createArenaButton(i, arena));
            }
        }

        builder.open(player);
    }

    private static GuiButton createArenaButton(int arenaNumber, Arena arena) {
        ArenaManager manager = ArenaManager.getInstance();
        GameState state = manager.getArenaState(arenaNumber);
        int playerCount = manager.getArenaPlayerCount(arenaNumber);
        boolean isJoinable = manager.isArenaJoinable(arenaNumber);
        int maxPlayers = ConfigManager.getInstance().getMaxPlayers();

        // Determine material based on state
        Material material;
        if (!arena.isReady()) {
            material = Material.RED_WOOL;
        } else if (state == GameState.WAITING && playerCount < maxPlayers) {
            material = Material.GREEN_WOOL;
        } else if (state == GameState.WAITING && playerCount == maxPlayers) {
            material = Material.YELLOW_WOOL;
        } else {
            material = Material.ORANGE_WOOL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse("<yellow><bold>" + arena.getName() + "</bold></yellow>"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        String stateDisplay = switch (state) {
            case WAITING -> "<gray>Status: <green>Waiting</green>";
            case ROUND_1_SHOPPING, ROUND_2_SHOPPING, ROUND_3_SHOPPING,
                 ROUND_4_SHOPPING, ROUND_5_SHOPPING -> "<gray>Status: <yellow>Shopping</yellow>";
            case ROUND_1_COMBAT, ROUND_2_COMBAT, ROUND_3_COMBAT,
                 ROUND_4_COMBAT, ROUND_5_COMBAT -> "<gray>Status: <red>In Combat</red>";
            case ENDING -> "<gray>Status: <gold>Ending</gold>";
        };

        lore.add(Messages.parse(stateDisplay));
        lore.add(Messages.parse("<gray>Players: <white>" + playerCount + "/" + maxPlayers + "</white>"));

        if (!arena.isReady()) {
            lore.add(Messages.parse("<red>Not configured!</red>"));
        }

        lore.add(Component.empty());

        // Join status
        if (isJoinable) {
            lore.add(Messages.parse("<green><bold>✔ Click to join!</bold></green>"));
        } else if (!arena.isReady()) {
            lore.add(Messages.parse("<red><bold>✗ Arena not ready</bold></red>"));
        } else if (playerCount >= maxPlayers) {
            lore.add(Messages.parse("<red><bold>✗ Arena is full</bold></red>"));
        } else if (state != GameState.WAITING) {
            lore.add(Messages.parse("<red><bold>✗ Game in progress</bold></red>"));
        } else {
            lore.add(Messages.parse("<red><bold>✗ Cannot join</bold></red>"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        return GuiButton.of(item).onClick(player -> handleArenaClick(player, arenaNumber));
    }

    /**
     * Handle click on arena item.
     */
    public static void handleArenaClick(Player player, int arenaNumber) {
        ArenaManager arenaManager = ArenaManager.getInstance();
        GameManager gameManager = GameManager.getInstance();

        if (gameManager.getPlayerSession(player) != null) {
            Messages.send(player, "<red>You're already in a game!</red>");
            player.closeInventory();
            return;
        }

        if (!arenaManager.isArenaJoinable(arenaNumber)) {
            Messages.send(player, "<red>This arena cannot be joined right now!</red>");
            player.closeInventory();
            return;
        }

        Arena arena = arenaManager.getArena(arenaNumber);
        if (arena == null) {
            Messages.send(player, "<red>Arena not found!</red>");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        GameSession session = gameManager.getSessionForArena(arenaNumber);

        if (session == null) {
            session = gameManager.createSession(arenaNumber);
        }

        // Determine team - provisional balanced assignment based on config
        int currentPlayers = arenaManager.getArenaPlayerCount(arenaNumber);
        int maxPlayers = ConfigManager.getInstance().getMaxPlayers();
        int capacityPerTeam = Math.max(1, maxPlayers / 2);

        int teamNumber = 1;
        GameSession existing = gameManager.getSessionForArena(arenaNumber);

        if (existing != null) {
            int t1size = existing.getTeam1().getSize();
            int t2size = existing.getTeam2().getSize();

            if (t1size > t2size && t2size < capacityPerTeam) {
                teamNumber = 2;
            } else if (t1size >= capacityPerTeam) {
                teamNumber = 2;
            }
        } else {
            teamNumber = currentPlayers < capacityPerTeam ? 1 : 2;
        }

        session.addPlayer(player, teamNumber);
        gameManager.addPlayerToSession(player, session);

        // Teleport player to the copied world's lobby spawn
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tpl != null && tpl.isConfigured() && tpl.getLobbySpawn() != null && session.getGameWorld() != null) {
            Location lobbyInCopiedWorld = LocationUtils.adjustLocationToWorld(tpl.getLobbySpawn(), session.getGameWorld());
            player.teleport(lobbyInCopiedWorld);
        }

        int newPlayerCount = arenaManager.getArenaPlayerCount(arenaNumber);
        String teamColor = teamNumber == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
        Messages.send(player, "<green>Joined " + arena.getName() + "!</green>");
        Messages.send(player, "<yellow>Team: " + teamColor + "</yellow>");

        session.getPlayers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Messages.send(p, "<gray>Waiting for players... (" + newPlayerCount + "/" + maxPlayers + ")</gray>");
            }
        });

        // If we have min players, start a 2-minute countdown
        int minPlayers = ConfigManager.getInstance().getMinPlayers();
        if (newPlayerCount >= minPlayers) {
            if (!session.isStartingCountdown()) {
                session.startCountdown(120); // 120s = 2 minutes
            } else {
                Messages.send(player, "<gray>Game is already counting down. Please wait...</gray>");
            }
        }
    }
}

