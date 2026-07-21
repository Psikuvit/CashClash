package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.gamemode.FinalStandManager;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.game.TimerDisplayUtils;
import me.psikuvit.cashClash.util.game.ctf.FlagBannerUtils;
import me.psikuvit.cashClash.util.game.ctf.FlagBaseMechanicsUtils;
import me.psikuvit.cashClash.util.game.ctf.FlagEffectsUtils;
import me.psikuvit.cashClash.util.game.ctf.FlagPickupValidator;
import me.psikuvit.cashClash.util.items.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Capture the Flag Gamemode
 * Goal: Steal the enemy flag and return it to your base (first to 2 captures wins)
 */
public class CaptureTheFlagGamemode extends Gamemode {

     private static final int WIN_CONDITION = 2;
     private static final long CAPTURE_BONUS = 15000;
     private static final long CAPTURE_TIMER_MS = 45 * 1000; // 45 seconds for bonus
     private static final long FLAG_PICKUP_DURATION_MS = 3000; // 3 seconds to pick up flag

    private final Map<Integer, Integer> flagCaptures;
    private final Map<Integer, Integer> suddenDeathCycleCaptures;
    private final Map<Integer, FlagState> flagStates; // Red flag (team 1) and Blue flag (team 2)
    private final Map<Integer, Location> flagBaseLocations;
    private final Map<Integer, BukkitTask> flagReturnTasks;
    private final Map<Integer, BukkitTask> flagReturnDisplayTasks;
    private final Map<Integer, Long> flagReturnExpiry; // scheduled return time (ms) for dropped flags
    private final Map<UUID, Long> playerCircleTimestamps; // Track when each player entered the circle
    private final Map<UUID, Integer> playerNearestFlagTeam; // Track which flag team player is near
    private final Set<UUID> stalemateMsgShown; // Track players who've been told about stalemate in current state
    private final Map<UUID, Long> playerHeartTimestamps; // Track when each player received a heart bonus (for 45s timer)

      private final SuddenDeathManager suddenDeathManager;
      private final FinalStandManager finalStandManager;
      private BukkitTask carrierGlowTask;
      private BukkitTask bannerRotationTask;
      private BukkitTask flagPickupTask;
      private int suddenDeathWinningTeam;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);

        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.suddenDeathCycleCaptures = new HashMap<>(2);
        this.flagStates = new HashMap<>(2);
        this.flagBaseLocations = new HashMap<>(2);
        this.flagReturnTasks = new HashMap<>(2);
        this.flagReturnDisplayTasks = new HashMap<>(2);
        this.flagReturnExpiry = new HashMap<>(2);
        this.playerCircleTimestamps = new HashMap<>();
        this.playerNearestFlagTeam = new HashMap<>();
        this.stalemateMsgShown = new HashSet<>();
        this.playerHeartTimestamps = new HashMap<>();
         this.suddenDeathManager = new SuddenDeathManager(session, this);
         this.finalStandManager = new FinalStandManager(session, this);
         this.carrierGlowTask = null;
         this.bannerRotationTask = null;
         this.flagPickupTask = null;
         this.suddenDeathWinningTeam = 0;

        // Pre-populate flag states and capture map
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        suddenDeathCycleCaptures.put(1, 0);
        suddenDeathCycleCaptures.put(2, 0);
        flagStates.put(1, FlagState.create());
        flagStates.put(2, FlagState.create());
    }

    // ========= OVERRIDDEN METHODS =========

    @Override
    public void onGameStart() {
        Messages.debug("[CTF] Gamemode started");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.game-started");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.objective");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.bonus-info");
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[CTF] Combat phase started");
        if (suddenDeathManager.isInSuddenDeath()) {
            // Reset sudden death kill counters for this cycle
            suddenDeathCycleCaptures.put(1, 0);
            suddenDeathCycleCaptures.put(2, 0);
            Messages.debug("[CTF] Sudden death cycle started - capture counters reset");
        }

        // Initialize banners at flag plates
        initializeBanners();

        // Reset flag state each round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());

        // Clear circle tracking data
        playerCircleTimestamps.clear();
        playerNearestFlagTeam.clear();
        stalemateMsgShown.clear();
        playerHeartTimestamps.clear();

         // Start carrier glow effect task
         startCarrierGlowEffect();

         // Start banner rotation task
         startBannerRotationTask();

         // Start flag pickup task (3-second circle mechanic)
         startFlagPickupTask();
     }

    @Override
    public void onRoundEnd() {
        // Remove all banners from players and reset positions
        removeBannersFromPlayers();

        // Reset state for next round
        suddenDeathManager.resetForNewRound();
        finalStandManager.cancel();
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        suddenDeathCycleCaptures.put(1, 0);
        suddenDeathCycleCaptures.put(2, 0);
        suddenDeathWinningTeam = 0;
        flagReturnTasks.values().forEach(this::cancelTask);
        flagReturnTasks.clear();
        flagReturnDisplayTasks.values().forEach(this::cancelTask);
        flagReturnDisplayTasks.clear();
        flagReturnExpiry.clear();
        // Reset flag states but preserve the plate locations and banners for next round
        returnFlagToBase(1);
        returnFlagToBase(2);
        stalemateMsgShown.clear();

        // Clear circle tracking data
        playerCircleTimestamps.clear();
        playerNearestFlagTeam.clear();
        playerHeartTimestamps.clear();

         // Reset task references (tasks will be recreated in next combat phase)
         this.carrierGlowTask = null;
         this.bannerRotationTask = null;
         this.flagPickupTask = null;
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();

        // Check if victim was carrying a flag
        handleFlagHolderDeath(1, victimUuid, victim);
        handleFlagHolderDeath(2, victimUuid, victim);
    }

    @Override
    public void onPlayerSpawn(Player player) {
        suddenDeathManager.onPlayerSpawn(player);
    }

    @Override
    public void onPlayerRemove(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if this player was carrying any flags and remove their banners
        removeFlagIfCarriedByPlayer(1, playerUuid, player);
        removeFlagIfCarriedByPlayer(2, playerUuid, player);
    }

    @Override
    public boolean checkGameWinner() {
        int captures1 = flagCaptures.get(1);
        int captures2 = flagCaptures.get(2);

        if (suddenDeathWinningTeam > 0) {
            return true;
        }

        if (!suddenDeathManager.isInSuddenDeath() && (captures1 >= WIN_CONDITION || captures2 >= WIN_CONDITION)) {
            Messages.debug("[CTF] Game winner found - Team " + (captures1 >= WIN_CONDITION ? 1 : 2) + " with " + Math.max(captures1, captures2) + " captures");
            return true;
        }

        if (finalStandManager.isActive() && (captures1 >= WIN_CONDITION || captures2 >= WIN_CONDITION)) {
            Messages.debug("[CTF] Final Stand winner found - Team " + (captures1 >= WIN_CONDITION ? 1 : 2) + " with " + Math.max(captures1, captures2) + " captures");
            return true;
        }

        return false;
    }

    @Override
    public int getWinningTeam() {
        if (suddenDeathWinningTeam > 0) {
            return suddenDeathWinningTeam;
        }

        if ((!suddenDeathManager.isInSuddenDeath() || finalStandManager.isActive()) && flagCaptures.get(1) >= WIN_CONDITION) {
            return 1;
        } else if ((!suddenDeathManager.isInSuddenDeath() || finalStandManager.isActive()) && flagCaptures.get(2) >= WIN_CONDITION) {
            return 2;
        }
        return 0;
    }

     @Override
     public void cleanup() {
         // Remove banners from all players first
         removeBannersFromPlayers();

         // Cancel all tasks
         cancelTask(carrierGlowTask);
         cancelTask(bannerRotationTask);
         cancelTask(flagPickupTask);
         flagReturnTasks.values().forEach(this::cancelTask);
         flagReturnTasks.clear();
         flagReturnDisplayTasks.values().forEach(this::cancelTask);
         flagReturnDisplayTasks.clear();
         flagReturnExpiry.clear();

         // Cancel carrying tasks for all flags
         for (FlagState flag : flagStates.values()) {
             if (flag != null && flag.carryingTask() != null) {
                 cancelTask(flag.carryingTask());
             }
         }

         // Cleanup sudden death manager
         finalStandManager.cancel();
         suddenDeathManager.cleanup();

         // Remove banner entities
         FlagBannerUtils.cleanupAllBanners(flagStates);

         flagStates.clear();
         flagBaseLocations.clear();
         flagCaptures.clear();
         playerCircleTimestamps.clear();
         playerNearestFlagTeam.clear();
         playerHeartTimestamps.clear();
     }

    @Override
    public String getRoundStartMessage() {
        return "<gold>Capture the Flag Round!</gold>";
    }

    @Override
    public String getBuyPhaseMessage() {
        return "<yellow>Capture the enemy's flag! First team to " + WIN_CONDITION + " captures wins!</yellow>";
    }

    // ========= PUBLIC METHODS (non-overridden) =========

    /**
     * Handle shopping phase start - remove banners from players
     */
    public void onShoppingPhaseStart() {
        Messages.debug("[CTF] Shopping phase starting - removing banners from players");
        removeBannersFromPlayers();
    }

     /**
      * Handle flag pickup
      */
     public void flagPickup(Player player, int enemyTeamNumber) {
         UUID playerUuid = player.getUniqueId();
         long now = System.currentTimeMillis();

         FlagState flag = flagStates.get(enemyTeamNumber);
         if (flag == null) return;
         boolean pickedUpFromBase = isFlagAtBase(enemyTeamNumber, flag);
         cancelFlagReturnTask(enemyTeamNumber);

         FlagState updatedFlag = flag.withHolder(playerUuid, pickedUpFromBase ? now : 0L);
         if (enemyTeamNumber == 1) {
             flagStates.put(1, updatedFlag);
             Messages.debug("[CTF] " + player.getName() + " picked up Team Red's flag");
             Messages.broadcast(session.getPlayers(), "gamemode-ctf.flag-stolen-red",
                     "player_name", player.getName());

             // Apply silenced ability if not in final stand
         } else {
             flagStates.put(2, updatedFlag);
             Messages.debug("[CTF] " + player.getName() + " picked up Team Blue's flag");
             Messages.broadcast(session.getPlayers(), "gamemode-ctf.flag-stolen-blue",
                     "player_name", player.getName());

             // Apply silenced ability if not in final stand
         }
         if (!finalStandManager.isActive()) {
             Messages.send(player, "gamemode-ctf.silenced-activated");
             Messages.debug("[CTF] Applied silenced ability to flag carrier: " + player.getName());

             // Grey out unavailable items
             SchedulerUtils.runTaskLater(() -> updateSilencedItemDisplay(player), 1);
         }
         moveBannerToPlayer(updatedFlag.bannerDisplay(), player);

         if (pickedUpFromBase) {
             TimerDisplayUtils.startBonusTimer(player, updatedFlag);
         } else {
             ActionBarQueue.get().stopDisplay(player);
             TimerDisplayUtils.stopBonusTimer(player);
         }

         SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
     }

    /**
     * Handle flag capture (returning to base)
     */
    public void flagCapture(Player player, int teamNumber) {
        flagCaptures.merge(teamNumber, 1, Integer::sum);
        if (suddenDeathManager.isInSuddenDeath()) {
            suddenDeathCycleCaptures.merge(teamNumber, 1, Integer::sum);
        }
        int captures = flagCaptures.get(teamNumber);
        int targetCaptures = WIN_CONDITION;

        Messages.debug("[CTF] Team " + teamNumber + " captured a flag! Total captures: " + captures + "/" + targetCaptures);
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.flag-captured-by-player",
                "player_name", player.getName(),
                "captures", String.valueOf(captures),
                "win_condition", String.valueOf(targetCaptures));

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // ISSUE 1: Check if bonus timer has expired before awarding bonus
        long now = System.currentTimeMillis();
        int enemyTeamNumber = (teamNumber == 1) ? 2 : 1;
        FlagState enemyFlag = flagStates.get(enemyTeamNumber);
        stopFlagActionBar(enemyFlag);
        ActionBarQueue.get().stopDisplay(player);

        boolean bonusEarned = (enemyFlag != null && enemyFlag.captureTime() > 0 &&
                (now - enemyFlag.captureTime()) <= CAPTURE_TIMER_MS);

        if (bonusEarned) {
            awardCaptureBonus(teamNumber);
        }

        else {
            // Award base points without bonus
            Messages.debug("[CTF] Flag captured after 45s - no bonus awarded to Team " + teamNumber);
        }

        CustomArmorManager.getInstance().onInvestorObjectivectf(player);
        Messages.debug("[INVESTOR] Capturer: " + player.getName());
        resetFlagsAfterCapture(teamNumber);

        // Check for 2-2 tie in sudden death to restart cycle
        if (suddenDeathManager.isInSuddenDeath()) {
            int redC = suddenDeathCycleCaptures.getOrDefault(1, 0);
            int blueC = suddenDeathCycleCaptures.getOrDefault(2, 0);
            if (redC >= 2 && blueC >= 2) {
                Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death-restart-tie");
                suddenDeathManager.restartCycle();
            }
        }
    }

    public UUID getFlagHolder(int teamNumber) {
        FlagState flag = flagStates.get(teamNumber);
        return flag != null ? flag.holder() : null;
    }

    public int getFlagCaptures(int teamNumber) {
        return flagCaptures.get(teamNumber);
    }

    public boolean isSilenced(UUID playerUuid) {
        // If final stand is active, silenced is not applied
        if (isFinalStandActive()) {
            return false;
        }

        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        return (redFlag != null && redFlag.isHeld() && redFlag.holder().equals(playerUuid)) ||
                (blueFlag != null && blueFlag.isHeld() && blueFlag.holder().equals(playerUuid));
    }

    /**
     * Update silenced state visually - grey out unavailable items in player's inventory
     */
    public void updateSilencedItemDisplay(Player player) {
        ItemUtils.updateSilencedItemDisplay(player, isSilenced(player.getUniqueId()));
    }

    // ========= PRIVATE HELPER METHODS =========

     /**
      * Initialize banners for both flags at their base locations
      * Banners are BlockDisplay entities that rotate around the flag plates
      */
     private void initializeBanners() {
         Location redFlagLoc = getRedFlagLocation();
         Location blueFlagLoc = getBlueFlagLocation();

         Map<Integer, FlagState> newFlags = FlagBannerUtils.initializeFlagBanners(redFlagLoc, blueFlagLoc);
         flagStates.putAll(newFlags);

         // Store base locations
         if (redFlagLoc != null) {
             flagBaseLocations.put(1, redFlagLoc.clone());
         }
         if (blueFlagLoc != null) {
             flagBaseLocations.put(2, blueFlagLoc.clone());
         }
     }

    /**
     * Get red flag location from template or fallback to team spawn
     */
    private Location getRedFlagLocation() {
        TemplateWorld template = session.getArenaTemplate();
        if (template != null && template.getRedFlagLoc() != null) {
            return LocationUtils.copyToWorld(template.getRedFlagLoc(), session.getGameWorld());
        }
        return null;
    }

    /**
     * Get blue flag location from template or fallback to team spawn
     */
    private Location getBlueFlagLocation() {
        TemplateWorld template = session.getArenaTemplate();
        if (template != null && template.getBlueFlagLoc() != null) {
            return LocationUtils.copyToWorld(template.getBlueFlagLoc(), session.getGameWorld());
        }
        return null;
    }

    /**
     * Handle flag holder death for a specific team
     */
    private void handleFlagHolderDeath(int teamNumber, UUID victimUuid, Player victim) {
        FlagState flag = flagStates.get(teamNumber);
        if (flag == null || !flag.isHeld() || !flag.holder().equals(victimUuid)) {
            return;
        }

        String teamName = teamNumber == 1 ? "Red" : "Blue";
        Messages.debug("[CTF] Team " + teamName + " flag holder eliminated: " + victim.getName());
        String colorTag = teamNumber == 1 ? "red" : "blue";
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.flag-holder-eliminated",
                "color", colorTag,
                "team_name", teamName);

        dropFlagAtLocation(teamNumber, victim.getLocation());
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);

        // Restore normal item display for the flag carrier
        updateSilencedItemDisplay(victim);

        // Clear stalemate state since one flag is now down
        stalemateMsgShown.clear();
    }

    /**
     * Remove flag if player was carrying it
     */
    private void removeFlagIfCarriedByPlayer(int teamNumber, UUID playerUuid, Player player) {
        FlagState flag = flagStates.get(teamNumber);
        if (flag == null || !flag.isHeld() || !flag.holder().equals(playerUuid)) {
            return;
        }

        String teamName = teamNumber == 1 ? "Red" : "Blue";
        Messages.debug("[CTF] Removing player " + player.getName() + " who was carrying " + teamName + " flag");

        if (flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
            if (flag.bannerDisplay().getVehicle() != null) {
                flag.bannerDisplay().getVehicle().removePassenger(flag.bannerDisplay());
            }
        }
        returnFlagToBase(teamNumber);

        // Restore normal item display for the flag carrier
        SchedulerUtils.runTaskLater(() -> updateSilencedItemDisplay(player), 1);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Start task to show glowing effect on flag carriers every 5 seconds
     */
    private void startCarrierGlowEffect() {
        carrierGlowTask = FlagEffectsUtils.startCarrierGlowEffectTask(() -> flagStates);
    }

    /**
     * Remove all banners from players and move them back to their plates
     */
    private void removeBannersFromPlayers() {
        FlagBannerUtils.removeAllBannersFromPlayers(flagStates, this::cancelTask);
    }

    @Override
    public boolean isFinalStandActive() {
        return finalStandManager.isActive();
    }

    @Override
    public void onFinalStandActivated() {
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.final-stand-activated");
        Messages.debug("[CTF] Final Stand activated - removing flag silenced effects");
        for (UUID uuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ItemUtils.restoreNormalItemDisplay(player);
            }
        }
    }

    @Override
    public void onSuddenDeathCycleEnded() {
        int team1Captures = suddenDeathCycleCaptures.getOrDefault(1, 0);
        int team2Captures = suddenDeathCycleCaptures.getOrDefault(2, 0);

        if (team1Captures != team2Captures) {
            suddenDeathWinningTeam = team1Captures > team2Captures ? 1 : 2;
            Messages.debug("[CTF] Sudden death cycle winner determined: Team " + suddenDeathWinningTeam + " with " + Math.max(team1Captures, team2Captures) + " captures");
        } else {
            Messages.debug("[CTF] Sudden death cycle tied " + team1Captures + "-" + team2Captures + "; restarting");
        }
    }

    @Override
    public void onSuddenDeathCycleRestart() {
        suddenDeathCycleCaptures.put(1, 0);
        suddenDeathCycleCaptures.put(2, 0);
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        
        // Reset scoreboard indicators
        for (UUID uuid : session.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                ScoreboardManager.getInstance().updatePlayerScoreboard(p);
                // Also send the timer start message to confirm cycle reset
                Messages.send(p, "gamemode-ctf.sudden-death-timer-start");
            }
        }
        
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death-tied-restart");
        Messages.debug("[CTF] Sudden death capture counters and scoreboard indicators reset for next cycle");
    }

    @Override
    public SuddenDeathManager getSuddenDeathManager() {
        return suddenDeathManager;
    }

    @Override
    public FinalStandManager getFinalStandManager() {
        return finalStandManager;
    }

    /**
     * Start flag pickup task - checks if players are standing in flag capture circles
     * Players must stand in the circle for 3 seconds to pick up the flag
     */
    private void startFlagPickupTask() {
        flagPickupTask = SchedulerUtils.runTaskTimer(this::checkFlagPickupProgress, 0, 5); // Check every 5 ticks
    }

    /**
     * Check if a player is within the capture circle radius of a flag
     */
    private boolean isPlayerNearFlag(Player player, FlagState flag) {
        if (flag == null || flag.flagLoc() == null) return false;
        return FlagPickupValidator.isPlayerNearFlag(player, flag.flagLoc());
    }

    /**
     * Check if a player is inside a circular scoring zone around a banner/plate location.
     */
    private boolean isPlayerInScoringZone(Player player, Location bannerLoc) {
        return FlagPickupValidator.isPlayerInScoringZone(player, bannerLoc);
    }

    /**
     * Check if a player carrying enemy flag reached their own scoring area.
     */
    private boolean tryHandleFlagCapture(Player player, int playerTeam, FlagState redFlag, FlagState blueFlag) {
        UUID playerUuid = player.getUniqueId();
        Location redBase = flagBaseLocations.get(1);
        Location blueBase = flagBaseLocations.get(2);
        if (redBase == null || blueBase == null) {
            return false;
        }

        // Cannot score if both teams have flags (mutual flag stalemate)
        boolean bothFlagsHeld = redFlag.isHeld() && blueFlag.isHeld();
        if (bothFlagsHeld) {
            // Show blocking message once per player per stalemate
            if (!stalemateMsgShown.contains(playerUuid)) {
                Messages.send(player, "gamemode-ctf.both-flags-held");
                stalemateMsgShown.add(playerUuid);
            }
            FlagBannerUtils.spawnBannerParticles(redFlag.flagLoc(), 0);
            FlagBannerUtils.spawnBannerParticles(blueFlag.flagLoc(), 0);
            return false;
        } else {
            // Clear the message flag if stalemate is broken
            stalemateMsgShown.remove(playerUuid);
        }

        // ISSUE 3: Cannot score if team's own flag is already held by someone (no double capture)
        if (playerTeam == 1 && redFlag.isHeld()) {
            return false; // Team 1's own flag is held, can't score right now
        }
        if (playerTeam == 2 && blueFlag.isHeld()) {
            return false; // Team 2's own flag is held, can't score right now
        }

        // Team 1 carries Team 2's (Blue) flag and scores at Team 1's (Red) plate
        if (playerTeam == 1 && isDroppedFlagWaitingForReturn(1)) {
            return false; // Team 1's own flag is dropped and returning, cannot score
        }
        if (playerTeam == 1 && blueFlag != null
                && blueFlag.isHeld()
                && playerUuid.equals(blueFlag.holder())
                && isPlayerInScoringZone(player, redBase)) {
            flagCapture(player, 1);
            return true;
        }

        // Team 2 carries Team 1's (Red) flag and scores at Team 2's (Blue) plate
        if (playerTeam == 2 && isDroppedFlagWaitingForReturn(2)) {
            return false; // Team 2's own flag is dropped and returning, cannot score
        }
        if (playerTeam == 2 && redFlag.isHeld() &&
                playerUuid.equals(redFlag.holder()) &&
                isPlayerInScoringZone(player, blueBase)) {
            flagCapture(player, 2);
            return true;
        }

        return false;
    }

     /**
      * Reset flag holders and move banners back to plates after capture
      */
     private void resetFlagsAfterCapture(int teamNumber) {
         int enemyTeam = teamNumber == 1 ? 2 : 1;
         returnFlagToBase(enemyTeam);
     }

     private void returnFlagToBase(int teamNumber) {
         cancelFlagReturnTask(teamNumber);
         FlagState flag = flagStates.get(teamNumber);
         stopFlagActionBar(flag);

         if (flag != null && flag.carryingTask() != null) {
             flag.carryingTask().cancel();
         }

         FlagBaseMechanicsUtils.returnFlagToBase(teamNumber, flagStates, flagBaseLocations);
         FlagState returnedFlag = flagStates.get(teamNumber);
         if (returnedFlag != null) {
             moveBannerBack(returnedFlag.bannerDisplay(), returnedFlag.getFlagLoc());
         }
     }

    private void cancelFlagReturnTask(int teamNumber) {
        BukkitTask task = flagReturnTasks.remove(teamNumber);
        if (task != null) {
            cancelTask(task);
        }
        cancelFlagReturnDisplayTask();
        flagReturnExpiry.remove(teamNumber);
    }

     private void cancelFlagReturnDisplayTask() {
         TimerDisplayUtils.stopFlagReturnTimer(session.getPlayers());
     }

    private void scheduleFlagReturnTimer(int teamNumber) {
        if (flagReturnTasks.containsKey(teamNumber)) {
            return;
        }
        long now = System.currentTimeMillis();
        long expiryMs = now + 5_000L;
        flagReturnExpiry.put(teamNumber, expiryMs);

        long remainingMs = Math.max(0L, expiryMs - now);
        long remainingTicks = Math.max(1L, (remainingMs + 49L) / 50L);

        BukkitTask returnTask = SchedulerUtils.runTaskLater(() -> {
            flagReturnTasks.remove(teamNumber);
            returnFlagToBase(teamNumber);
        }, remainingTicks);
        flagReturnTasks.put(teamNumber, returnTask);
        scheduleFlagReturnDisplayTimer(teamNumber);
        Messages.debug("[CTF] Scheduled return timer for Team " + teamNumber + " flag to " + (remainingMs / 1000 + (remainingMs % 1000 > 0 ? 1 : 0)) + "s");
    }

    private void pauseFlagReturnTimer(int teamNumber) {
        if (!flagReturnTasks.containsKey(teamNumber)) {
            return;
        }
        BukkitTask task = flagReturnTasks.remove(teamNumber);
        if (task != null) {
            cancelTask(task);
        }
        cancelFlagReturnDisplayTask();
        Messages.debug("[CTF] Paused return timer for Team " + teamNumber + " flag because a player entered the circle");
    }

     private void scheduleFlagReturnDisplayTimer(int teamNumber) {
         cancelFlagReturnDisplayTask();

         Long expiry = flagReturnExpiry.get(teamNumber);
         if (expiry != null) {
             TimerDisplayUtils.startFlagReturnTimer(teamNumber, expiry, session.getPlayers());
         }
     }


     private boolean isDroppedFlagWaitingForReturn(int teamNumber) {
         return FlagBaseMechanicsUtils.isDroppedFlagWaitingForReturn(teamNumber, flagStates, flagBaseLocations, flagReturnExpiry);
     }

     private boolean isFlagAtBase(int teamNumber, FlagState flag) {
         Location base = flagBaseLocations.get(teamNumber);
         Location flagLoc = flag != null ? flag.flagLoc() : null;
         if (base == null || flagLoc == null || base.getWorld() != flagLoc.getWorld()) {
             return false;
         }
         return flagLoc.distanceSquared(base) <= 0.25;
     }

      private void stopFlagActionBar(FlagState flag) {
          if (flag != null && flag.holder() != null) {
              Player holder = Bukkit.getPlayer(flag.holder());
              if (holder != null) {
                  ActionBarQueue.get().stopDisplay(holder);
                  TimerDisplayUtils.stopBonusTimer(holder);
              }
          }
      }

    /**
     * Check flag pickup progress for all online players
     */
    private void checkFlagPickupProgress() {
        for (UUID playerUuid : session.getPlayers()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                playerCircleTimestamps.remove(playerUuid);
                playerNearestFlagTeam.remove(playerUuid);
                continue;
            }

            FlagState redFlag = flagStates.get(1);
            FlagState blueFlag = flagStates.get(2);

            int playerTeam = session.getPlayerTeam(player).getTeamNumber();

            // Scoring check: if carrier reached own scoring plate, capture immediately.
            if (tryHandleFlagCapture(player, playerTeam, redFlag, blueFlag)) {
                playerCircleTimestamps.remove(playerUuid);
                playerNearestFlagTeam.remove(playerUuid);
                continue;
            }

            // Determine which flag team player is near (if any)
            Integer nearestTeam = null;
            boolean nearRedFlag = isPlayerNearFlag(player, redFlag);
            boolean nearBlueFlag = isPlayerNearFlag(player, blueFlag);

            // Check Red flag pickup
            if (nearRedFlag && !redFlag.isHeld()) {
                // Only enemy team can pick up
                if (playerTeam == 2) {
                    nearestTeam = 1;
                }
            }

            // Check Blue flag pickup
            if (nearBlueFlag && !blueFlag.isHeld()) {
                // Only enemy team can pick up
                if (playerTeam == 1) {
                    nearestTeam = 2;
                }
            }

            // Update player's standing time
            if (nearestTeam != null) {
                long now = System.currentTimeMillis();
                Long prevTime = playerCircleTimestamps.get(playerUuid);
                Integer prevTeam = playerNearestFlagTeam.get(playerUuid);

                if (prevTeam == null || !prevTeam.equals(nearestTeam)) {
                    // Player entered a different flag circle or first time - reset
                    playerCircleTimestamps.put(playerUuid, now);
                    playerNearestFlagTeam.put(playerUuid, nearestTeam);

                    // Show "capturing" message
                    String teamName = nearestTeam == 1 ? "Red" : "Blue";
                    String colorTag = nearestTeam == 1 ? "red" : "blue";
                    Messages.send(player, "gamemode-ctf.flag-capturing",
                            "team_name", teamName, "color", colorTag);
                    if (flagReturnTasks.containsKey(nearestTeam)) {
                        Long expiry = flagReturnExpiry.get(nearestTeam);
                        if (expiry != null) {
                            String flagColor = nearestTeam == 1 ? "<red>" : "<blue>";
                            // Use countdown timer for flag return display (priority 2)
                            long remainingMs = Math.max(0, expiry - now);
                            ActionBarQueue.get().startCountdownTimer(player, remainingMs, 2,
                                    secondsRemaining -> flagColor + "Flag returns in " + secondsRemaining + "s");
                        }
                        pauseFlagReturnTimer(nearestTeam);
                    }
                } else if (prevTime != null) {
                    long elapsedMs = now - prevTime;

                    if (elapsedMs >= FLAG_PICKUP_DURATION_MS) {
                        // 3 seconds elapsed - pickup the flag
                        flagPickup(player, nearestTeam);
                        playerCircleTimestamps.remove(playerUuid);
                        playerNearestFlagTeam.remove(playerUuid);
                    } else {
                        // Show countdown timer on action bar (3 2 1)
                        long remainingMs = FLAG_PICKUP_DURATION_MS - elapsedMs;

                        String flagColor = nearestTeam == 1 ? "<red>" : "<blue>";
                        // Use countdown timer for flag pickup display (priority 0 - high priority)
                        ActionBarQueue.get().startCountdownTimer(player, remainingMs, 0,
                                secondsRemaining -> flagColor + "📍 " + secondsRemaining + " second" + (secondsRemaining == 1 ? "" : "s"));
                    }
                }
            } else {
                // Player left the circle - reset if they were in one
                if (playerNearestFlagTeam.containsKey(playerUuid)) {
                    Integer previousTeam = playerNearestFlagTeam.get(playerUuid);
                    String teamName = previousTeam == 1 ? "Red" : "Blue";
                    Messages.send(player, "gamemode-ctf.flag-capture-cancelled",
                            "team_name", teamName);
                    if (isDroppedFlagWaitingForReturn(previousTeam)) {
                        scheduleFlagReturnTimer(previousTeam);
                    }
                }
                ActionBarQueue.get().stopDisplay(player);
                playerCircleTimestamps.remove(playerUuid);
                playerNearestFlagTeam.remove(playerUuid);
            }
        }
    }

    /**
     * Apply extra heart to a player in CTF (for final stand)
     */
     private void applyExtraHeartCTF(Player player) {
         suddenDeathManager.applyExtraHeart(player, 45 * 1000);
         TimerDisplayUtils.recordHeartBonus(player.getUniqueId(), playerHeartTimestamps);
         // Start heart timer display
         TimerDisplayUtils.startHeartTimer(player, playerHeartTimestamps);
     }

    /**
     * Enter sudden death
     */
    private void enterSuddenDeath() {
        suddenDeathManager.enterSuddenDeath();
        Messages.debug("[CTF] Entering sudden death mode - match score is 3-3");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death-timer-start");
        Messages.debug("[CTF] Entered sudden death - 3-minute sudden-death initial period started");
    }

     private void dropFlagAtLocation(int teamNumber, Location location) {
         FlagState flag = flagStates.get(teamNumber);
         if (flag == null || location == null) {
             return;
         }

         cancelFlagReturnTask(teamNumber);

         Location dropLocation = location.clone();
         if (dropLocation.getWorld() == null) {
             dropLocation.setWorld(session.getGameWorld());
         }

         if (flag.carryingTask() != null) {
             flag.carryingTask().cancel();
         }

         FlagBaseMechanicsUtils.dropFlagAtLocation(teamNumber, dropLocation, flagStates);
         FlagState droppedFlag = flagStates.get(teamNumber);

         moveBannerBack(flag.bannerDisplay(), droppedFlag.getFlagLoc());
         scheduleFlagReturnTimer(teamNumber);
     }

    @Override
    public boolean forceSuddenDeathForTesting() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return false;
        }

        enterSuddenDeath();
        return true;
    }

    @Override
    public void prepareSuddenDeathRound() {
        if (suddenDeathManager.isInSuddenDeath()) {
            return;
        }

        suddenDeathManager.enterSuddenDeath();
        
        Messages.debug("[CTF] Prepared sudden death round - match score is 3-3.");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death-timer-start");
    }


     /**
      * Get bonus time remaining for a player
      */
      public long getBonusTimeRemainingMs(UUID playerUuid) {
          int teamNum = session.getPlayerTeam(playerUuid).getTeamNumber();
          FlagState flagState = flagStates.get(teamNum);
          if (flagState == null) {
              return 0;
          }
          return TimerDisplayUtils.getBonusTimeRemaining(flagState);
      }



    /**
     * Award capture bonus after 45 seconds
     */
    private void awardCaptureBonus(int team) {
        long bonusPerPlayer = CAPTURE_BONUS / 4;
        var teamObj = team == 1 ? session.getTeamRed() : session.getTeamBlue();

        Messages.debug("[CTF] Awarding 45-second capture bonus to Team " + team + ": " + bonusPerPlayer + " per player");

        for (UUID uuid : teamObj.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (suddenDeathManager.isInSuddenDeath()) {
                    applyExtraHeartCTF(p);
                    Messages.send(p, "gamemode-ctf.capture-bonus-heart");
                } else {
                    Messages.send(p, "gamemode-ctf.flag-captured-bonus");
                    session.getCashClashPlayer(uuid).addCoins(bonusPerPlayer);
                }
            }
        }

        String teamName = team == 1 ? "Red" : "Blue";
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.capture-bonus-team-broadcast",
                "team_name", teamName);
    }

    /**
     * Start carrying task for flag - banner is attached to player's head and rotates with them
     * Uses a direct teleport approach for instant rotation with player
     */
    private void moveBannerToPlayer(BlockDisplay banner, Player player) {
        if (banner == null || banner.isDead() || !player.isOnline()) {
            return;
        }

        // Find which flag this banner belongs to
        Integer teamNumber = null;
        for (int team = 1; team <= 2; team++) {
            FlagState flag = flagStates.get(team);
            if (flag != null && flag.bannerDisplay() == banner) {
                teamNumber = team;
                break;
            }
        }

        if (teamNumber == null) return;

        FlagState flagState = flagStates.get(teamNumber);

        // Stop existing carrying task if any
        if (flagState.carryingTask() != null) {
            flagState.carryingTask().cancel();
        }

        // Make variables final for lambda capture
        final int finalTeamNumber = teamNumber;

        // Create carrying task and store it
        BukkitTask task = FlagBannerUtils.createCarryingTask(banner, player, () -> {
            FlagState current = flagStates.get(finalTeamNumber);
            if (current != null && current.carryingTask() != null) {
                flagStates.put(finalTeamNumber, current.withCarryingTask(null));
            }
        });

        // Store the task in flag state
        FlagState updatedFlag = flagState.withCarryingTask(task);
        flagStates.put(teamNumber, updatedFlag);

        Messages.debug("[CTF] Started carrying task for banner on " + player.getName());
    }

    /**
     * Stop carrying task and move banner back to plate
     */
    private void moveBannerBack(BlockDisplay banner, Location location) {
        if (banner == null || banner.isDead() || location == null) {
            return;
        }

        for (int team = 1; team <= 2; team++) {
            FlagState flag = flagStates.get(team);
            if (flag != null && flag.bannerDisplay() == banner) {
                FlagBannerUtils.stopCarryingBanner(banner, location, flag.carryingTask());
                flagStates.put(team, flag.withCarryingTask(null));
                break;
            }
        }
    }

     /**
      * Start task to rotate banners
      */
     private void startBannerRotationTask() {
         bannerRotationTask = SchedulerUtils.runTaskTimer(this::updateBannerRotations, 0, 1);
     }

      /**
       * Update banner rotations and particle effects for both flags
       */
      private void updateBannerRotations() {
          for (int team = 1; team <= 2; team++) {
              FlagState flag = flagStates.get(team);

              if (flag != null) {
                  if (flag.isHeld() && flag.holder() != null) {
                      Location basePlate = flagBaseLocations.get(team);
                      if (basePlate != null) {
                          FlagBannerUtils.spawnBannerParticles(basePlate, 0);
                      }
                      continue;
                  }

                  // Use the flag's current location (either base or dropped location)
                  Location centerPlate = flag.flagLoc() != null ? flag.flagLoc() : flagBaseLocations.get(team);
                  if (centerPlate == null) {
                      continue;
                  }

                  // Update banner rotation while idle/dropped.
                  FlagState rotatedFlag = FlagBannerUtils.rotateBanner(flag, centerPlate);
                  flagStates.put(team, rotatedFlag);

                  // At base/dropped, keep team identity with team-colored particles.
                  FlagBannerUtils.spawnBannerParticles(centerPlate, team);
              }
          }
      }
}
