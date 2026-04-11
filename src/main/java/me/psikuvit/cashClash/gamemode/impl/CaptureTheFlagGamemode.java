package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.ParticleUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Capture the Flag Gamemode
 * Goal: Steal the enemy flag and return it to your base (first to 2 captures wins)
 */
public class CaptureTheFlagGamemode extends Gamemode {

    private static final long CAPTURE_TIMER_MS = 45 * 1000;
    private static final int WIN_CONDITION = 2;
    private static final int SUDDEN_DEATH_CONDITION = 4;
    private static final int GLOW_INTERVAL_TICKS = 100; // 5 seconds
    private static final long CAPTURE_BONUS = 15000;
    private static final long PLATE_ACTIVATION_MS = 3000; // 3 seconds to capture on plate
    private static final double BANNER_ORBIT_RADIUS = 0.7; // Distance from plate center
    private static final double BANNER_ROTATION_SPEED = 4.0; // Degrees per tick

    private final Map<Integer, Integer> flagCaptures;
    private final Map<UUID, Long> platePressStartTime; // Track when player starts standing on plate
    private final Map<Integer, FlagState> flagStates; // Red flag (team 1) and Blue flag (team 2)

    private boolean inSuddenDeath;
    private BukkitTask carrierGlowTask;
    private BukkitTask captureTimerTask;
    private BukkitTask bannerRotationTask;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);
        
        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.platePressStartTime = new HashMap<>();
        this.flagStates = new HashMap<>(2);
        this.inSuddenDeath = false;
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
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<gold>Capture the Flag has been selected as the gamemode!</gold>");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<yellow>Capture the enemy's flag and return it to your base to score!</yellow>");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<yellow>Capturing the flag 45 seconds after stealing it grants your team a split 15k bonus!</yellow>");

        // Place pressure plates for capture zones
        placePressurePlates();
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[CTF] Combat phase started");

        // Reset flag state each round
        flagStates.put(1, flagStates.get(1).withoutHolder());
        flagStates.put(2, flagStates.get(2).withoutHolder());
        platePressStartTime.clear();

        // Start carrier glow effect task
        startCarrierGlowEffect();

        // Start capture timer task
        startCaptureTimerTask();
        

        // Start banner rotation task
        startBannerRotationTask();
    }

    @Override
    public void onRoundEnd() {
        // Reset for next round if continuing
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();

        // Check if victim was carrying a flag
        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        if (redFlag != null && redFlag.isHeld() && redFlag.holder().equals(victimUuid)) {
            Messages.debug("[CTF] Team Red flag holder eliminated: " + victim.getName());
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Team Red's flag holder was eliminated!</bold></red>");
            flagStates.put(1, redFlag.withoutHolder());
            moveBannerToLocation(redFlag.bannerDisplay(), redFlag.capturePlate());
            SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        } else if (blueFlag != null && blueFlag.isHeld() && blueFlag.holder().equals(victimUuid)) {
            Messages.debug("[CTF] Team Blue flag holder eliminated: " + victim.getName());
            Messages.broadcastWithPrefix(session.getPlayers(), "<blue><bold>Team Blue's flag holder was eliminated!</bold></blue>");
            flagStates.put(2, blueFlag.withoutHolder());
            moveBannerToLocation(blueFlag.bannerDisplay(), blueFlag.capturePlate());
            SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        }
    }

    @Override
    public void onPlayerSpawn(Player player) {
        // No special spawn logic
    }

    @Override
    public boolean checkGameWinner() {
        int targetCaptures = inSuddenDeath ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;
        int captures1 = flagCaptures.get(1);
        int captures2 = flagCaptures.get(2);

        if (captures1 >= targetCaptures || captures2 >= targetCaptures) {
            Messages.debug("[CTF] Game winner found - Team " + (captures1 >= targetCaptures ? 1 : 2) + " with " + Math.max(captures1, captures2) + " captures");
            return true;
        }

        // Check for sudden death condition
        if (!inSuddenDeath && captures1 == 3 && captures2 == 3) {
            enterSuddenDeath();
        }

        return false;
    }

    @Override
    public int getWinningTeam() {
        int targetCaptures = inSuddenDeath ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;

        if (flagCaptures.get(1) >= targetCaptures) {
            return 1;
        } else if (flagCaptures.get(2) >= targetCaptures) {
            return 2;
        }
        return 0;
    }

    @Override
    public void cleanup() {
        cancelTask(carrierGlowTask);
        cancelTask(captureTimerTask);
        cancelTask(bannerRotationTask);
        flagStates.clear();
        platePressStartTime.clear();
        flagCaptures.clear();

        // Remove banner entities
        for (FlagState flag : flagStates.values()) {
            if (flag != null && flag.bannerDisplay() != null && !flag.bannerDisplay().isDead()) {
                flag.bannerDisplay().remove();
            }
        }
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
        int targetCaptures = inSuddenDeath ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;
        return "<yellow>Capture the enemy's flag! First team to " + targetCaptures + " captures wins!</yellow>";
    }


    /**
     * Place pressure plates from template config at their capture locations
     */
    private void placePressurePlates() {
        var template = session.getArenaTemplate();
        if (template == null) {
            Messages.debug("[CTF] Template not found for CTF pressure plates");
            return;
        }

        World world = session.getGameWorld();
        if (world == null) {
            Messages.debug("[CTF] Game world is null, cannot place pressure plates");
            return;
        }

        // Place Team 1's capture plate (from template)
        Location teamRedPlateLocation = template.getCTFCaptureTeamRedPlate();
        if (teamRedPlateLocation != null) {
            // Convert template location to world copy location
            Location copiedLoc = teamRedPlateLocation.clone();
            copiedLoc.setWorld(world);
            Block block = world.getBlockAt(copiedLoc);
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            Messages.debug("[CTF] Placed Team 1 capture plate at " + copiedLoc);

            // Create banner for Team 1 plate
            BlockDisplay banner = createBannerDisplay(copiedLoc, Material.RED_BANNER);
            FlagState redFlag = flagStates.get(1)
                    .withCapturePlate(copiedLoc)
                    .withBannerDisplay(banner);
            flagStates.put(1, redFlag);
        } else {
            Messages.debug("[CTF] Team 1 capture plate location not found in template");
        }

        // Place Team 2's capture plate (from template)
        Location teamBluePlateLocation = template.getCTFCaptureTeamBluePlate();
        if (teamBluePlateLocation != null) {
            // Convert template location to world copy location
            Location copiedLoc = teamBluePlateLocation.clone();
            copiedLoc.setWorld(world);
            Block block = world.getBlockAt(copiedLoc);
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            Messages.debug("[CTF] Placed Team 2 capture plate at " + copiedLoc);

            // Create banner for Team 2 plate
            BlockDisplay banner = createBannerDisplay(copiedLoc, Material.BLUE_BANNER);
            FlagState blueFlag = flagStates.get(2)
                    .withCapturePlate(copiedLoc)
                    .withBannerDisplay(banner);
            flagStates.put(2, blueFlag);
        } else {
            Messages.debug("[CTF] Team 2 capture plate location not found in template");
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
     * Check if player is pressing a plate and handle flag pickup/scoring
     * This should be called from PlayerMoveEvent when a player is on a pressure plate
     * <p>
     * Logic:
     * 1. If player presses ENEMY plate without flag for 3 seconds -> pickup flag (banner to head)
     * 2. If player presses OWN plate while carrying ENEMY flag for 3 seconds -> score
     */
    public void checkPlateCapture(Player player) {
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();

        FlagState blueFlag = flagStates.get(2);
        FlagState redFlag = flagStates.get(1);

        // Check if player is on Team 1 (Red) plate
        if (redFlag != null && redFlag.capturePlate() != null && isPlayerOnPlate(player, redFlag.capturePlate())) {
            // Carrying Blue flag and on Red plate = SCORE
            if (blueFlag != null && blueFlag.isHeld() && blueFlag.holder().equals(playerUuid)) {
                if (!platePressStartTime.containsKey(playerUuid)) {
                    platePressStartTime.put(playerUuid, now);
                    Messages.debug("[CTF] " + player.getName() + " on Team Red plate with Blue flag (scoring)");
                } else {
                    long pressedTime = now - platePressStartTime.get(playerUuid);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        Messages.debug("[CTF] " + player.getName() + " scored with Blue flag!");
                        flagCapture(player, 1);
                        platePressStartTime.remove(playerUuid);
                    }
                }
            }
            // Red flag available and not held = PICKUP
            else if (!redFlag.isHeld() && (blueFlag == null || !blueFlag.isHeld())) {
                if (!platePressStartTime.containsKey(playerUuid)) {
                    platePressStartTime.put(playerUuid, now);
                    Messages.debug("[CTF] " + player.getName() + " on Team Red plate (attempting pickup)");
                } else {
                    long pressedTime = now - platePressStartTime.get(playerUuid);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        Messages.debug("[CTF] " + player.getName() + " picked up Red flag!");
                        flagPickup(player, 1);
                        platePressStartTime.remove(playerUuid);
                    }
                }
            } else {
                platePressStartTime.remove(playerUuid);
            }
        }
        // Check if player is on Team 2 (Blue) plate
        else if (blueFlag != null && blueFlag.capturePlate() != null && isPlayerOnPlate(player, blueFlag.capturePlate())) {
            // Carrying Red flag and on Blue plate = SCORE
            if (redFlag != null && redFlag.isHeld() && redFlag.holder().equals(playerUuid)) {
                if (!platePressStartTime.containsKey(playerUuid)) {
                    platePressStartTime.put(playerUuid, now);
                    Messages.debug("[CTF] " + player.getName() + " on Team Blue plate with Red flag (scoring)");
                } else {
                    long pressedTime = now - platePressStartTime.get(playerUuid);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        Messages.debug("[CTF] " + player.getName() + " scored with Red flag!");
                        flagCapture(player, 2);
                        platePressStartTime.remove(playerUuid);
                    }
                }
            }
            // Blue flag available and not held = PICKUP
            else if (!blueFlag.isHeld() && (redFlag == null || !redFlag.isHeld())) {
                if (!platePressStartTime.containsKey(playerUuid)) {
                    platePressStartTime.put(playerUuid, now);
                    Messages.debug("[CTF] " + player.getName() + " on Team Blue plate (attempting pickup)");
                } else {
                    long pressedTime = now - platePressStartTime.get(playerUuid);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        Messages.debug("[CTF] " + player.getName() + " picked up Blue flag!");
                        flagPickup(player, 2);
                        platePressStartTime.remove(playerUuid);
                    }
                }
            } else {
                platePressStartTime.remove(playerUuid);
            }
        } else {
            // Player left all plates
            platePressStartTime.remove(playerUuid);
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
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<blue>" + player.getName() + " has stolen Team Red's flag!</blue>");

            // Move banner to player head
        } else {
            flagStates.put(2, updatedFlag);
            Messages.debug("[CTF] " + player.getName() + " picked up Team Blue's flag");
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<red>" + player.getName() + " has stolen Team Blue's flag!</red>");

            // Move banner to player head
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
        int targetCaptures = inSuddenDeath ? SUDDEN_DEATH_CONDITION : WIN_CONDITION;

        Messages.debug("[CTF] Team " + teamNumber + " captured a flag! Total captures: " + captures + "/" + targetCaptures);
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<gold><bold>" + player.getName() + " has captured the flag! " +
                captures + "/" + targetCaptures + "</bold></gold>");

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        awardCaptureBonus(teamNumber);

        // Reset flag holders and move banners back to plates
        FlagState blueFlag = flagStates.get(2);
        FlagState redFlag = flagStates.get(1);

        if (teamNumber == 1) {
            if (blueFlag != null) {
                flagStates.put(2, blueFlag.withoutHolder());
                moveBannerToLocation(blueFlag.bannerDisplay(), blueFlag.capturePlate());
            }
        } else {
            if (redFlag != null) {
                flagStates.put(1, redFlag.withoutHolder());
                moveBannerToLocation(redFlag.bannerDisplay(), redFlag.capturePlate());
            }
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
                Messages.send(p, "<green>+3,750 coins! (45-second capture bonus)</green>");
                session.getCashClashPlayer(uuid).addCoins(bonusPerPlayer);
            }
        }

        String teamName = team == 1 ? "Red" : "Blue";
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<yellow>Team " + teamName + " earned a 45-second capture bonus!</yellow>");
    }

    /**
     * Enter sudden death
     */
    private void enterSuddenDeath() {
        inSuddenDeath = true;
        Messages.debug("[CTF] Entering sudden death mode - both teams at 3-3 captures");
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<red><bold>SUDDEN DEATH! Both teams must now capture 4 flags to win!</bold></red>");
    }

    /**
     * Create a BlockDisplay banner entity at the given location
     */
    private BlockDisplay createBannerDisplay(Location location, Material bannerType) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        // Spawn banner display above the pressure plate
        Location displayLoc = location.clone().add(0, 1.5, 0);
        BlockDisplay banner = world.spawn(displayLoc, BlockDisplay.class);
        banner.setBlock(Bukkit.createBlockData(bannerType));
        banner.setTeleportDuration(0);
        banner.setInterpolationDuration(0);

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

        // Position banner straight up above player's head
        Location headLoc = player.getEyeLocation().add(0, 1.2, 0);
        banner.teleport(headLoc);

        // Use default transformation (identity)
        banner.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                new Quaternionf()
        ));

        // Add banner as passenger to the player
        player.addPassenger(banner);
        Messages.debug("[CTF] Moved banner to " + player.getName() + "'s head and added as passenger");
    }

    /**
     * Move banner to float above a location (plate) and remove from passengers
     */
    private void moveBannerToLocation(BlockDisplay banner, Location location) {
        if (banner == null || banner.isDead() || location == null) {
            return;
        }

        // Remove from any passengers (i.e., from player if being carried)
        if (banner.getVehicle() != null) {
            banner.getVehicle().removePassenger(banner);
        }

        Location displayLoc = location.clone().add(0.5, 1.5, 0.5);
        banner.teleport(displayLoc);
        Messages.debug("[CTF] Moved banner back to plate at " + displayLoc);
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
        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        rotateBanner(redFlag, 1);
        rotateBanner(blueFlag, 2);
    }

    /**
     * Rotate a single banner in a perfect circle around the plate
     * Banner is only rotated when not being carried by a player
     */
    private void rotateBanner(FlagState flag, int teamNumber) {
        if (flag == null || flag.bannerDisplay() == null || flag.bannerDisplay().isDead() || flag.capturePlate() == null) return;

        // Skip rotation if banner is being carried (is a passenger of a player)
        if (flag.isHeld()) return;

        double currentAngle = flag.bannerAngle();

        // Increment angle for rotation
        double newAngleDegrees = currentAngle + BANNER_ROTATION_SPEED;
        if (newAngleDegrees >= 360) {
            newAngleDegrees -= 360;
        }

        // Convert to radians for trigonometry
        double angleInRadians = Math.toRadians(newAngleDegrees);

        // Calculate position on circle around the plate
        double offsetX = BANNER_ORBIT_RADIUS * Math.cos(angleInRadians);
        double offsetZ = BANNER_ORBIT_RADIUS * Math.sin(angleInRadians);

        Location bannerPos = flag.capturePlate().clone().add(offsetX, 1.5, offsetZ);
        flag.bannerDisplay().teleport(bannerPos);

        // Use identity transformation
        flag.bannerDisplay().setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(1, 1, 1),
                new Quaternionf()
        ));

        // Update angle in flag state
        FlagState updatedFlag = flag.withBannerAngle(newAngleDegrees);
        flagStates.put(teamNumber, updatedFlag);

        // Spawn team-colored particles around the orbit path
        Color teamColor = teamNumber == 1 ? Color.RED : Color.BLUE;
        for (int i = 0; i < 8; i++) {
            double particleAngle = Math.PI * 2 * i / 8;
            double particleX = BANNER_ORBIT_RADIUS * Math.cos(particleAngle);
            double particleZ = BANNER_ORBIT_RADIUS * Math.sin(particleAngle);
            ParticleUtils.spawnDust(flag.capturePlate().clone().add(particleX, 0.1, particleZ), teamColor, 1, 1, 0);
        }
    }


    public boolean isFlagHeld(int teamNumber) {
        FlagState flag = flagStates.get(teamNumber);
        return flag != null && flag.isHeld();
    }

    public UUID getFlagHolder(int teamNumber) {
        FlagState flag = flagStates.get(teamNumber);
        return flag != null ? flag.holder() : null;
    }

    public Location getFlagBase(int teamNumber) {
        FlagState flag = flagStates.get(teamNumber);
        return flag != null ? flag.getFlagBase() : null;
    }

    public int getFlagCaptures(int teamNumber) {
        return flagCaptures.get(teamNumber);
    }

    public boolean isSilenced(UUID playerUuid) {
        FlagState redFlag = flagStates.get(1);
        FlagState blueFlag = flagStates.get(2);

        return (redFlag != null && redFlag.isHeld() && redFlag.holder().equals(playerUuid)) ||
               (blueFlag != null && blueFlag.isHeld() && blueFlag.holder().equals(playerUuid));
    }
}