package me.psikuvit.cashClash.game;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.manager.CashQuakeManager;
import me.psikuvit.cashClash.manager.EconomyManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.manager.RoundManager;
import me.psikuvit.cashClash.manager.ScoreboardManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.manager.PlayerDataManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.ShopManager;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collection;
import java.util.Random;

/**
 * Represents a single Cash Clash game instance
 */
public class GameSession {

    private final UUID sessionId;
    private final int arenaNumber;
    private final World gameWorld;
    private GameState state;
    private int currentRound;

    private final Team team1;
    private final Team team2;
    private final Map<UUID, CashClashPlayer> players;

    private RoundData currentRoundData;
    private long roundStartTime;
    private long phaseStartTime;

    private RoundManager roundManager;
    private CashQuakeManager cashQuakeManager;

    // Countdown/start preparation
    private BukkitTask startCountdownTask;
    private boolean startingCountdown;
    private int countdownSecondsRemaining;

    public GameSession(int arenaNumber) {
        this.sessionId = UUID.randomUUID();
        this.arenaNumber = arenaNumber;
        this.state = GameState.WAITING;
        this.currentRound = 0;
        this.team1 = new Team(1);
        this.team2 = new Team(2);
        this.players = new HashMap<>();

        this.startingCountdown = false;

        // Get the fixed arena
        Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
        if (arena == null) {
            throw new IllegalStateException("Arena " + arenaNumber + " not found!");
        }

        ArenaManager.getInstance().setArenaState(arenaNumber, GameState.WAITING);

        this.gameWorld = arena.createWorldCopy(sessionId);
        if (this.gameWorld == null) {
            throw new IllegalStateException("Failed to create world copy for session " + sessionId);
        }

        CashClashPlugin.getInstance().getLogger().info(
            "GameSession " + sessionId + " created for Arena " + arenaNumber + " with world: " + gameWorld.getName()
        );

        // create shops in the copied world for this session
        ShopManager.getInstance().createShopsForSession(this);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getArenaNumber() {
        return arenaNumber;
    }

    public World getGameWorld() {
        return gameWorld;
    }

    public GameState getState() {
        return state;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public Team getTeam1() {
        return team1;
    }

    public Team getTeam2() {
        return team2;
    }

    public Collection<UUID> getPlayers() {
        return players.keySet();
    }

    public CashClashPlayer getCashClashPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public RoundData getCurrentRoundData() {
        return currentRoundData;
    }

    public void start() {
        if (state != GameState.WAITING) return;
        if (gameWorld == null) throw new IllegalStateException("Game world is null for session " + sessionId);

        state = GameState.ROUND_1_SHOPPING;
        currentRound = 1;
        phaseStartTime = System.currentTimeMillis();

        ArenaManager.getInstance().setArenaState(arenaNumber, GameState.ROUND_1_SHOPPING);
        ScoreboardManager.getInstance().createBoardForSession(this);

        currentRoundData = new RoundData(currentRound, players.keySet());

        players.values().forEach(CashClashPlayer::initializeRound1);

        roundManager = new RoundManager(this);
        cashQuakeManager = new CashQuakeManager(this);

        roundManager.startShoppingPhase(currentRound);
        players.keySet().forEach(this::applyKit);
        ScoreboardManager.getInstance().createBoardForSession(this);


        CashClashPlugin.getInstance().getLogger().info("GameSession " + sessionId + " started in Arena " + arenaNumber);
    }

    /**
     * Start a countdown (in seconds) that will begin the game when it reaches zero.
     * If players drop below minPlayers the countdown is cancelled.
     */
    public synchronized void startCountdown(int seconds) {
        if (startingCountdown) return;
        startingCountdown = true;
        countdownSecondsRemaining = seconds;

        int minPlayers = ConfigManager.getInstance().getMinPlayers();

        Messages.broadcast(players.keySet(), "<yellow>Minimum players reached! Starting game in <gold>" + seconds + "s</gold>. Join now!</yellow>");

        startCountdownTask = SchedulerUtils.runTaskTimer(() -> {
             if (state != GameState.WAITING) {
                 cancelStartCountdown();
                 return;
             }

             int count = players.size();
             if (count < minPlayers) {
                 Messages.broadcast(players.keySet(), "<red>Not enough players, starting countdown cancelled.</red>");
                 cancelStartCountdown();
                 return;
             }

             if (countdownSecondsRemaining % 30 == 0 || countdownSecondsRemaining <= 10) Messages.broadcast(players.keySet(), "<yellow>Game starting in <gold>" + countdownSecondsRemaining + "s</gold>...</yellow>");
             SoundUtils.playTo(players.keySet(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

             if (countdownSecondsRemaining <= 0) {
                 Messages.broadcast(players.keySet(), "<green>Starting game now!</green>");
                 SoundUtils.playTo(players.keySet(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                 players.keySet()
                         .stream()
                         .map(Bukkit::getPlayer)
                         .filter(Objects::nonNull)
                         .forEach(player -> {
                             int teamNum = team1.hasPlayer(player.getUniqueId()) ? 1 : 2;
                             Messages.send(player, "<yellow>You have been assigned to Team <gray>" + teamNum + "</gray></yellow>");
                         });
                 cancelStartCountdown();
                 start();
                 return;
             }
             countdownSecondsRemaining--;
         }, 0L, 20L);
     }

    /**
     * Expose remaining time from the active RoundManager for scoreboard {time} placeholder.
     */
    public int getTimeRemaining() {
        return roundManager.getTimeRemaining();
    }

    public synchronized void cancelStartCountdown() {
        if (!startingCountdown) return;
        startingCountdown = false;
        if (startCountdownTask != null) {
            startCountdownTask.cancel();
            startCountdownTask = null;
        }
    }

    public boolean isStartingCountdown() {
        return startingCountdown;
    }

    public void startCombatPhase() {
        switch (currentRound) {
            case 1 -> state = GameState.ROUND_1_COMBAT;
            case 2 -> state = GameState.ROUND_2_COMBAT;
            case 3 -> state = GameState.ROUND_3_COMBAT;
            case 4 -> state = GameState.ROUND_4_COMBAT;
            case 5 -> state = GameState.ROUND_5_COMBAT;
        }
        long now = System.currentTimeMillis();
        roundStartTime = phaseStartTime = now;

        ArenaManager.getInstance().setArenaState(arenaNumber, state);
        if (cashQuakeManager != null) cashQuakeManager.startEventScheduler();

        players.keySet().forEach(uuid -> {
            CashClashPlayer ccp = players.get(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || ccp == null) return;

            Location spawn = getSpawnForPlayer(uuid);
            if (spawn != null) p.teleport(spawn);

            int protSec = ConfigManager.getInstance().getRespawnProtection();
            ccp.setRespawnProtection(protSec * 1000L);
        });
    }

    private void applyKit(UUID uuid) {
        CashClashPlayer ccp = players.get(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline() || ccp == null) return;

        Kit randomKit = getRandomKit();

        ccp.setCurrentKit(randomKit);
        randomKit.apply(p);
        Messages.send(p, "<green>You have been assigned kit: <yellow>" + randomKit + "</yellow></green>");
    }

    /**
     * Get a spawn location for a player (based on their team and available template spawns), adjusted for the session world.
     */
    public Location getSpawnForPlayer(UUID playerUuid) {
        Team team = team1.hasPlayer(playerUuid) ? team1 : (team2.hasPlayer(playerUuid) ? team2 : null);
        if (team == null) return null;

        Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
        if (arena == null) return null;

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tpl == null) return gameWorld != null ? gameWorld.getSpawnLocation() : null;

        int idx = (int) (new Random().nextDouble() * 3);

        Location templateLoc = team == team1 ? tpl.getTeam1Spawn(idx) : tpl.getTeam2Spawn(idx);
        if (templateLoc == null) return gameWorld != null ? gameWorld.getSpawnLocation() : null;

        // Adjust template location coordinates into the session's copied world
        return LocationUtils.adjustLocationToWorld(templateLoc, gameWorld);
    }

    public void nextRound() {
        currentRound++;
        if (currentRound > 5) {
            end();
            return;
        }

        currentRoundData = new RoundData(currentRound, players.keySet());
        players.values().forEach(p -> p.initializeRound(currentRound));

        if (cashQuakeManager != null) cashQuakeManager.resetRoundEvents();
        team1.resetForfeitVotes();
        team2.resetForfeitVotes();

        switch (currentRound) {
            case 2 -> state = GameState.ROUND_2_SHOPPING;
            case 3 -> state = GameState.ROUND_3_SHOPPING;
            case 4 -> state = GameState.ROUND_4_SHOPPING;
            case 5 -> state = GameState.ROUND_5_SHOPPING;
        }
        phaseStartTime = System.currentTimeMillis();
        ArenaManager.getInstance().setArenaState(arenaNumber, state);
        if (roundManager != null) roundManager.startShoppingPhase(currentRound);
    }

    public void end() {
        state = GameState.ENDING;
        Team winner = calculateWinner();
        Location finalSpawn = determineFinalSpawn();

        notifyGameEnd(winner, finalSpawn);

        cancelStartCountdown();
        if (roundManager != null) roundManager.cleanup();
        if (cashQuakeManager != null) cashQuakeManager.cleanup();

        if (gameWorld != null) {
            Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
            if (arena != null) {
                ShopManager.getInstance().removeShopsForSession(this);
                arena.deleteWorldCopy(gameWorld);
            }
        }

        // remove scoreboard for session
        ScoreboardManager.getInstance().removeBoard(sessionId);

        // Clear kits/effects for all players in this session before removing them
        for (UUID u : new ArrayList<>(players.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) clearPlayerKit(p);
        }

        ArenaManager.getInstance().setArenaState(arenaNumber, GameState.WAITING);
        ArenaManager.getInstance().setArenaPlayerCount(arenaNumber, 0);

        GameManager.getInstance().removeSession(sessionId);

        // Persist win statistics for the winning team

        if (winner != null) {
            for (UUID u : winner.getPlayers()) {
                PlayerDataManager.getInstance().incWins(u);
            }
        }

        for (UUID u : team1.getPlayers()) team1.removePlayer(u);
        for (UUID u : team2.getPlayers()) team2.removePlayer(u);
        team1.resetForfeitVotes(); team2.resetForfeitVotes();
        players.clear();

        CashClashPlugin.getInstance().getLogger().info("Arena " + arenaNumber + " reset to WAITING state after game ended");
    }

    /**
     * Remove items and potion effects granted by kits from the player.
     */
    private void clearPlayerKit(Player player) {
        try {
            if (player == null) return;

            // Clear inventory and armor
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);

            for (PotionEffect t : player.getActivePotionEffects()) player.removePotionEffect(t.getType());

            // Reset health and food to safe defaults (do not change max health)
            var attr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;
            player.setHealth(Math.max(1.0, Math.min(maxHealth, player.getHealth())));

        } catch (Exception t) {
            CashClashPlugin.getInstance().getLogger().warning("Failed to clear kit for player " + player.getName() + ": " + t.getMessage());
        }
    }

    private Team calculateWinner() {
        return getTeam1Coins() > getTeam2Coins() ? team1 : team2;
    }

    public long getTeam1Coins() {
        return team1.getPlayers().stream().mapToLong(p -> players.get(p).getCoins()).sum();
    }

    public long getTeam2Coins() {
        return team2.getPlayers().stream().mapToLong(p -> players.get(p).getCoins()).sum();
    }

    public void addPlayer(Player player, int teamNumber) {
        CashClashPlayer ccPlayer = new CashClashPlayer(player);
        players.put(player.getUniqueId(), ccPlayer);

        if (teamNumber == 1) team1.addPlayer(player.getUniqueId());
        else team2.addPlayer(player.getUniqueId());
        ArenaManager.getInstance().incrementPlayerCount(arenaNumber);
    }

    public void removePlayer(Player player) {
        if (player == null) return;

        // Clean up kit items and effects before removing player from session
        clearPlayerKit(player);

        players.remove(player.getUniqueId());
        team1.removePlayer(player.getUniqueId());
        team2.removePlayer(player.getUniqueId());
        ArenaManager.getInstance().decrementPlayerCount(arenaNumber);
    }

    public Team getPlayerTeam(Player player) {
        UUID uuid = player.getUniqueId();
        if (team1.hasPlayer(uuid)) return team1;
        if (team2.hasPlayer(uuid)) return team2;
        return null;
    }

    public Team getOpposingTeam(Team team) {
        return team == team1 ? team2 : team1;
    }

    public RoundData getRoundData() { return currentRoundData; }

    public void requestForfeit(Player requester) {
        Team team = getPlayerTeam(requester);
        if (team == null) return;

        int aliveCount = (int) team.getPlayers().stream().filter(uuid -> currentRoundData.isAlive(uuid)).count();
        if (aliveCount > 2) {
            Messages.send(requester, "<red>You can only start a forfeit when at least 2 teammates have died.</red>");
            return;
        }

        long now = System.currentTimeMillis();
        int combatGrace = ConfigManager.getInstance().getForfeitCombatGrace();
        boolean anyRecentDamage = team.getPlayers().stream().anyMatch(uuid -> currentRoundData.getLastDamageTime(uuid) + (combatGrace * 1000L) > now);

        if (aliveCount > 1 && anyRecentDamage) {
            Messages.send(requester, "<red>Can't start forfeit while any teammate has taken damage in the last " + combatGrace + " seconds.</red>");
            return;
        }

        long deadCount = team.getPlayers().stream().filter(uuid -> !currentRoundData.isAlive(uuid)).count();
        if (deadCount < 2) {
            Messages.send(requester, "<red>At least two teammates must be dead to start a forfeit.</red>");
            return;
        }

        if (team.getForfeitStartTime() == 0L) team.setForfeitStartTime(now);
        team.addForfeitVote(requester.getUniqueId());

        Messages.broadcastToTeam(team, "<yellow>Forfeit vote started by " + requester.getName() + " - agreement required by all teammates. Type /cc forfeit to vote.</yellow>");
        if (team.hasAllForfeitVotes()) executeForfeit(team);
    }

    public void castForfeitVote(Player voter) {
        Team team = getPlayerTeam(voter); if (team == null) return;
        if (team.getForfeitVotes().isEmpty()) {
            requestForfeit(voter);
            return;
        }

        team.addForfeitVote(voter.getUniqueId());
        Messages.broadcastToTeam(team, "<yellow>" + voter.getName() + " has voted to forfeit. (" + team.getForfeitVotes().size() + "/" + team.getSize() + ")</yellow>");
        if (team.hasAllForfeitVotes()) executeForfeit(team);
    }

    private void executeForfeit(Team forfeitingTeam) {
        Team other = getOpposingTeam(forfeitingTeam);
        long bonus = ConfigManager.getInstance().getForfeitBonus();

        forfeitingTeam.setForfeitVoted(true);
        forfeitingTeam.getPlayers().forEach(uuid -> {
            var p = players.get(uuid);
            if (p != null) EconomyManager.applyForfeitPenalty(this, p);
        });

        other.getPlayers().forEach(uuid -> {
            var p = players.get(uuid);
            if (p != null) p.addCoins(bonus);
        });

        Messages.broadcast(players.keySet(), "<gold>Team " + forfeitingTeam.getTeamNumber() + " has chosen to forfeit the round. Team " + other.getTeamNumber() + " earns " + bonus + " each.</gold>");
        if (roundManager != null) roundManager.endCombatPhase();
    }


    private Kit getRandomKit() {
        Kit[] kits = Kit.values();
        return kits[(int) (Math.random() * kits.length)];
    }

    private Location determineFinalSpawn() {
        Location spawnLoc = ArenaManager.getInstance().getServerLobbySpawn();
        if (spawnLoc != null) return spawnLoc;
        World mainWorld = Bukkit.getWorlds().getFirst();
        return mainWorld != null ? mainWorld.getSpawnLocation() : null;
    }

    private void notifyGameEnd(Team winner, Location finalSpawn) {
        players.keySet().forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;
            if (finalSpawn != null) player.teleport(finalSpawn);

            player.setGameMode(GameMode.SURVIVAL);
            var attr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;

            player.setHealth(Math.max(1.0, Math.min(maxHealth, player.getHealth())));
            player.setFoodLevel(20);
            player.closeInventory();

            Messages.send(player, "<green><bold>=== GAME ENDED ===</bold></green>");
            Messages.send(player, "<yellow>Winning Team: <gray>" + winner.getTeamNumber() + "</gray></yellow>");
            Messages.send(player, "<gray>Thanks for playing!</gray>");
        });
    }
}
