package me.psikuvit.cashClash.game;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.listener.BlockListener;
import me.psikuvit.cashClash.manager.game.EconomyManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.GamemodeManager;
import me.psikuvit.cashClash.manager.game.RejoinData;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.game.RoundManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.manager.items.MythicItemManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager;
import me.psikuvit.cashClash.manager.player.BonusManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.manager.shop.ShopManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.Investment;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Represents a single Cash Clash game instance
 */
public class GameSession {

    private final UUID sessionId;
    private final int arenaNumber;
    private final World gameWorld;
    private GameState state;
    private int currentRound;

    private final Team teamRed;
    private final Team teamBlue;
    private final Map<UUID, CashClashPlayer> players;

    private RoundData currentRoundData;

    private RoundManager roundManager;
    //private CashQuakeManager cashQuakeManager;
    private BonusManager bonusManager;
    // Shield logic: rounds 1-3 are either shield or shieldless, rounds 4-6 is the other one
    // Determined at game start, consistent for the entire game
    private final boolean rounds1to3HaveShields;

    // Countdown/start preparation
    private BukkitTask startCountdownTask;
    private boolean startingCountdown;
    private int countdownSecondsRemaining;
    private Gamemode gamemode;

    public GameSession(int arenaNumber) {
        this.sessionId = UUID.randomUUID();
        this.arenaNumber = arenaNumber;
        this.state = GameState.WAITING;
        this.currentRound = 1;
        this.teamRed = new Team(1);
        this.teamBlue = new Team(2);
        this.players = new HashMap<>();

        this.startingCountdown = false;
        
        // Determine shield preference for this game (50/50 chance)
        // If true, rounds 1-3 have shields, rounds 4-6 don't
        // If false, rounds 1-3 don't have shields, rounds 4-6 do
        this.rounds1to3HaveShields = new Random().nextBoolean();

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

        Messages.debug("GAME", "GameSession " + sessionId + " created for Arena " + arenaNumber + " with world: " + gameWorld.getName());

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

    public Team getTeamRed() {
        return teamRed;
    }

    public Team getTeamBlue() {
        return teamBlue;
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

    public BonusManager getBonusManager() {
        return bonusManager;
    }

    public Gamemode getGamemode() {
        return gamemode;
    }

    /**
     * Get the arena template for this session
     */
    public TemplateWorld getArenaTemplate() {
        Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
        if (arena == null) return null;
        return ArenaManager.getInstance().getTemplate(arena.getTemplateId());
    }

    /**
     * Returns whether rounds 1-3 have shields in this game session.
     * Rounds 4-6 will have the opposite setting.
     */
    public boolean hasShieldsInRounds1to3() {
        return rounds1to3HaveShields;
    }

    /**public CashQuakeManager getCashQuakeManager() {
        return cashQuakeManager;
    }*/

    public void start() {
        if (state != GameState.WAITING) return;
        if (gameWorld == null) throw new IllegalStateException("Game world is null for session " + sessionId);

        // Play game start sound (warden sonic boom)
        SoundUtils.playTo(players.keySet(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        state = GameState.SHOPPING;

        ArenaManager.getInstance().setArenaState(arenaNumber, GameState.SHOPPING);

        currentRoundData = new RoundData(players.keySet());
        players.values().forEach(CashClashPlayer::initializeRound1);

        roundManager = new RoundManager(this);
        //cashQuakeManager = new CashQuakeManager(this);
        bonusManager = new BonusManager(this);
        
        // Select a random gamemode for this session
        gamemode = GamemodeManager.getInstance().selectGamemode(this);
        gamemode.onGameStart();

        players.keySet().forEach(this::applyKit);
        roundManager.startShoppingPhase(currentRound);
        ScoreboardManager.getInstance().createBoardForSession(this);


        Messages.debug("GAME", "GameSession " + sessionId + " started in Arena " + arenaNumber);
    }

    /**
     * Start a countdown (in seconds) that will begin the game when it reaches zero.
     * If players drop below minPlayers the countdown is canceled.
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

             if (countdownSecondsRemaining % 30 == 0 || countdownSecondsRemaining <= 10)
                 Messages.broadcast(players.keySet(), "<yellow>Game starting in <gold>" + countdownSecondsRemaining + "s</gold>...</yellow>");
             if (countdownSecondsRemaining <= 5 && countdownSecondsRemaining > 0)
                 SoundUtils.playTo(players.keySet(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

             if (countdownSecondsRemaining <= 0) {
                 Messages.broadcast(players.keySet(), "<green>Starting game now!</green>");

                  players.keySet()
                          .stream()
                          .map(Bukkit::getPlayer)
                          .filter(Objects::nonNull)
                          .forEach(player -> {
                              Team team = teamRed.hasPlayer(player.getUniqueId()) ? teamRed : teamBlue;
                              Messages.send(player, "<yellow>You have been assigned to Team " + team.getColoredName() + "</yellow>");
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
        state = GameState.COMBAT;

        ArenaManager.getInstance().setArenaState(arenaNumber, state);
        //if (cashQuakeManager != null) cashQuakeManager.startEventScheduler();

        if (gamemode != null) {
            gamemode.onCombatPhaseStart();
        }

        // Prepare all players for combat
        preparePlayers();
    }

    /**
     * Prepare all players for combat phase (teleport, protection, effects)
     */
    private void preparePlayers() {
        players.keySet().forEach(uuid -> {
            CashClashPlayer ccp = players.get(uuid);
            Player p = ccp.getPlayer();
            if (!isPlayerOnline(p)) return;

            teleportPlayerToSpawn(p, uuid);
            applyRespawnProtection(ccp);
            reapplyKitPotionEffects(p, ccp.getCurrentKit());
        });
    }

    /**
     * Check if player is online
     */
    private boolean isPlayerOnline(Player player) {
        return player != null && player.isOnline();
    }

    /**
     * Teleport player to their team spawn
     */
    private void teleportPlayerToSpawn(Player player, UUID uuid) {
        Location spawn = getSpawnForPlayer(uuid);
        if (spawn != null) player.teleport(spawn);
    }

    /**
     * Apply respawn protection to player
     */
    private void applyRespawnProtection(CashClashPlayer ccp) {
        int protSec = ConfigManager.getInstance().getRespawnProtection();
        ccp.setRespawnProtection(protSec * 1000L);
    }

    /**
     * Reapply kit-specific potion effects at the start of each round.
     */
    private void reapplyKitPotionEffects(Player player, Kit kit) {
        if (kit == null) return;

        switch (kit) {
            case GHOST -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 * 20, 0, false, false));
            case PYROMANIAC -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60 * 20, 0, false, false));
            default -> {} // Other kits don't have potion effects
        }
    }

    private void applyKit(UUID uuid) {
        CashClashPlayer ccp = players.get(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline() || ccp == null) return;

        Kit kitToApply = determineKitForRound(ccp);
        applyKitToPlayer(p, ccp, kitToApply);
    }

    /**
     * Determine which kit to apply based on round
     */
    private Kit determineKitForRound(CashClashPlayer ccp) {
        if (currentRound == 1) {
            return selectAndAssignRandomKit(ccp);
        } else {
            return getOrFallbackKit(ccp);
        }
    }

    /**
     * Select a random kit and assign it to player
     */
    private Kit selectAndAssignRandomKit(CashClashPlayer ccp) {
        Kit kitToApply = getRandomKit();
        ccp.setCurrentKit(kitToApply);
        return kitToApply;
    }

    /**
     * Get player's current kit or fallback to random
     */
    private Kit getOrFallbackKit(CashClashPlayer ccp) {
        Kit kitToApply = ccp.getCurrentKit();
        if (kitToApply == null) {
            kitToApply = getRandomKit();
            ccp.setCurrentKit(kitToApply);
        }
        return kitToApply;
    }

    /**
     * Apply kit to player with layout or default
     */
    private void applyKitToPlayer(Player p, CashClashPlayer ccp, Kit kitToApply) {
        if (currentRound == 1) {
            applyKitWithLayout(p, ccp.getUuid(), kitToApply);
            Messages.send(p, "<green>You have been assigned kit: <yellow>" + kitToApply + "</yellow></green>");
        } else {
            kitToApply.apply(p, currentRound, rounds1to3HaveShields);
        }
    }

    /**
     * Apply kit with layout if available
     */
    private void applyKitWithLayout(Player p, UUID uuid, Kit kit) {
        PlayerData playerData = PlayerDataManager.getInstance().getData(uuid);
        if (playerData.hasKitLayout(kit.name())) {
            Map<Integer, String> layout = playerData.getKitLayout(kit.name());
            kit.applyWithLayout(p, layout, currentRound, rounds1to3HaveShields);
        } else {
            kit.apply(p, currentRound, rounds1to3HaveShields);
        }
    }

    /**
     * Get a spawn location for a player (based on their team and available template spawns), adjusted for the session world.
     */
    public Location getSpawnForPlayer(UUID playerUuid) {
        Team team = teamRed.hasPlayer(playerUuid) ? teamRed : (teamBlue.hasPlayer(playerUuid) ? teamBlue : null);
        if (team == null) return null;

        Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
        if (arena == null) return null;

        TemplateWorld tpl = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (tpl == null) return gameWorld != null ? gameWorld.getSpawnLocation() : null;

        int idx = (int) (new Random().nextDouble() * 3);

        Location templateLoc = team == teamRed ? tpl.getTeamRedSpawn(idx) : tpl.getTeamBlueSpawn(idx);
        if (templateLoc == null) return gameWorld != null ? gameWorld.getSpawnLocation() : null;

        return LocationUtils.copyToWorld(templateLoc, gameWorld);
    }

    public void nextRound() {
        currentRound++;
        // check if the round number exceeds the rounds in the config
        if (currentRound > ConfigManager.getInstance().getTotalRounds()) {
            end();
            return;
        }

        selectLegForRoundTwo();
        initializeRoundData();
        resetRoundState();
        transitionToShoppingPhase();
    }

    /**
     * Select legendaries for round 2
     */
    private void selectLegForRoundTwo() {
        if (currentRound == 2) {
            MythicItemManager.getInstance().selectLegendariesForSession(this);
        }
    }

    /**
     * Initialize round data
     */
    private void initializeRoundData() {
        currentRoundData = new RoundData(players.keySet());
        players.values().forEach(p -> p.initializeRound(currentRound));
    }

    /**
     * Reset all round state
     */
    private void resetRoundState() {
        teamRed.resetForfeitVotes();
        teamBlue.resetForfeitVotes();
        MythicItemManager.getInstance().resetCoinCleaverTrackingForSession(this);
        CustomArmorManager.getInstance().resetRoundTracking();
        BlockListener.cleanupRound(sessionId);
    }

    /**
     * Transition to shopping phase
     */
    private void transitionToShoppingPhase() {
        state = GameState.SHOPPING;
        ArenaManager.getInstance().setArenaState(arenaNumber, state);
        gamemode.onRoundEnd();

        SchedulerUtils.runTask(() -> {
            players.keySet().forEach(this::applyKit);
            healAllPlayers();
        });

        if (roundManager != null) roundManager.startShoppingPhase(currentRound);
    }

    public void end() {
        state = GameState.ENDING;

        // Clear any pending rejoins for this session
        RejoinManager.getInstance().clearSessionRejoins(sessionId);

        Team winner = calculateWinner();
        Location finalSpawn = determineFinalSpawn();

        notifyGameEnd(winner, finalSpawn);
        cleanupManagers();
        cleanupPlayers();
        cleanupArena();

        Messages.debug("GAME", "Arena " + arenaNumber + " reset to WAITING state after game ended");
    }

    /**
     * Clean up all game managers
     */
    private void cleanupManagers() {
        cancelStartCountdown();
        if (roundManager != null) roundManager.cleanup();
        if (bonusManager != null) bonusManager.cleanup();
        if (gamemode != null) {
            GamemodeManager.getInstance().removeGamemode(sessionId);
            gamemode = null;
        }
        CustomArmorManager.getInstance().cleanup();
        CustomItemManager.getInstance().cleanup();
        MythicItemManager.getInstance().cleanup();
    }

    /**
     * Clean up all players and teams
     */
    private void cleanupPlayers() {
        for (UUID u : players.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                clearPlayerKit(p);
                LobbyManager.getInstance().giveLobbyItems(p);
            }
        }

        recordWins();
        removeAllPlayersFromTeams();
    }

    /**
     * Record wins for all players on winning team
     */
    private void recordWins() {
        Team winner = calculateWinner();
        for (UUID u : winner.getPlayers()) {
            PlayerDataManager.getInstance().incWins(u);
        }
    }

    /**
     * Remove all players from teams
     */
    private void removeAllPlayersFromTeams() {
        for (UUID u : teamRed.getPlayers()) teamRed.removePlayer(u);
        for (UUID u : teamBlue.getPlayers()) teamBlue.removePlayer(u);
        teamRed.resetForfeitVotes();
        teamBlue.resetForfeitVotes();
        players.clear();
    }

    /**
     * Clean up arena and shops
     */
    private void cleanupArena() {
        BlockListener.cleanupSession(sessionId);
        ScoreboardManager.getInstance().removeBoard(sessionId);

        if (gameWorld != null) {
            Arena arena = ArenaManager.getInstance().getArena(arenaNumber);
            if (arena != null) {
                ShopManager.getInstance().removeShopsForSession(this);
                arena.deleteWorldCopy(gameWorld);
            }
        }

        ArenaManager.getInstance().setArenaState(arenaNumber, GameState.WAITING);
        ArenaManager.getInstance().setArenaPlayerCount(arenaNumber, 0);

        GameManager.getInstance().removeSession(sessionId);
    }

    /**
     * Resolves all player investments at end of round.
     * Awards bonus, breaks even, or applies penalty based on deaths this round.
     * Called at end of each combat phase, not at game end.
     */
    public void resolveRoundInvestments() {
        for (CashClashPlayer ccp : players.values()) {
            Investment investment = ccp.getCurrentInvestment();
            if (investment == null) continue;

            Player p = Bukkit.getPlayer(ccp.getUuid());
            long returnAmount = investment.calculateReturn();
            String typeName = investment.getType().name().replace("_", " ");

            if (investment.isProfitable()) {
                // 0-1 deaths: Bonus
                ccp.addCoins(returnAmount);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "<green><bold>INVESTMENT SUCCESS!</bold></green>");
                    Messages.send(p, "<green>Your " + typeName + " earned you <gold>$" +
                        String.format("%,d", returnAmount) + "</gold>!</green>");
                    Messages.send(p, "<gray>(Deaths: " + investment.getDeaths() + ")</gray>");
                    SoundUtils.play(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else if (investment.isBreakEven()) {
                // 2 deaths: Break even
                ccp.addCoins(returnAmount);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "<yellow><bold>INVESTMENT BREAK EVEN</bold></yellow>");
                    Messages.send(p, "<yellow>Your " + typeName + " returned your investment of <gold>$" +
                        String.format("%,d", returnAmount) + "</gold>.</yellow>");
                    Messages.send(p, "<gray>(Deaths: " + investment.getDeaths() + ")</gray>");
                    SoundUtils.play(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            } else {
                // 3+ deaths: Loss
                long penalty = investment.getType().getNegativeReturn();
                ccp.deductCoins(penalty);
                if (p != null && p.isOnline()) {
                    Messages.send(p, "<red><bold>INVESTMENT FAILED!</bold></red>");
                    Messages.send(p, "<red>Your " + typeName + " cost you <gold>$" +
                        String.format("%,d", penalty) + "</gold>!</red>");
                    Messages.send(p, "<gray>(Deaths: " + investment.getDeaths() + " - Lost invested amount + penalty)</gray>");
                    SoundUtils.play(p, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                }
            }

            // Clear the investment after resolution
            ccp.setCurrentInvestment(null);
            ccp.setInvestedCoins(0);
        }
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

            var attr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = attr != null ? attr.getValue() : 20.0;
            player.setHealth(Math.clamp(maxHealth, 1.0, player.getHealth()));

        } catch (Exception t) {
            Messages.debug("GAME", "Failed to clear kit for player " + player.getName() + ": " + t.getMessage());
        }
    }

    private Team calculateWinner() {
        return getTeamRedCoins() > getTeamBlueCoins() ? teamRed : teamBlue;
    }

    public long getTeamRedCoins() {
        return teamRed.getPlayers().stream().mapToLong(p -> players.get(p).getCoins()).sum();
    }

    public long getTeamBlueCoins() {
        return teamBlue.getPlayers().stream().mapToLong(p -> players.get(p).getCoins()).sum();
    }

    public void addPlayer(Player player, int teamNumber) {
        // Clear lobby items when joining a game
        LobbyManager.getInstance().clearLobbyItems(player);

        CashClashPlayer ccPlayer = new CashClashPlayer(player);
        players.put(player.getUniqueId(), ccPlayer);

        if (teamNumber == 1) teamRed.addPlayer(player.getUniqueId());
        else teamBlue.addPlayer(player.getUniqueId());
        ArenaManager.getInstance().incrementPlayerCount(arenaNumber);
    }

    public void removePlayer(Player player) {
        if (player == null) return;

        // Clean up kit items and effects before removing player from session
        clearPlayerKit(player);

        // Notify gamemode about player removal for any special cleanup (e.g., banners in CTF)
        if (gamemode != null) {
            gamemode.onPlayerRemove(player);
        }

        players.remove(player.getUniqueId());
        teamRed.removePlayer(player.getUniqueId());
        teamBlue.removePlayer(player.getUniqueId());
        ArenaManager.getInstance().decrementPlayerCount(arenaNumber);
    }

    public Team getPlayerTeam(Player player) {
        UUID uuid = player.getUniqueId();
        if (teamRed.hasPlayer(uuid)) return teamRed;
        if (teamBlue.hasPlayer(uuid)) return teamBlue;
        return null;
    }

    public Team getOpposingTeam(Team team) {
        return team == teamRed ? teamBlue : teamRed;
    }

    public void requestForfeit(Player requester) {
        Team team = getPlayerTeam(requester);
        if (team == null) return;

        if (!validateForfeitRequest(team, requester)) return;

        initiateForfeitVote(team, requester);
    }

    /**
     * Validate if forfeit request is allowed
     */
    private boolean validateForfeitRequest(Team team, Player requester) {
        int aliveCount = countAliveTeammates(team);
        if (aliveCount > 2) {
            Messages.send(requester, "<red>You can only start a forfeit when at least 2 teammates have died.</red>");
            return false;
        }

        if (!checkRecentDamageGrace(team, aliveCount)) {
            return false;
        }

        long deadCount = team.getPlayers().stream().filter(uuid -> !currentRoundData.isAlive(uuid)).count();
        if (deadCount < 2) {
            Messages.send(requester, "<red>At least two teammates must be dead to start a forfeit.</red>");
            return false;
        }

        return true;
    }

    /**
     * Count alive teammates on a team
     */
    private int countAliveTeammates(Team team) {
        return (int) team.getPlayers().stream().filter(uuid -> currentRoundData.isAlive(uuid)).count();
    }

    /**
     * Check if team has recent damage within grace period
     */
    private boolean checkRecentDamageGrace(Team team, int aliveCount) {
        long now = System.currentTimeMillis();
        int combatGrace = ConfigManager.getInstance().getForfeitCombatGrace();
        boolean anyRecentDamage = team.getPlayers().stream()
                .anyMatch(uuid -> currentRoundData.getLastDamageTime(uuid) + (combatGrace * 1000L) > now);

        if (aliveCount > 1 && anyRecentDamage) {
            Player requester = Bukkit.getPlayer(team.getPlayers().iterator().next());
            if (requester != null) {
                Messages.send(requester, "<red>Can't start forfeit while any teammate has taken damage in the last " + combatGrace + " seconds.</red>");
            }
            return false;
        }
        return true;
    }

    /**
     * Initiate forfeit vote
     */
    private void initiateForfeitVote(Team team, Player requester) {
        long now = System.currentTimeMillis();
        if (team.getForfeitStartTime() == 0L) team.setForfeitStartTime(now);
        team.addForfeitVote(requester.getUniqueId());

        Messages.broadcastToTeam(team, "<yellow>Forfeit vote started by " + requester.getName() + " - agreement required by all teammates. Type /cc forfeit to vote.</yellow>");
        if (team.hasAllForfeitVotes()) executeForfeit(team);
    }

    public void castForfeitVote(Player voter) {
        Team team = getPlayerTeam(voter);
        if (team == null) return;
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

        applyForfeitPenalty(forfeitingTeam);
        applyForfeitBonus(other, bonus);

        Messages.broadcast(players.keySet(), "<gold>Team " + forfeitingTeam.getColoredName() + " has chosen to forfeit the round. Team " + other.getColoredName() + " earns " + bonus + " each.</gold>");
        if (roundManager != null) roundManager.endCombatPhase();
    }

    /**
     * Apply forfeit penalty to forfeiting team
     */
    private void applyForfeitPenalty(Team forfeitingTeam) {
        forfeitingTeam.getPlayers().forEach(uuid -> {
            var p = players.get(uuid);
            if (p != null) EconomyManager.applyForfeitPenalty(this, p);
        });
    }

    /**
     * Apply forfeit bonus to winning team
     */
    private void applyForfeitBonus(Team winningTeam, long bonus) {
        winningTeam.getPlayers().forEach(uuid -> {
            var p = players.get(uuid);
            if (p != null) p.addCoins(bonus);
        });
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
        Team loser = (winner == teamRed) ? teamBlue : teamRed;
        String winnerList = formatPlayerList(winner);
        String loserList = formatPlayerList(loser);

        players.keySet().forEach(uuid -> sendGameEndMessages(uuid, winner, winnerList, loserList, finalSpawn));
    }

    /**
     * Format a player list for display
     */
    private String formatPlayerList(Team team) {
        return team.getPlayers().stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : uuid.toString();
                })
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    /**
     * Send game end messages to a player
     */
    private void sendGameEndMessages(UUID uuid, Team winner, String winnerList, String loserList, Location finalSpawn) {
        Player player = Bukkit.getPlayer(uuid);
        if (!isPlayerOnline(player)) return;

        if (finalSpawn != null) player.teleport(finalSpawn);

        preparePlayerForGameEnd(player);

        boolean isWinner = winner.getPlayers().contains(uuid);
        Messages.send(player, isWinner ? "<green><bold>YOU WIN!</bold></green>" : "<red><bold>YOU LOSE</bold></red>");
        Messages.send(player, "<yellow>Winning Team: " + winner.getColoredName() + "</yellow>");
        Messages.send(player, "<gray>Winners: " + winnerList + "</gray>");
        Messages.send(player, "<gray>Losers: " + loserList + "</gray>");
        Messages.send(player, "<gray>Thanks for playing!</gray>");
    }

    /**
     * Prepare player for game end (heal, food, etc.)
     */
    private void preparePlayerForGameEnd(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;

        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.closeInventory();
    }

    public void healAllPlayers() {
        for (UUID uuid : players.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                var attr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = attr != null ? attr.getValue() : 20.0;
                player.setHealth(maxHealth);
                player.setFoodLevel(20);
            }
        }
    }

    // ==================== REJOIN SYSTEM ====================

    /**
     * Mark a player as disconnected but keep their data for potential rejoin.
     * This is called when a player disconnects during an active game.
     *
     * @param player The player who disconnected
     */
    public void markPlayerDisconnected(Player player) {
        UUID uuid = player.getUniqueId();
        CashClashPlayer ccp = players.get(uuid);
        if (ccp == null) return;

        Messages.debug("REJOIN", "Marking player " + player.getName() + " as disconnected in session " + sessionId);

        // Don't remove from teams - just mark for potential rejoin
        // The RejoinManager will handle the timeout and removal if they don't return
    }

    /**
     * Handle a player's rejoin timeout expiring.
     * Called by RejoinManager when a player doesn't rejoin in time.
     *
     * @param playerUuid The UUID of the player who didn't rejoin
     */
    public void handleRejoinTimeout(UUID playerUuid) {
        CashClashPlayer ccp = players.remove(playerUuid);
        if (ccp != null) {
            teamRed.removePlayer(playerUuid);
            teamBlue.removePlayer(playerUuid);
            ArenaManager.getInstance().decrementPlayerCount(arenaNumber);
            Messages.debug("REJOIN", "Player " + playerUuid + " removed due to rejoin timeout");
        }

        // Check if we need to end the game due to not enough players
        checkForceEndGame();
    }

    /**
     * Rejoin a player to the session, restoring their data.
     *
     * @param player The player rejoining
     * @param data   The rejoin data containing their saved state
     * @return true if rejoin was successful
     */
    public boolean rejoinPlayer(Player player, RejoinData data) {
        UUID uuid = player.getUniqueId();

        // Check if player is still in our records (they should be marked as disconnected)
        CashClashPlayer existingCcp = players.get(uuid);

        if (existingCcp == null) {
            // Player was already removed (e.g., game ended while they were gone)
            // Re-add them to the appropriate team
            CashClashPlayer newCcp = new CashClashPlayer(player);
            players.put(uuid, newCcp);

            if (data.teamNumber() == 1) {
                teamRed.addPlayer(uuid);
            } else {
                teamBlue.addPlayer(uuid);
            }

            existingCcp = newCcp;
        }

        // Restore player state from rejoin data
        restorePlayerState(player, existingCcp, data);

        // Teleport to spawn location
        Location spawn = getSpawnForPlayer(uuid);
        if (spawn != null) {
            player.teleport(spawn);
        } else if (gameWorld != null) {
            player.teleport(gameWorld.getSpawnLocation());
        }

        // Apply respawn protection
        int protSec = ConfigManager.getInstance().getRespawnProtection();
        existingCcp.setRespawnProtection(protSec * 1000L);

        // Set up scoreboard
        ScoreboardManager.getInstance().setScoreboard(player);

        Messages.send(player, "<green>You have successfully rejoined the game!</green>");
        Messages.send(player, "<yellow>Round: <gold>" + currentRound + "</gold> | Lives: <gold>" + existingCcp.getLives() + "</gold></yellow>");

        return true;
    }

    /**
     * Restore a player's state from rejoin data.
     */
    private void restorePlayerState(Player player, CashClashPlayer ccp, RejoinData data) {
        // Restore kit
        if (data.kit() != null) {
            ccp.setCurrentKit(data.kit());
        }

        // Restore economy
        if (ConfigManager.getInstance().isRejoinRestoreBalance()) {
            ccp.setCoins(data.coins());
        }

        // Restore lives and stats
        ccp.setLives(data.lives());

        // Restore inventory
        if (ConfigManager.getInstance().isRejoinRestoreInventory()) {
            player.getInventory().clear();

            if (data.inventoryContents() != null) {
                player.getInventory().setContents(data.inventoryContents());
            }
            if (data.armorContents() != null) {
                player.getInventory().setArmorContents(data.armorContents());
            }
            if (data.offhandItem() != null) {
                player.getInventory().setItemInOffHand(data.offhandItem());
            }
        } else {
            // Apply kit if not restoring inventory
            if (data.kit() != null) {
                data.kit().apply(player);
            }
        }

        // Restore purchase history
        if (data.purchaseHistory() != null) {
            for (PurchaseRecord record : data.purchaseHistory()) {
                ccp.addPurchase(record);
            }
        }

        // Restore owned enchants
        if (data.ownedEnchants() != null) {
            for (Map.Entry<EnchantEntry, Integer> entry : data.ownedEnchants().entrySet()) {
                ccp.setOwnedEnchantLevel(entry.getKey(), entry.getValue());
            }
        }

        // Reset health and food
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setGameMode(GameMode.SURVIVAL);

        // Clear potion effects
        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);
    }

    /**
     * Check if the game should be force-ended due to not enough players.
     */
    private void checkForceEndGame() {
        // If both teams have no players, end the game
        if (teamRed.getPlayers().isEmpty() && teamBlue.getPlayers().isEmpty()) {
            Messages.debug("GAME", "Force ending game - no players remain");
            end();
            return;
        }

        // If one team has no players and the game is active
        if (state != GameState.WAITING && state != GameState.ENDING) {
            if (teamRed.getPlayers().isEmpty() || teamBlue.getPlayers().isEmpty()) {
                Messages.broadcast(players.keySet(), "<yellow>Game ending - one team has no remaining players.</yellow>");
                end();
            }
        }
    }
}
