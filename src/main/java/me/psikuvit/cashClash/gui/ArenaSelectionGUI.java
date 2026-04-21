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
                Messages.send(owner, "arena.party-member-in-game", "player_name", member.getName());
                return;
            }
            if (LayoutManager.getInstance().isEditing(member)) {
                Messages.send(owner, "arena.party-member-editing-layout", "player_name", member.getName());
                return;
            }
        }

        // Check if there's enough space in the arena for the party
        int currentPlayers = arenaManager.getArenaPlayerCount(arenaNumber);
        int availableSlots = maxPlayers - currentPlayers;

        if (partyMembers.size() > availableSlots) {
            Messages.send(owner, "arena.not-enough-space-party");
            Messages.send(owner, "arena.party-size-vs-slots", "party_size", String.valueOf(partyMembers.size()),
                    "available_slots", String.valueOf(availableSlots));
            return;
        }

        // Determine the best team for the party
        int t1size = session.getTeamRed().getSize();
        int t2size = session.getTeamBlue().getSize();
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
            lobbySpawn = LocationUtils.copyToWorld(tpl.getLobbySpawn(), session.getGameWorld());
        }

        // Add primary team members
        for (Player member : primaryTeamMembers) {
            session.addPlayer(member, primaryTeam);
            gameManager.addPlayerToSession(member, session);

            if (lobbySpawn != null) {
                member.teleport(lobbySpawn);
            }

            String teamColor = primaryTeam == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
            Messages.send(member, "arena.joined-with-party", "arena_name", arena.getName());
            Messages.send(member, "arena.team-assigned", "team_color", teamColor);
        }

        // Add secondary team members (overflow)
        if (!secondaryTeamMembers.isEmpty()) {
            for (Player member : partyMembers) {
                Messages.send(member, "arena.party-split");
            }
        }

        for (Player member : secondaryTeamMembers) {
            session.addPlayer(member, secondaryTeam);
            gameManager.addPlayerToSession(member, session);

            if (lobbySpawn != null) {
                member.teleport(lobbySpawn);
            }

            String teamColor = secondaryTeam == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
            Messages.send(member, "arena.joined-with-party", "arena_name", arena.getName());
            Messages.send(member, "arena.team-assigned-overflow", "team_color", teamColor);
        }

        // Notify all players in the session
        int newPlayerCount = arenaManager.getArenaPlayerCount(arenaNumber);
        session.getPlayers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Messages.send(p, "arena.waiting-for-players", "current", String.valueOf(newPlayerCount), "max", String.valueOf(maxPlayers));
            }
        });

        // Check for countdown start
        checkAndStartCountdown(session, newPlayerCount);
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
            Messages.send(player, "arena.cannot-join-while-editing");
            Messages.send(player, "arena.layout-finish-help");
            player.closeInventory();
            return;
        }

        if (gameManager.getPlayerSession(player) != null) {
            Messages.send(player, "arena.already-in-game");
            player.closeInventory();
            return;
        }

        if (!arenaManager.isArenaJoinable(arenaNumber)) {
            Messages.send(player, "arena.not-joinable");
            player.closeInventory();
            return;
        }

        Arena arena = arenaManager.getArena(arenaNumber);
        if (arena == null) {
            Messages.send(player, "arena.not-found");
            player.closeInventory();
            return;
        }

        // Check if player is in a party and is the owner
        Party party = partyManager.getPlayerParty(player);
        boolean isPartyOwner = party != null && party.isOwner(player.getUniqueId());

        // If player is in a party but not the owner, they can't join on their own
        if (party != null && !isPartyOwner) {
            Messages.send(player, "arena.only-party-owner-can-join");
            Messages.send(player, "arena.ask-owner-to-join", "owner_name", getOwnerName(party));
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
     * Handle a solo player joining.
     */
    private static void handleSoloJoin(Player player, GameSession session, Arena arena,
                                       int arenaNumber, int capacityPerTeam, int maxPlayers) {
        ArenaManager arenaManager = ArenaManager.getInstance();
        GameManager gameManager = GameManager.getInstance();

        // Determine team - balanced assignment
        int t1size = session.getTeamRed().getSize();
        int t2size = session.getTeamBlue().getSize();

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
            Location lobbyInCopiedWorld = LocationUtils.copyToWorld(tpl.getLobbySpawn(), session.getGameWorld());
            player.teleport(lobbyInCopiedWorld);
        }

        int newPlayerCount = arenaManager.getArenaPlayerCount(arenaNumber);
        String teamColor = teamNumber == 1 ? "<red>Red</red>" : "<blue>Blue</blue>";
        Messages.send(player, "arena.joined", "arena_name", arena.getName());
        Messages.send(player, "arena.team-assigned", "team_color", teamColor);

        session.getPlayers().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Messages.send(p, "arena.waiting-for-players", "current", String.valueOf(newPlayerCount), "max", String.valueOf(maxPlayers));
            }
        });

        // Check for countdown start
        checkAndStartCountdown(session, newPlayerCount);
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

        // Add map name (template ID or world name)
        TemplateWorld template = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (template != null) {
            String mapName = template.getId();
            if (template.getWorld() != null) {
                mapName = template.getWorld().getName();
            }
            lore.add(Messages.parse("<gray>Map: <white>" + mapName + "</white></gray>"));
        }

        String stateDisplay = switch (state) {
            case WAITING -> "<gray>Status: <green>Waiting</green>";
            case BUFF_SELECTION -> "<gray>Status: <yellow>Buff Selection</yellow>";
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

