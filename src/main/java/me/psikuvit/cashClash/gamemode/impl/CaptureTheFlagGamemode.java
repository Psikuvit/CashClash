package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.util.LocationUtils;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Capture the Flag Gamemode
 * Goal: Steal the enemy flag and return it to your base (first to 2 captures wins)
 */
public class CaptureTheFlagGamemode extends Gamemode {

    private static final int WIN_CONDITION = 2;
    private static final int SUDDEN_DEATH_CONDITION = 4;
    private static final int GLOW_INTERVAL_TICKS = 100; // 5 seconds
    private static final long CAPTURE_BONUS = 15000;
    private static final long CAPTURE_TIMER_MS = 45 * 1000; // 45 seconds for bonus
    private static final double BANNER_ORBIT_RADIUS = 2; // Distance from plate center
    private static final double BANNER_ANGULAR_SPEED = 0.03; // Radians per tick - smooth rotation
    private static final long FLAG_PICKUP_DURATION_MS = 3000; // 3 seconds to pick up flag
    private static final double CIRCLE_RADIUS = 1.5; // Radius of flag capture circle

    private final Map<Integer, Integer> flagCaptures;
    private final Map<Integer, FlagState> flagStates; // Red flag (team 1) and Blue flag (team 2)
    private final Map<UUID, Long> playerCircleTimestamps; // Track when each player entered the circle
    private final Map<UUID, Integer> playerNearestFlagTeam; // Track which flag team player is near

    private final SuddenDeathManager suddenDeathManager;
    private BukkitTask carrierGlowTask;
    private BukkitTask captureTimerTask;
    private BukkitTask bannerRotationTask;
    private BukkitTask flagPickupTask;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);

        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.flagStates = new HashMap<>(2);
        this.playerCircleTimestamps = new HashMap<>();
        this.playerNearestFlagTeam = new HashMap<>();
        this.suddenDeathManager = new SuddenDeathManager(session);
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
        this.bannerRotationTask = null;
        this.flagPickupTask = null;

        // Pre-populate flag states and capture map
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
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

        // Initialize banners at flag plates
        initializeBanners();

        // Reset flag state each round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());

        // Clear circle tracking data
        playerCircleTimestamps.clear();
        playerNearestFlagTeam.clear();

        // Start carrier glow effect task
        startCarrierGlowEffect();

        // Start capture timer task
        startCaptureTimerTask();

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
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        // Reset flag states but preserve the plate locations and banners for next round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());

        // Clear circle tracking data
        playerCircleTimestamps.clear();
        playerNearestFlagTeam.clear();

        // Reset task references (tasks will be recreated in next combat phase)
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
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
        // No special spawn logic
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
        int targetCaptures = suddenDeathManager.isInSuddenDeath() ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;
        int captures1 = flagCaptures.get(1);
        int captures2 = flagCaptures.get(2);

        if (captures1 >= targetCaptures || captures2 >= targetCaptures) {
            Messages.debug("[CTF] Game winner found - Team " + (captures1 >= targetCaptures ? 1 : 2) + " with " + Math.max(captures1, captures2) + " captures");
            return true;
        }

        // Check for sudden death condition
        if (!suddenDeathManager.isInSuddenDeath() && captures1 == 3 && captures2 == 3) {
            enterSuddenDeath();
        }

        return false;
    }

    @Override
    public int getWinningTeam() {
        int targetCaptures = suddenDeathManager.isInSuddenDeath() ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;

        if (flagCaptures.get(1) >= targetCaptures) {
            return 1;
        } else if (flagCaptures.get(2) >= targetCaptures) {
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
        cancelTask(captureTimerTask);
        cancelTask(bannerRotationTask);
        cancelTask(flagPickupTask);

        // Cancel carrying tasks for all flags
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.carryingTask() != null) {
                cancelTask(flag.carryingTask());
            }
        }

        // Cleanup sudden death manager
        suddenDeathManager.cleanup();

        // Remove banner entities
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                flag.bannerDisplay().remove();
            }
        }

        flagStates.clear();
        flagCaptures.clear();
        playerCircleTimestamps.clear();
        playerNearestFlagTeam.clear();
    }

    @Override
    public String getRoundStartMessage() {
        return "<gold>Capture the Flag Round!</gold>";
    }

    @Override
    public String getBuyPhaseMessage() {
        int targetCaptures = suddenDeathManager.isInSuddenDeath() ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;
        return "<yellow>Capture the enemy's flag! First team to " + targetCaptures + " captures wins!</yellow>";
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

        FlagState updatedFlag = flag.withHolder(playerUuid, now);
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
        if (!isFinalStandActive()) {
            Messages.send(player, "gamemode-ctf.silenced-activated");
            Messages.debug("[CTF] Applied silenced ability to flag carrier: " + player.getName());
        }
        moveBannerToPlayer(updatedFlag.bannerDisplay(), player);

        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
    }

    /**
     * Handle flag capture (returning to base)
     */
    public void flagCapture(Player player, int teamNumber) {
        flagCaptures.merge(teamNumber, 1, Integer::sum);
        int captures = flagCaptures.get(teamNumber);
        int targetCaptures = suddenDeathManager.isInSuddenDeath() ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;

        Messages.debug("[CTF] Team " + teamNumber + " captured a flag! Total captures: " + captures + "/" + targetCaptures);
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.flag-captured-by-player",
                "player_name", player.getName(),
                "captures", String.valueOf(captures),
                "win_condition", String.valueOf(targetCaptures));

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        awardCaptureBonus(teamNumber);
        resetFlagsAfterCapture(teamNumber);
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

    // ========= PRIVATE HELPER METHODS =========

    /**
     * Initialize banners for both flags at their base locations
     * Banners are BlockDisplay entities that rotate around the flag plates
     */
    private void initializeBanners() {
        // Team 1 (Red) flag
        Location redFlagLoc = getRedFlagLocation();
        if (redFlagLoc != null) {
            BlockDisplay redBanner = spawnFlagBanner(redFlagLoc, Color.RED);
            if (redBanner != null) {
                flagStates.put(1, new FlagState(null, 0, redFlagLoc, redBanner, 0.0, null, 0.0));
                Messages.debug("[CTF] Spawned Red flag banner at " + redFlagLoc);
            }
        }

        // Team 2 (Blue) flag
        Location blueFlagLoc = getBlueFlagLocation();
        if (blueFlagLoc != null) {
            BlockDisplay blueBanner = spawnFlagBanner(blueFlagLoc, Color.BLUE);
            if (blueBanner != null) {
                flagStates.put(2, new FlagState(null, 0, blueFlagLoc, blueBanner, 0.0, null, 0.0));
                Messages.debug("[CTF] Spawned Blue flag banner at " + blueFlagLoc);
            }
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
     * Spawn a flag banner (BlockDisplay) at a location
     */
    private BlockDisplay spawnFlagBanner(Location location, Color color) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Create banner 2 blocks above the flag plate
        Location bannerLoc = location.clone().add(0, 2, 0);

        // Spawn BlockDisplay entity for the flag
        return location.getWorld().spawn(bannerLoc, BlockDisplay.class, banner ->{

            // Use a material that matches the team color
            Material bannerMaterial = color == Color.RED ? Material.RED_BANNER : Material.BLUE_BANNER;
            banner.setBlock(bannerMaterial.createBlockData());

        });
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

        flagStates.put(teamNumber, flag.withoutHolder());
        moveBannerBack(flag.bannerDisplay(), flag.getFlagLoc());
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
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
            moveBannerBack(flag.bannerDisplay(), flag.getFlagLoc());
        }
        flagStates.put(teamNumber, flag.withoutHolder());
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
        carrierGlowTask = SchedulerUtils.runTaskTimer(this::applyGlowToCarriers, 0, GLOW_INTERVAL_TICKS);
    }

    private void applyGlowToCarriers() {
        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        applyGlowIfCarrying(redFlag);
        applyGlowIfCarrying(blueFlag);
    }

    private void applyGlowIfCarrying(FlagState flag) {
        if (flag != null && flag.isHeld()) {
            Player carrier = Bukkit.getPlayer(flag.holder());
            if (carrier != null && carrier.isOnline()) {
                carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10, 0, false, false));
            }
        }
    }

    /**
     * Start task to track capture timers (45 seconds for bonus)
     */
    private void startCaptureTimerTask() {
        captureTimerTask = SchedulerUtils.runTaskTimer(this::checkCaptureTimers, 0, 20);
    }

    private void checkCaptureTimers() {
        long now = System.currentTimeMillis();

        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        if (redFlag != null && redFlag.captureTime() > 0 && (now - redFlag.captureTime()) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(1);
            flagStates.put(1, redFlag.withHolder(null, 0));
        }

        if (blueFlag != null && blueFlag.captureTime() > 0 && (now - blueFlag.captureTime()) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(2);
            flagStates.put(2, blueFlag.withHolder(null, 0));
        }
    }

    /**
     * Remove all banners from players and move them back to their plates
     */
    private void removeBannersFromPlayers() {
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                // Stop carrying task if active
                if (flag.carryingTask() != null) {
                    flag.carryingTask().cancel();
                }

                if (!flag.isHeld()) {
                    continue; // If not held, banner should already be at plate, no need to move
                }

                // Move banner back to its plate
                if (flag.getFlagLoc() != null) {
                    moveBannerBack(flag.bannerDisplay(), flag.getFlagLoc());
                }
            }
        }
    }

    /**
     * Check if final stand is active
     */
    private boolean isFinalStandActive() {
        return suddenDeathManager.isFinalStandActive();
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
        return LocationUtils.isPlayerNearLocation(player.getLocation(), flag.flagLoc(), CIRCLE_RADIUS);
    }

    /**
     * Check if a player is standing on a specific block by checking distance to the block location
     */
    private boolean isPlayerOnPlate(Player player, Location blockLoc) {
        return LocationUtils.isPlayerOnBlock(player.getLocation(), blockLoc, 0.7);
    }

    /**
     * Reset flag holders and move banners back to plates after capture
     */
    private void resetFlagsAfterCapture(int teamNumber) {
        // Reset the enemy flag that was captured
        FlagState enemyFlag = flagStates.get(teamNumber == 1 ? 2 : 1);
        if (enemyFlag != null) {
            flagStates.put(teamNumber == 1 ? 2 : 1, enemyFlag.withoutHolder());
            moveBannerBack(enemyFlag.bannerDisplay(), enemyFlag.getFlagLoc());
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

            // Determine which flag team player is near (if any)
            Integer nearestTeam = null;
            boolean nearRedFlag = isPlayerNearFlag(player, redFlag);
            boolean nearBlueFlag = isPlayerNearFlag(player, blueFlag);

            // Check Red flag pickup
            if (nearRedFlag && !redFlag.isHeld()) {
                int playerTeam = session.getPlayerTeam(player).getTeamNumber();
                // Only enemy team can pick up
                if (playerTeam == 2 && (blueFlag == null || !blueFlag.isHeld())) {
                    nearestTeam = 1;
                }
            }

            // Check Blue flag pickup
            if (nearBlueFlag && !blueFlag.isHeld()) {
                int playerTeam = session.getPlayerTeam(player).getTeamNumber();
                // Only enemy team can pick up
                if (playerTeam == 1 && (redFlag == null || !redFlag.isHeld())) {
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
                } else if (prevTime != null) {
                    long elapsedMs = now - prevTime;
                    if (elapsedMs >= FLAG_PICKUP_DURATION_MS) {
                        // 3 seconds elapsed - pickup the flag
                        flagPickup(player, nearestTeam);
                        playerCircleTimestamps.remove(playerUuid);
                        playerNearestFlagTeam.remove(playerUuid);
                    }
                }
            } else {
                // Player left the circle - reset if they were in one
                if (playerNearestFlagTeam.containsKey(playerUuid)) {
                    Integer previousTeam = playerNearestFlagTeam.get(playerUuid);
                    String teamName = previousTeam == 1 ? "Red" : "Blue";
                    Messages.send(player, "gamemode-ctf.flag-capture-cancelled",
                            "team_name", teamName);
                }
                playerCircleTimestamps.remove(playerUuid);
                playerNearestFlagTeam.remove(playerUuid);
            }
        }
    }

    /**
     * Apply extra heart to a player in CTF (for final stand)
     */
    private void applyExtraHeartCTF(Player player) {
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(22);
            player.setHealth(22);
        }

        // Apply red glow effect for 45 seconds
        long heartDurationMs = 45 * 1000;
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (heartDurationMs / 50), 0, false, false));
    }

    /**
     * Enter sudden death
     */
    private void enterSuddenDeath() {
        suddenDeathManager.enterSuddenDeath();
        Messages.debug("[CTF] Entering sudden death mode - both teams at 3-3 captures");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death");

        // Start final stand timer (5 minutes)
        startFinalStandTimer();
    }

    /**
     * Start final stand timer (5 minutes in sudden death)
     */
    private void startFinalStandTimer() {
        // This is now handled internally by SuddenDeathManager
        // Nothing to do here anymore
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
                // In sudden death with final stand active, award extra hearts instead
                if (suddenDeathManager.isInSuddenDeath() && suddenDeathManager.isFinalStandActive()) {
                    applyExtraHeartCTF(p);
                    Messages.send(p, "gamemode-ctf.capture-bonus-final-stand");
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
     * Start carrying task for flag - banner follows behind player's head
     * Uses orbit-like logic to position banner continuously
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
        final BlockDisplay finalBanner = banner;
        final Player finalPlayer = player;

        // Start new carrying task - runs every tick
        BukkitTask task = SchedulerUtils.runTaskTimer(() -> {
            if (!finalPlayer.isOnline() || finalBanner.isDead()) {
                // Stop task if player offline or banner dead
                FlagState current = flagStates.get(finalTeamNumber);
                if (current != null && current.carryingTask() != null) {
                    current.carryingTask().cancel();
                    flagStates.put(finalTeamNumber, current.withCarryingTask(null));
                }
                return;
            }

            FlagState current = flagStates.get(finalTeamNumber);
            if (current == null) return;

            // Position banner on player's head facing player's direction
            Location playerLoc = finalPlayer.getLocation();

            float yaw = playerLoc.getYaw();
            double x = playerLoc.getX();
            double y = playerLoc.getY() + 2.0; // On top of player's head
            double z = playerLoc.getZ();

            Location bannerLoc = new Location(playerLoc.getWorld(), x, y, z);

            LocationUtils.applyOrbitTransformation(finalBanner, bannerLoc, yaw);

            // Update yaw in flag state
            flagStates.put(finalTeamNumber, current.withCarryingAngle(yaw));
        }, 0, 1); // Run every tick

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

        // Find which flag this banner belongs to and stop its carrying task
        for (int team = 1; team <= 2; team++) {
            FlagState flag = flagStates.get(team);
            if (flag != null && flag.bannerDisplay() == banner && flag.carryingTask() != null) {
                flag.carryingTask().cancel();
                flagStates.put(team, flag.withCarryingTask(null));
                break;
            }
        }

        // Teleport banner back to location
        banner.teleport(location);
        Messages.debug("[CTF] Moved banner back to plate at " + location);
    }

    /**
     * Start task to rotate banners
     */
    private void startBannerRotationTask() {
        bannerRotationTask = SchedulerUtils.runTaskTimer(this::rotateBanners, 0, 1);
    }

    /**
     * Rotate banners continuously and spawn team-colored particles
     */
    private void rotateBanners() {
        rotateBannerForTeam(1);
        rotateBannerForTeam(2);
    }

    /**
     * Rotate banner for a specific team
     */
    private void rotateBannerForTeam(int teamNumber) {
        rotateBanner(flagStates.get(teamNumber), teamNumber);
    }

    /**
     * Rotate a single banner in a perfect circle around the plate
     * Banner is only rotated when not being carried by a player
     * The banner rotates in a circle and faces outward (away from center) like an arrow
     */
    private void rotateBanner(FlagState flag, int teamNumber) {
        if (!isBannerRotatable(flag)) return;

        Location centerPlate = flag.getFlagLoc();
        if (centerPlate == null) {
            Messages.debug("[CTF] Cannot rotate banner for Team " + teamNumber + " - no flag found");
            return;
        }
        double centerX = centerPlate.getX();
        double centerY = centerPlate.getY();
        double centerZ = centerPlate.getZ();

        // Compute new position and rotation
        double newTheta = LocationUtils.calculateNextOrbitAngle(flag.bannerAngle(), BANNER_ANGULAR_SPEED);
        updateBannerTransform(flag, centerX, centerY, centerZ, newTheta);

        // Update angle in flag state
        flagStates.put(teamNumber, flag.withBannerAngle(newTheta));

        // Spawn team-colored particles
        spawnBannerParticles(flag.flagLoc(), teamNumber);
    }

    /**
     * Check if banner is rotatable
     */
    private boolean isBannerRotatable(FlagState flag) {
        return flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead() &&
               flag.flagLoc() != null && !flag.isHeld();
    }

    /**
     * Update banner transform (position and rotation)
     * Uses same logic as BlockDisplayCommand with AxisAngle4f
     * Banner rotates on Y-axis using angle theta
     */
    private void updateBannerTransform(FlagState flag, double centerX, double centerY, double centerZ, double theta) {
        // Compute position on circumference
        double x = centerX + Math.cos(theta) * BANNER_ORBIT_RADIUS;
        double z = centerZ + Math.sin(theta) * BANNER_ORBIT_RADIUS;

        // Create rotation using Y-axis formula (same as BlockDisplayCommand)
        float yaw = (float) (-theta + Math.PI / 2);
        Location newLoc = new Location(flag.flagLoc().getWorld(), x, centerY, z);

        LocationUtils.applyOrbitTransformation(flag.bannerDisplay(), newLoc, yaw);
    }

    /**
     * Spawn team-colored particles around the banner orbit
     */
    private void spawnBannerParticles(Location centerPlate, int teamNumber) {
        Color teamColor = teamNumber == 1 ? Color.RED : Color.BLUE;
        for (int i = 0; i < 8; i++) {
            double particleAngle = Math.PI * 2 * i / 8;
            double particleX = BANNER_ORBIT_RADIUS * Math.cos(particleAngle);
            double particleZ = BANNER_ORBIT_RADIUS * Math.sin(particleAngle);
            ParticleUtils.spawnDust(centerPlate.clone().add(particleX, 0.1, particleZ), teamColor, 1, 1, 0);
        }
    }
}
