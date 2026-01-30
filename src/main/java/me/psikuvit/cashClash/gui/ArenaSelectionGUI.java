package me.psikuvit.cashClash.gui;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.gui.builder.AbstractGui;
import me.psikuvit.cashClash.gui.builder.GuiButton;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.party.Party;
import me.psikuvit.cashClash.party.PartyManager;
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
import java.util.Set;

/**
 * GUI for browsing and joining arenas.
 * Extends AbstractGui for consistent GUI implementation.
 */
public class ArenaSelectionGUI extends AbstractGui {

    private static final String GUI_ID = "arena_selection";

    public ArenaSelectionGUI(Player viewer) {
        super(GUI_ID, viewer);
        setTitle("<gold><bold>Cash Clash - Select Arena</bold></gold>");
        setRows(3);
        setFillMaterial(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Static convenience method to open the arena GUI.
     */
    public static void openArenaGUI(Player player) {
        new ArenaSelectionGUI(player).open();
    }

    @Override
    protected void build() {
        // Add arenas to GUI (slots 11, 12, 13, 14, 15)
        for (int i = 1; i <= 5; i++) {
            Arena arena = ArenaManager.getInstance().getArena(i);
            if (arena != null) {
                int slot = 10 + i;
                setButton(slot, createArenaButton(i, arena));
            }
        }

        // Close button
        setCloseButton(22);
    }

    private GuiButton createArenaButton(int arenaNumber, Arena arena) {
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
            case SHOPPING -> "<gray>Status: <yellow>Shopping</yellow>";
            case COMBAT -> "<gray>Status: <red>In Combat</red>";
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
        PartyManager partyManager = PartyManager.getInstance();

        // Prevent joining while editing a layout
        if (LayoutManager.getInstance().isEditing(player)) {
            Messages.send(player, "<red>You cannot join a game while editing a layout!</red>");
            Messages.send(player, "<gray>Use <yellow>/cc layout confirm</yellow> or <yellow>/cc layout cancel</yellow> first.</gray>");
            player.closeInventory();
            return;
        }

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

        // Check if player is in a party and is the owner
        Party party = partyManager.getPlayerParty(player);
        boolean isPartyOwner = party != null && party.isOwner(player.getUniqueId());

        // If player is in a party but not the owner, they can't join on their own
        if (party != null && !isPartyOwner) {
            Messages.send(player, "<red>Only the party owner can join games for the party!</red>");
            Messages.send(player, "<gray>Ask <yellow>" + getOwnerName(party) + "</yellow> to join a game.</gray>");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        GameSession session = gameManager.getSessionForArena(arenaNumber);

        if (session == null) {
            session = gameManager.createSession(arenaNumber);
        }

        int maxPlayers = ConfigManager.getInstance().getMaxPlayers();
        int capacityPerTeam = Math.max(1, maxPlayers / 2);

        if (isPartyOwner) {
            // Handle party join
            handlePartyJoin(player, party, session, arena, arenaNumber, capacityPerTeam, maxPlayers);
        } else {
            // Solo player join
            handleSoloJoin(player, session, arena, arenaNumber, capacityPerTeam, maxPlayers);
        }
    }

    /**
     * Handle a party joining together.
     * Party members are placed on the same team, with overflow going to the other team.
     */
    private static void handlePartyJoin(Player owner, Party party, GameSession session, Arena arena,
                                        int arenaNumber, int capacityPerTeam, int maxPlayers) {
        ArenaManager arenaManager = ArenaManager.getInstance();
        GameManager gameManager = GameManager.getInstance();

        // Get online party members
        Set<Player> partyMembers = party.getOnlineMembers();

        // Check if all party members are available (not in games, not editing layouts)
        for (Player member : partyMembers) {
            if (gameManager.getPlayerSession(member) != null) {
                Messages.send(owner, "<red>Party member " + member.getName() + " is already in a game!</red>");
                return;
            }
            if (LayoutManager.getInstance().isEditing(member)) {
                Messages.send(owner, "<red>Party member " + member.getName() + " is editing a layout!</red>");
                return;
            }
        }

        // Check if there's enough space in the arena for the party
        int currentPlayers = arenaManager.getArenaPlayerCount(arenaNumber);
        int availableSlots = maxPlayers - currentPlayers;

        if (partyMembers.size() > availableSlots) {
            Messages.send(owner, "<red>Not enough space in this arena for your party!</red>");
            Messages.send(owner, "<gray>Party size: <white>" + partyMembers.size() + "</white>, Available slots: <white>" + availableSlots + "</white></gray>");
            return;
        }

        // Determine the best team for the party
        int t1size = session.getTeam1().getSize();
        int t2size = session.getTeam2().getSize();
        int t1available = capacityPerTeam - t1size;
        int t2available = capacityPerTeam - t2size;

        // Primary team is the one with more space, or team 1 if equal
        int primaryTeam;
        int primaryAvailable;
        int secondaryTeam;

        if (t1available >= t2available) {
            primaryTeam = 1;
            primaryAvailable = t1available;
            secondaryTeam = 2;
        } else {
            primaryTeam = 2;
            primaryAvailable = t2available;
            secondaryTeam = 1;
        }

        // Add party members - prioritize keeping them together on primary team
        List<Player> primaryTeamMembers = new ArrayList<>();
        List<Player> secondaryTeamMembers = new ArrayList<>();

        int addedToPrimary = 0;
        for (Player member : partyMembers) {
            if (addedToPrimary < primaryAvailable) {
                primaryTeamMembers.add(member);
                addedToPrimary++;
            } else {
                secondaryTeamMembers.add(member);
            }
        }

        // Teleport location
        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        Location lobbySpawn = null;
        if (tpl != null && tpl.isConfigured() && tpl.getLobbySpawn() != null && session.getGameWorld() != null) {
            lobbySpawn = LocationUtils.adjustLocationToWorld(tpl.getLobbySpawn(), session.getGameWorld());
        }

        // Add primary team members
        for (Player member : primaryTeamMembers) {
            session.addPlayer(member, primaryTeam);
            gameManager.addPlayerToSession(member, session);

            if (lobbySpawn != null) {
                member.teleport(lobbySpawn);
            }

            String teamColor = primaryTeam == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
            Messages.send(member, "<green>Joined " + arena.getName() + " with your party!</green>");
            Messages.send(member, "<yellow>Team: " + teamColor + "</yellow>");
        }

        // Add secondary team members (overflow)
        if (!secondaryTeamMembers.isEmpty()) {
            for (Player member : partyMembers) {
                Messages.send(member, "<yellow>Party was split due to team size limits!</yellow>");
            }
        }

        for (Player member : secondaryTeamMembers) {
            session.addPlayer(member, secondaryTeam);
            gameManager.addPlayerToSession(member, session);

            if (lobbySpawn != null) {
                member.teleport(lobbySpawn);
            }

            String teamColor = secondaryTeam == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
            Messages.send(member, "<green>Joined " + arena.getName() + " with your party!</green>");
            Messages.send(member, "<yellow>Team: " + teamColor + " <gray>(overflow)</gray></yellow>");
        }

        // Notify all players in the session
        int newPlayerCount = arenaManager.getArenaPlayerCount(arenaNumber);
        session.getPlayers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Messages.send(p, "<gray>Waiting for players... (" + newPlayerCount + "/" + maxPlayers + ")</gray>");
            }
        });

        // Check for countdown start
        checkAndStartCountdown(session, newPlayerCount);
    }

    /**
     * Handle a solo player joining.
     */
    private static void handleSoloJoin(Player player, GameSession session, Arena arena,
                                       int arenaNumber, int capacityPerTeam, int maxPlayers) {
        ArenaManager arenaManager = ArenaManager.getInstance();
        GameManager gameManager = GameManager.getInstance();

        // Determine team - balanced assignment
        int t1size = session.getTeam1().getSize();
        int t2size = session.getTeam2().getSize();

        int teamNumber;
        if (t1size > t2size && t2size < capacityPerTeam) {
            teamNumber = 2;
        } else if (t1size >= capacityPerTeam) {
            teamNumber = 2;
        } else {
            teamNumber = 1;
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

        // Check for countdown start
        checkAndStartCountdown(session, newPlayerCount);
    }

    /**
     * Check and start countdown if minimum players reached.
     */
    private static void checkAndStartCountdown(GameSession session, int playerCount) {
        int minPlayers = ConfigManager.getInstance().getMinPlayers();
        if (playerCount >= minPlayers) {
            if (!session.isStartingCountdown()) {
                session.startCountdown(120); // 120s = 2 minutes
            }
        }
    }

    /**
     * Get the party owner's name.
     */
    private static String getOwnerName(Party party) {
        Player owner = party.getOwnerPlayer();
        if (owner != null) {
            return owner.getName();
        }
        return "the party owner";
    }
}

