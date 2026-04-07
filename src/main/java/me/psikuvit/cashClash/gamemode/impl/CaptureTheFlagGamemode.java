package me.psikuvit.cashClash.gamemode.impl;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.Gamemode;
import me.psikuvit.cashClash.gamemode.GamemodeType;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
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
    private final Map<Integer, Integer> flagCaptures;
    private final Map<UUID, Long> carryStartTime;
    private final Map<UUID, Long> platePressStartTime; // Track when player starts standing on plate
    private UUID teamRedFlagHolder;
    private UUID teamBlueFlagHolder;
    private long teamRedFlagCaptureTime;
    private long teamBlueFlagCaptureTime;
    private Location teamRedFlagBase;
    private Location teamBlueFlagBase;
    private Location teamRedCapturePlate;
    private Location teamBlueCapturePlate;
    private BlockDisplay teamRedBannerDisplay;
    private BlockDisplay teamBlueBannerDisplay;
    private boolean inSuddenDeath;
    private BukkitTask carrierGlowTask;
    private BukkitTask captureTimerTask;
    private BukkitTask plateCheckTask;
    private BukkitTask bannerRotationTask;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);
        
        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.carryStartTime = new HashMap<>();
        this.platePressStartTime = new HashMap<>();
        this.teamRedFlagHolder = null;
        this.teamBlueFlagHolder = null;
        this.teamRedFlagCaptureTime = 0;
        this.teamBlueFlagCaptureTime = 0;
        this.teamRedFlagBase = null;
        this.teamBlueFlagBase = null;
        this.teamRedCapturePlate = null;
        this.teamBlueCapturePlate = null;
        this.teamRedBannerDisplay = null;
        this.teamBlueBannerDisplay = null;
        this.inSuddenDeath = false;
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
        this.plateCheckTask = null;
        this.bannerRotationTask = null;

        // Pre-populate capture map
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
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

        // Flag bases will be at arena spawn points
        // This is a simplified version - you may need to adjust based on your arena system
        initializeFlagLocations();
        
        // Place pressure plates for capture zones
        placePressurePlates();
    }

    @Override
    public void onCombatPhaseStart() {
        Messages.debug("[CTF] Combat phase started");

        // Reset flag state each round
        teamRedFlagHolder = null;
        teamBlueFlagHolder = null;
        teamRedFlagCaptureTime = 0;
        teamBlueFlagCaptureTime = 0;
        carryStartTime.clear();
        platePressStartTime.clear();

        // Start carrier glow effect task
        startCarrierGlowEffect();

        // Start capture timer task
        startCaptureTimerTask();
        
        // Start pressure plate check task
        startPlateCheckTask();

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
        if (victimUuid.equals(teamRedFlagHolder)) {
            Messages.debug("[CTF] Team Red flag holder eliminated: " + victim.getName());
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Team Red's flag holder was eliminated!</bold></red>");
            teamRedFlagHolder = null;
            carryStartTime.remove(victimUuid);
            moveBannerToLocation(teamRedBannerDisplay, teamRedCapturePlate);
            SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        } else if (victimUuid.equals(teamBlueFlagHolder)) {
            Messages.debug("[CTF] Team Blue flag holder eliminated: " + victim.getName());
            Messages.broadcastWithPrefix(session.getPlayers(), "<blue><bold>Team Blue's flag holder was eliminated!</bold></blue>");
            teamBlueFlagHolder = null;
            carryStartTime.remove(victimUuid);
            moveBannerToLocation(teamBlueBannerDisplay, teamBlueCapturePlate);
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
        cancelTask(plateCheckTask);
        cancelTask(bannerRotationTask);
        teamRedFlagHolder = null;
        teamBlueFlagHolder = null;
        carryStartTime.clear();
        platePressStartTime.clear();
        flagCaptures.clear();

        // Remove banner entities
        if (teamRedBannerDisplay != null && !teamRedBannerDisplay.isDead()) {
            teamRedBannerDisplay.remove();
        }
        if (teamBlueBannerDisplay != null && !teamBlueBannerDisplay.isDead()) {
            teamBlueBannerDisplay.remove();
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
     * Initialize flag locations based on arena spawns
     */
    private void initializeFlagLocations() {
        // This is simplified - you should use arena-specific spawn points
        // For now, using general arena locations
        World world = session.getGameWorld();
        if (world != null) {
            // These would normally be read from arena config
            teamRedFlagBase = world.getSpawnLocation().clone().add(0, 1, 0);
            teamBlueFlagBase = world.getSpawnLocation().clone().add(50, 1, 0);
        }
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
            teamRedCapturePlate = copiedLoc;
            Messages.debug("[CTF] Placed Team 1 capture plate at " + copiedLoc);

            // Create banner for Team 1 plate
            teamRedBannerDisplay = createBannerDisplay(copiedLoc, Material.RED_BANNER);
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
            teamBlueCapturePlate = copiedLoc;
            Messages.debug("[CTF] Placed Team 2 capture plate at " + copiedLoc);

            // Create banner for Team 2 plate
            teamBlueBannerDisplay = createBannerDisplay(copiedLoc, Material.BLUE_BANNER);
        } else {
            Messages.debug("[CTF] Team 2 capture plate location not found in template");
        }
    }

    /**
     * Start task to check if flag carriers are standing on capture plates
     */
    private void startPlateCheckTask() {
        plateCheckTask = SchedulerUtils.runTaskTimer(this::checkPlateCaptures, 0, 10);
    }

    /**
     * Check if flag carriers standing on plates have been there long enough to capture
     */
    private void checkPlateCaptures() {
        long now = System.currentTimeMillis();

        // Check Team 1's flag holder on Team 2's capture plate
        if (teamBlueFlagHolder != null && teamBlueCapturePlate != null) {
            Player carrier = Bukkit.getPlayer(teamBlueFlagHolder);
            if (carrier != null && carrier.isOnline() && isPlayerOnPlate(carrier, teamBlueCapturePlate)) {
                // Player is on the capture plate
                if (!platePressStartTime.containsKey(teamBlueFlagHolder)) {
                    // First time on plate, start timer
                    platePressStartTime.put(teamBlueFlagHolder, now);
                    Messages.debug("[CTF] " + carrier.getName() + " standing on Team 2 capture plate");
                } else {
                    long pressedTime = now - platePressStartTime.get(teamBlueFlagHolder);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        // Capture completed!
                        Messages.debug("[CTF] " + carrier.getName() + " captured Team 2's flag!");
                        flagCapture(carrier, 2);
                        platePressStartTime.remove(teamBlueFlagHolder);
                    }
                }
            } else {
                // Player left the plate or is offline
                platePressStartTime.remove(teamBlueFlagHolder);
            }
        }

        // Check Team 2's flag holder on Team 1's capture plate
        if (teamRedFlagHolder != null && teamRedCapturePlate != null) {
            Player carrier = Bukkit.getPlayer(teamRedFlagHolder);
            if (carrier != null && carrier.isOnline() && isPlayerOnPlate(carrier, teamRedCapturePlate)) {
                // Player is on the capture plate
                if (!platePressStartTime.containsKey(teamRedFlagHolder)) {
                    // First time on plate, start timer
                    platePressStartTime.put(teamRedFlagHolder, now);
                    Messages.debug("[CTF] " + carrier.getName() + " standing on Team 1 capture plate");
                } else {
                    long pressedTime = now - platePressStartTime.get(teamRedFlagHolder);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        // Capture completed!
                        Messages.debug("[CTF] " + carrier.getName() + " captured Team 1's flag!");
                        flagCapture(carrier, 1);
                        platePressStartTime.remove(teamRedFlagHolder);
                    }
                }
            } else {
                // Player left the plate or is offline
                platePressStartTime.remove(teamRedFlagHolder);
            }
        }
    }

    /**
     * Check if a player is standing on a block
     */
    private boolean isPlayerOnBlock(Player player, Location blockLoc) {
        if (blockLoc == null) return false;
        
        Location playerLoc = player.getLocation();
        // Check if player is within 0.5 blocks of the block's center
        return Math.abs(playerLoc.getX() - blockLoc.getX() - 0.5) <= 0.5 &&
               Math.abs(playerLoc.getY() - blockLoc.getY() - 1) <= 1.0 &&
               Math.abs(playerLoc.getZ() - blockLoc.getZ() - 0.5) <= 0.5;
    }

    /**
     * Start task to show glowing effect on flag carriers every 5 seconds
     */
    private void startCarrierGlowEffect() {
        carrierGlowTask = SchedulerUtils.runTaskTimer(this::applyGlowToCarriers, 0, GLOW_INTERVAL_TICKS);
    }

    private void applyGlowToCarriers() {
        applyGlowIfCarrying(teamRedFlagHolder);
        applyGlowIfCarrying(teamBlueFlagHolder);
    }

    private void applyGlowIfCarrying(UUID carrierUuid) {
        if (carrierUuid != null) {
            Player carrier = Bukkit.getPlayer(carrierUuid);
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
        
        if (teamRedFlagCaptureTime > 0 && (now - teamRedFlagCaptureTime) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(1);
            teamRedFlagCaptureTime = 0;
        }
        
        if (teamBlueFlagCaptureTime > 0 && (now - teamBlueFlagCaptureTime) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(2);
            teamBlueFlagCaptureTime = 0;
        }
    }

    /**
     * Handle flag pickup
     */
    public void flagPickup(Player player, int enemyTeamNumber) {
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (enemyTeamNumber == 1) {
            teamRedFlagHolder = playerUuid;
            teamRedFlagCaptureTime = now;
            carryStartTime.put(playerUuid, now);
            Messages.debug("[CTF] " + player.getName() + " picked up Team Red's flag");
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<blue>" + player.getName() + " has stolen Team Red's flag!</blue>");

            // Move banner to player head
            moveBannerToPlayer(teamRedBannerDisplay, player);
        } else {
            team2FlagHolder = playerUuid;
            team2FlagCaptureTime = now;
            teamBlueFlagHolder = playerUuid;
            teamBlueFlagCaptureTime = now;
            carryStartTime.put(playerUuid, now);
            Messages.debug("[CTF] " + player.getName() + " picked up Team Blue's flag");
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<red>" + player.getName() + " has stolen Team Blue's flag!</red>");

            // Move banner to player head
            moveBannerToPlayer(teamBlueBannerDisplay, player);
        }

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

        // Reset flag holders and move banners back to plates
        if (teamNumber == 1) {
            teamBlueFlagHolder = null;
            teamBlueFlagCaptureTime = 0;
            moveBannerToLocation(teamBlueBannerDisplay, teamBlueCapturePlate);
        } else {
            teamRedFlagHolder = null;
            teamRedFlagCaptureTime = 0;
            moveBannerToLocation(teamRedBannerDisplay, teamRedCapturePlate);
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
        Location displayLoc = location.clone().add(0.5, 1.5, 0.5);
        BlockDisplay banner = world.spawn(displayLoc, BlockDisplay.class);
        banner.setBlock(Bukkit.createBlockData(bannerType));
        banner.setTeleportDuration(0);
        banner.setInterpolationDuration(0);

        Messages.debug("[CTF] Created banner display at " + displayLoc);
        return banner;
    }

    /**
     * Move banner to float above player's head
     */
    private void moveBannerToPlayer(BlockDisplay banner, Player player) {
        if (banner == null || banner.isDead() || !player.isOnline()) {
            return;
        }

        Location headLoc = player.getEyeLocation().add(0, 1, 0);
        banner.teleport(headLoc);
        Messages.debug("[CTF] Moved banner to " + player.getName() + "'s head");
    }

    /**
     * Move banner to float above a location (plate)
     */
    private void moveBannerToLocation(BlockDisplay banner, Location location) {
        if (banner == null || banner.isDead() || location == null) {
            return;
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
        rotateBanner(teamRedBannerDisplay, teamRedCapturePlate, true);
        rotateBanner(teamBlueBannerDisplay, teamBlueCapturePlate, false);
    }

    /**
     * Rotate a single banner
     */
    private void rotateBanner(BlockDisplay banner) {
        if (banner == null || banner.isDead()) {
            return;
        }

        // Get current transformation
        Transformation current = banner.getTransformation();

        // Rotate around Y axis (vertical rotation) - 2 degrees per tick = 1 full rotation every 180 ticks (9 seconds)
        Quaternionf rotation = new Quaternionf();
        float angle = (float) Math.toRadians(2); // 2 degrees per tick
        rotation.rotateY(angle);

        // Apply the rotation to the existing rotation
        Quaternionf newRotation = new Quaternionf(current.getLeftRotation()).mul(rotation);

        Transformation newTransform = new Transformation(
                current.getTranslation(),
                newRotation,
                current.getScale(),
                current.getRightRotation()
        );

        banner.setTransformation(newTransform);
    }

    public boolean isFlagHeld(int teamNumber) {
        return teamNumber == 1 ? teamRedFlagHolder != null : teamBlueFlagHolder != null;
    }

    public UUID getFlagHolder(int teamNumber) {
        return teamNumber == 1 ? teamRedFlagHolder : teamBlueFlagHolder;
    }

    public Location getFlagBase(int teamNumber) {
        return teamNumber == 1 ? teamRedFlagBase : teamBlueFlagBase;
    }

    public int getFlagCaptures(int teamNumber) {
        return flagCaptures.get(teamNumber);
    }

    public boolean isSilenced(UUID playerUuid) {
        return playerUuid.equals(teamRedFlagHolder) || playerUuid.equals(teamBlueFlagHolder);
    }
}