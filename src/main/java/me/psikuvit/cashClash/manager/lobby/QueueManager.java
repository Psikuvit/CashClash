package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.party.Party;
import me.psikuvit.cashClash.party.PartyManager;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The single queue every game is filled from. Players - a party owner brings their whole party -
 * wait in the shared waiting room. Once {@code game.min-players} are queued a countdown runs;
 * when it ends, up to {@code game.max-players} of them start a game on a random free map, party
 * members on the same team. The map is loaded as the countdown starts, so the copy doesn't stall
 * the game's start, and players never see which map it is until they're in it.
 */
public class QueueManager implements Shutdownable, Listener {

    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final PartyManager partyManager;
    private final LobbyManager lobbyManager;
    private final LayoutManager layoutManager;
    private final MessagesConfig messagesConfig;

    private final List<UUID> queue = new ArrayList<>();
    private final List<String> tipBag = new ArrayList<>();
    private BukkitTask tickTask;
    private GameSession reservedSession;
    private int countdownRemaining = -1;
    private boolean forceStart;
    private boolean waitingForArenaNotified;
    private int tipClock;
    private String currentTip;
    private String lastTip;

    public QueueManager(GameManager gameManager, ArenaManager arenaManager, ConfigManager configManager,
                        PartyManager partyManager, LobbyManager lobbyManager, LayoutManager layoutManager,
                        MessagesConfig messagesConfig) {
        this.gameManager = gameManager;
        this.arenaManager = arenaManager;
        this.configManager = configManager;
        this.partyManager = partyManager;
        this.lobbyManager = lobbyManager;
        this.layoutManager = layoutManager;
        this.messagesConfig = messagesConfig;
    }

    public void start() {
        tickTask = SchedulerUtils.runTaskTimer(this::tick, 20L, 20L);
    }

    public boolean isQueued(Player player) {
        return queue.contains(player.getUniqueId());
    }

    public int getQueuedCount() {
        return queue.size();
    }

    /**
     * Queues a player - or, for a party owner, every online member of their party - and sends
     * them to the waiting room.
     */
    public void join(Player player) {
        if (gameManager.getPlayerSession(player) != null) {
            Messages.send(player, "arena.already-in-game");
            return;
        }
        if (layoutManager.isEditing(player)) {
            Messages.send(player, "arena.cannot-join-while-editing");
            Messages.send(player, "arena.layout-finish-help");
            return;
        }
        if (isQueued(player)) {
            Messages.send(player, "queue.already-queued");
            return;
        }

        Collection<Player> joining = List.of(player);
        Party party = partyManager.getPlayerParty(player);
        if (party != null) {
            if (!party.isOwner(player.getUniqueId())) {
                Player owner = party.getOwnerPlayer();
                Messages.send(player, "arena.only-party-owner-can-join");
                Messages.send(player, "arena.ask-owner-to-join", "owner_name", owner != null ? owner.getName() : "the party owner");
                return;
            }
            joining = party.getOnlineMembers();
            for (Player member : joining) {
                if (gameManager.getPlayerSession(member) != null) {
                    Messages.send(player, "arena.party-member-in-game", "player_name", member.getName());
                    return;
                }
                if (layoutManager.isEditing(member)) {
                    Messages.send(player, "arena.party-member-editing-layout", "player_name", member.getName());
                    return;
                }
            }
        }

        for (Player member : joining) {
            if (isQueued(member)) continue;
            queue.add(member.getUniqueId());

            Location waitingRoom = arenaManager.getWaitingLobbySpawn();
            if (waitingRoom != null) member.teleport(waitingRoom);
            lobbyManager.giveQueueItems(member);

            if (member.equals(player)) {
                Messages.send(member, "queue.joined");
            } else {
                Messages.send(member, "queue.joined-with-party", "owner_name", player.getName());
            }
        }
    }

    /**
     * Takes a player out of the queue and back to the hub.
     *
     * @return false if they weren't queued
     */
    public boolean leave(Player player) {
        if (!queue.remove(player.getUniqueId())) return false;
        lobbyManager.sendToLobby(player);
        return true;
    }

    /**
     * Takes a player out of the queue without sending them anywhere - they're going somewhere
     * else, like the practice range.
     */
    public void remove(Player player) {
        queue.remove(player.getUniqueId());
    }

    /**
     * Starts a game with whoever is queued, skipping the countdown and the minimum player count.
     *
     * @return false if nobody is queued
     */
    public boolean forceStart() {
        if (queue.isEmpty()) return false;
        forceStart = true;
        countdownRemaining = 0;
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queue.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        queue.removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            return player == null || !player.isOnline() || gameManager.getPlayerSession(player) != null;
        });

        if (queue.isEmpty()) {
            if (countdownRemaining >= 0) resetCountdown();
            tipClock = 0;
            currentTip = null;
            return;
        }

        updateActionBars();
        runCountdown();
    }

    /**
     * Every queued player's action bar: how many are waiting, swapped for a tip for
     * {@code duration-seconds} every {@code interval-seconds}.
     */
    private void updateActionBars() {
        int interval = configManager.getQueueTipIntervalSeconds();
        int duration = configManager.getQueueTipDurationSeconds();

        tipClock++;
        boolean showTip = false;
        if (interval > 0) {
            int phase = tipClock % interval;
            if (phase == 0) currentTip = nextTip();
            showTip = currentTip != null && phase < duration;
        }

        String text = showTip
                ? messagesConfig.getMessage("queue.tip", "tip", currentTip)
                : messagesConfig.getMessage("queue.waiting-action-bar",
                        "current", String.valueOf(Math.min(queue.size(), configManager.getMaxPlayers())),
                        "max", String.valueOf(configManager.getMaxPlayers()));
        for (UUID uuid : queue) {
            ActionBarQueue.get().sendRaw(uuid, text);
        }
    }

    /**
     * The next tip, dealt from a shuffled bag so every tip is shown once before any repeats.
     */
    private String nextTip() {
        if (tipBag.isEmpty()) {
            List<String> tips = configManager.getQueueTips();
            if (tips.isEmpty()) return null;
            tipBag.addAll(tips);
            Collections.shuffle(tipBag, ThreadLocalRandom.current());
            if (tipBag.size() > 1 && tipBag.getFirst().equals(lastTip)) {
                Collections.swap(tipBag, 0, tipBag.size() - 1);
            }
        }
        lastTip = tipBag.removeFirst();
        return lastTip;
    }

    /**
     * The same chat countdown a game's own start countdown always used: the start line, then
     * every 30s and each of the last 5 seconds, with a ping for the last 5.
     */
    private void runCountdown() {
        int minPlayers = configManager.getMinPlayers();

        if (countdownRemaining < 0) {
            if (queue.size() < minPlayers) return;
            countdownRemaining = configManager.getQueueCountdownSeconds();
            Messages.broadcast(queue, "round.game-countdown-start", "seconds", String.valueOf(countdownRemaining));
            reserveMap();
        }

        if (!forceStart && queue.size() < minPlayers) {
            Messages.broadcast(queue, "round.game-countdown-cancelled");
            resetCountdown();
            return;
        }

        if (!waitingForArenaNotified && (countdownRemaining % 30 == 0 || countdownRemaining <= 5)) {
            Messages.broadcast(queue, "round.game-countdown-tick", "seconds", String.valueOf(countdownRemaining));
        }
        if (countdownRemaining > 0 && countdownRemaining <= 5) {
            SoundUtils.playTo(queue, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }

        if (countdownRemaining <= 0) {
            tryStartGame();
            return;
        }
        countdownRemaining--;
    }

    private void resetCountdown() {
        countdownRemaining = -1;
        forceStart = false;
        waitingForArenaNotified = false;
        if (reservedSession != null) {
            reservedSession.discard();
            reservedSession = null;
        }
    }

    /**
     * Starts the game, or - while every arena is busy or the concurrent-game limit is reached -
     * tells the queue once and tries again every second.
     */
    private void tryStartGame() {
        reserveMap();
        if (reservedSession == null) {
            if (!waitingForArenaNotified) {
                Messages.broadcast(queue, hasReadyArena() ? "queue.waiting-for-arena" : "queue.no-arena");
                waitingForArenaNotified = true;
            }
            return;
        }

        List<List<Player>> groups = pickGroups(configManager.getMaxPlayers());
        if (groups.isEmpty()) return;

        GameSession session = reservedSession;
        reservedSession = null;
        countdownRemaining = -1;
        forceStart = false;
        waitingForArenaNotified = false;

        Map<Player, Integer> teams = assignTeams(groups);
        List<UUID> starting = teams.keySet().stream().map(Player::getUniqueId).toList();
        queue.removeAll(starting);

        Messages.broadcast(starting, "round.game-starting");
        teams.forEach((player, teamNumber) -> {
            session.addPlayer(player, teamNumber);
            gameManager.addPlayerToSession(player, session);
        });
        teams.keySet().forEach(player -> {
            Team team = session.getPlayerTeam(player);
            if (team != null) Messages.send(player, "round.team-assignment", "team_color", team.getColoredName());
        });

        Messages.debug("GAME", "Queue started a game in arena " + session.getArenaNumber() + " with " + starting.size() + " player(s)");
        session.start();
    }

    /**
     * Loads a random free, ready map for the next game, unless one is loaded already or the
     * concurrent-game limit is reached.
     */
    private void reserveMap() {
        if (reservedSession != null) return;
        if (gameManager.getActiveSessions().size() >= configManager.getQueueMaxConcurrentGames()) return;

        List<Integer> free = new ArrayList<>();
        for (Map.Entry<Integer, Arena> entry : arenaManager.getAllArenas().entrySet()) {
            if (entry.getValue().isReady() && gameManager.getSessionForArena(entry.getKey()) == null) {
                free.add(entry.getKey());
            }
        }
        if (free.isEmpty()) return;

        int arenaNumber = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        try {
            reservedSession = gameManager.createSession(arenaNumber);
        } catch (RuntimeException e) {
            Messages.debug("GAME", "Queue failed to load arena " + arenaNumber + ": " + e.getMessage());
        }
    }

    private boolean hasReadyArena() {
        return arenaManager.getAllArenas().values().stream().anyMatch(Arena::isReady);
    }

    /**
     * Who plays next, in queue order: each player comes with every queued member of their party,
     * and a party that doesn't fit in the slots left waits for the next game instead of being
     * split across two.
     */
    private List<List<Player>> pickGroups(int maxPlayers) {
        List<List<Player>> groups = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        int slotsLeft = maxPlayers;

        for (UUID uuid : queue) {
            if (slotsLeft == 0) break;
            if (seen.contains(uuid)) continue;

            List<Player> group = groupOf(uuid);
            group.forEach(member -> seen.add(member.getUniqueId()));
            if (group.size() > maxPlayers) group = group.subList(0, maxPlayers);
            if (group.size() > slotsLeft) continue;

            groups.add(group);
            slotsLeft -= group.size();
        }
        return groups;
    }

    private List<Player> groupOf(UUID uuid) {
        Party party = partyManager.getPlayerParty(uuid);
        List<Player> group = new ArrayList<>();
        for (UUID queued : queue) {
            boolean sameGroup = party != null ? party.isMember(queued) : queued.equals(uuid);
            Player player = Bukkit.getPlayer(queued);
            if (sameGroup && player != null) group.add(player);
        }
        return group;
    }

    /**
     * Splits the players into two teams as evenly as possible, biggest party first, each party
     * onto the team with the most room left. A party only gets split when it can't fit on either.
     */
    private Map<Player, Integer> assignTeams(List<List<Player>> groups) {
        int total = groups.stream().mapToInt(List::size).sum();
        int capacity = (total + 1) / 2;
        int[] teamSizes = {0, 0};

        List<List<Player>> bySize = new ArrayList<>(groups);
        bySize.sort(Comparator.comparingInt((List<Player> group) -> group.size()).reversed());

        Map<Player, Integer> teams = new LinkedHashMap<>();
        for (List<Player> group : bySize) {
            int primary = teamSizes[0] <= teamSizes[1] ? 0 : 1;
            int room = capacity - teamSizes[primary];
            for (int i = 0; i < group.size(); i++) {
                int team = i < room ? primary : 1 - primary;
                teams.put(group.get(i), team + 1);
                teamSizes[team]++;
            }
            if (group.size() > room) {
                group.forEach(member -> Messages.send(member, "arena.party-split"));
            }
        }
        return teams;
    }

    @Override
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (reservedSession != null) {
            reservedSession.discard();
            reservedSession = null;
        }
        queue.clear();
    }
}
