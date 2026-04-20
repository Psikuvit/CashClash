package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.config.MessagesConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.gamemode.SuddenDeathManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;

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

    private final Map<Integer, Integer> flagCaptures;
    private final Map<Integer, FlagState> flagStates; // Red flag (team 1) and Blue flag (team 2)

    private final SuddenDeathManager suddenDeathManager;
    private BukkitTask carrierGlowTask;
    private BukkitTask captureTimerTask;
    private BukkitTask bannerRotationTask;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);
        
        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.flagStates = new HashMap<>(2);
        this.inSuddenDeath = false;
        this.suddenDeathManager = new SuddenDeathManager(session);
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
        this.bannerRotationTask = null;

        // Pre-populate flag states and capture map
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        flagStates.put(1, FlagState.create());
        flagStates.put(2, FlagState.create());
    }

    @Override
    public void onGameStart() {
        Messages.debug("[CTF] Gamemode started");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.game-started");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.objective");
        Messages.broadcast(session.getPlayers(), "gamemode-ctf.bonus-info");

        // ...existing code...
        placePressurePlates();
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[CTF] Combat phase started");

        // Reset flag state each round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());

        // Start carrier glow effect task
        startCarrierGlowEffect();

        // Start capture timer task
        startCaptureTimerTask();

        // Start banner rotation task
        startBannerRotationTask();
    }

    @Override
    public void onRoundEnd() {
        // Remove all banners from players and reset positions
        removeBannersFromPlayers();

        this.inSuddenDeath = false;
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
        this.bannerRotationTask = null;

        // Reset state for next round
        suddenDeathManager.resetForNewRound();
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
        // Reset flag states but preserve the plate locations and banners for next round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();

        // Check if victim was carrying a flag
        handleFlagHolderDeath(1, victimUuid, victim);
        handleFlagHolderDeath(2, victimUuid, victim);
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
        Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ctf.flag-holder-eliminated",
                "color", colorTag,
                "team_name", teamName));

        flagStates.put(teamNumber, flag.withoutHolder());
        moveBannerBack(flag.bannerDisplay(), flag.getFlagLoc());
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
    }

    @Override
    public void onPlayerSpawn(Player player) {
        // No special spawn logic
    }

    /**
     * Handle player removal - remove any banners they were carrying
     */
    @Override
    public void onPlayerRemove(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if this player was carrying any flags and remove their banners
        removeFlagIfCarriedByPlayer(1, playerUuid, player);
        removeFlagIfCarriedByPlayer(2, playerUuid, player);
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
        suddenDeathManager.cleanup();

        // Remove banner entities
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                flag.bannerDisplay().remove();
            }
        }

        flagStates.clear();
        flagCaptures.clear();
    }

    /**
     * Remove all banners from players and move them back to their plates
     */
    private void removeBannersFromPlayers() {
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                // Remove from any passengers (i.e., from player if being carried)
                if (!flag.isHeld()) {
                    continue; // If not held, banner should already be at plate, no need to move
                }
                if (flag.bannerDisplay().getVehicle() != null) {
                    flag.bannerDisplay().getVehicle().removePassenger(flag.bannerDisplay());
                }

                // Move banner back to its plate
                if (flag.getFlagLoc() != null) {
                    moveBannerBack(flag.bannerDisplay(), flag.getFlagLoc());
                }
            }
        }
    }

    /**
     * Handle shopping phase start - remove banners from players
     */
    public void onShoppingPhaseStart() {
        Messages.debug("[CTF] Shopping phase starting - removing banners from players");
        removeBannersFromPlayers();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
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
     * Check if player is pressing a plate and handle flag pickup/scoring
     * Check if final stand is active
     */
    private boolean isFinalStandActive() {
        return suddenDeathManager.isFinalStandActive();
    }
     * This should be called from PlayerInteractEvent when a player steps on a pressure plate
     * Pickup and capture happen instantly when conditions are met.
     * <p>
     * Logic:
     * 1. If player presses ENEMY plate without flag -> pickup flag instantly
     * 2. If player presses OWN plate while carrying ENEMY flag -> score instantly
     */
    public void checkPlateCapture(Player player) {
        UUID playerUuid = player.getUniqueId();

        FlagState blueFlag = flagStates.get(2);
        FlagState redFlag = flagStates.get(1);

        // Determine which team the player is on
        int playerTeam = session.getPlayerTeam(player).getTeamNumber();

        // Check if player is on Team 1 (Red) plate
        if (redFlag != null && redFlag.capturePlate() != null && isPlayerOnPlate(player, redFlag.capturePlate())) {
            // Carrying Blue flag and on Red plate = SCORE INSTANTLY
            if (blueFlag != null && blueFlag.isHeld() && blueFlag.holder().equals(playerUuid)) {
                Messages.debug("[CTF] " + player.getName() + " scored with Blue flag!");
                flagCapture(player, 1);
                return;
            }
            // Red flag available and not held = PICKUP INSTANTLY (only if player is on Team 2)
            if (playerTeam == 2 && !redFlag.isHeld() && (blueFlag == null || !blueFlag.isHeld())) {
                Messages.debug("[CTF] " + player.getName() + " picked up Red flag instantly!");
                flagPickup(player, 1);
            }
        }
        // Check if player is on Team 2 (Blue) plate
        else if (blueFlag != null && blueFlag.capturePlate() != null && isPlayerOnPlate(player, blueFlag.capturePlate())) {
            // Carrying Red flag and on Blue plate = SCORE INSTANTLY
            if (redFlag != null && redFlag.isHeld() && redFlag.holder().equals(playerUuid)) {
                Messages.debug("[CTF] " + player.getName() + " scored with Red flag!");
                flagCapture(player, 2);
                return;
            }
            // Blue flag available and not held = PICKUP INSTANTLY (only if player is on Team 1)
            if (playerTeam == 1 && !blueFlag.isHeld() && (redFlag == null || !redFlag.isHeld())) {
                Messages.debug("[CTF] " + player.getName() + " picked up Blue flag instantly!");
                flagPickup(player, 2);
            }
        }
    }

    /**
     * Check if a player is standing on a specific block by checking distance to the block location
     */
    private boolean isPlayerOnPlate(Player player, Location blockLoc) {
        if (blockLoc == null || player.getWorld() != blockLoc.getWorld()) return false;

        // Check if player is within 1 block horizontally of the plate center
        return player.getLocation().distance(blockLoc.clone().add(0.5, 0, 0.5)) <= 0.7;
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
            Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ctf.flag-stolen-red",
                    "player_name", player.getName()));

            // Move banner to player head
        } else {
            flagStates.put(2, updatedFlag);
            Messages.debug("[CTF] " + player.getName() + " picked up Team Blue's flag");
            Messages.broadcast(session.getPlayers(), MessagesConfig.getInstance().getMessage("gamemode-ctf.flag-stolen-blue",
                    "player_name", player.getName()));

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
        Messages.broadcast(session.getPlayers(),
                "<gold><bold>" + player.getName() + " has captured the flag! " +
                captures + "/" + targetCaptures + "</bold></gold>");

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        awardCaptureBonus(teamNumber);
        resetFlagsAfterCapture(teamNumber);
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
                    Messages.send(p, "<gold>+1 Extra Heart! (Capture bonus in Final Stand)</gold>");
                } else {
                    Messages.send(p, "<green>+3,750 coins! (45-second capture bonus)</green>");
                    session.getCashClashPlayer(uuid).addCoins(bonusPerPlayer);
                }
            }
        }

        String teamName = team == 1 ? "Red" : "Blue";
        Messages.broadcast(session.getPlayers(),
                "<yellow>Team " + teamName + " earned a 45-second capture bonus!</yellow>");
    }

    /**
     * Enter sudden death
     */
    private void enterSuddenDeath() {
        suddenDeathManager.enterSuddenDeath();
        Messages.debug("[CTF] Entering sudden death mode - both teams at 3-3 captures");
        Messages.broadcast(session.getPlayers(),
                "<red><bold>SUDDEN DEATH! Both teams must now capture 4 flags to win!</bold></red>");

        // Start final stand timer (5 minutes)
        startFinalStandTimer();
    }

    /**
     * Create a BlockDisplay banner entity at the given location
     * Applies initial transformation to center the block on the display origin
     */
    private BlockDisplay createBannerDisplay(Location location, Material bannerType) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        // Spawn banner display above the pressure plate
        Location displayLoc = location.clone().add(0, 2, 0);
        BlockDisplay banner = world.spawn(displayLoc, BlockDisplay.class);
        banner.setBlock(Bukkit.createBlockData(bannerType));
        Messages.debug("[CTF] Created banner display at " + displayLoc);
        return banner;
    }

    /**
     * Move banner to float above player's head and add as passenger
     */
    private void moveBannerToPlayer(BlockDisplay banner, Player player) {
        if (banner == null || banner.isDead() || !player.isOnline()) {
            return;
        }
        // Add banner as passenger to the player
        player.addPassenger(banner);
        Messages.debug("[CTF] Moved banner to " + player.getName() + "'s head and added as passenger");
    }

    /**
     * Move banner to float above a location (plate) and remove from passengers
     */
    private void moveBannerBack(BlockDisplay banner, Location location) {
        if (banner == null || banner.isDead() || location == null) {
            return;
        }

        // Remove from any passengers (i.e., from player if being carried)
        if (banner.getVehicle() != null) {
            banner.getVehicle().removePassenger(banner);
        }

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
        double newTheta = calculateNextAngle(flag.bannerAngle());
        updateBannerTransform(flag, centerX, centerY, centerZ, newTheta);

        // Update angle in flag state
        flagStates.put(teamNumber, flag.withBannerAngle(newTheta));

        // Spawn team-colored particles
        spawnBannerParticles(flag.capturePlate(), teamNumber);
    }

    /**
     * Check if banner is rotatable
     */
    private boolean isBannerRotatable(FlagState flag) {
        return flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead() &&
               flag.capturePlate() != null && !flag.isHeld();
    }

    /**
     * Calculate next banner angle
     */
    private double calculateNextAngle(double currentTheta) {
        double newTheta = currentTheta + BANNER_ANGULAR_SPEED;
        if (newTheta > Math.PI * 2) {
            newTheta -= Math.PI * 2;
        }
        return newTheta;
    }

    /**
     * Update banner transform (position and rotation)
     * Uses only the angle theta to calculate both position and rotation
     * Banner faces outward and rotates to face the direction it's moving
     */
    private void updateBannerTransform(FlagState flag, double centerX, double centerY, double centerZ, double theta) {
        // Compute position on circumference
        double x = centerX + Math.cos(theta) * BANNER_ORBIT_RADIUS;
        double z = centerZ + Math.sin(theta) * BANNER_ORBIT_RADIUS;

        Matrix4f mat = new Matrix4f();
        mat.rotateY((float) theta);

        Location newLoc = new Location(flag.capturePlate().getWorld(), x, centerY, z);
        flag.bannerDisplay().setTransformationMatrix(mat);
        flag.bannerDisplay().teleport(newLoc);
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
}
