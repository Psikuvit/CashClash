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

    private static final long CAPTURE_TIMER_MS = 45 * 1000;
    private static final int WIN_CONDITION = 2;
    private static final int SUDDEN_DEATH_CONDITION = 4;
    private static final int GLOW_INTERVAL_TICKS = 100; // 5 seconds
    private static final long CAPTURE_BONUS = 15000;
    private static final long PLATE_ACTIVATION_MS = 3000; // 3 seconds to capture on plate
    private final Map<Integer, Integer> flagCaptures;
    private final Map<UUID, Long> carryStartTime;
    private final Map<UUID, Long> platePressStartTime; // Track when player starts standing on plate
    private UUID team1FlagHolder;
    private UUID team2FlagHolder;
    private long team1FlagCaptureTime;
    private long team2FlagCaptureTime;
    private Location team1FlagBase;
    private Location team2FlagBase;
    private Location team1CapturePlate;
    private Location team2CapturePlate;
    private boolean inSuddenDeath;
    private BukkitTask carrierGlowTask;
    private BukkitTask captureTimerTask;
    private BukkitTask plateCheckTask;

    public CaptureTheFlagGamemode(GameSession session) {
        super(session, GamemodeType.CAPTURE_THE_FLAG);
        
        // Initialize all data structures
        this.flagCaptures = new HashMap<>(2);
        this.carryStartTime = new HashMap<>();
        this.platePressStartTime = new HashMap<>();
        this.team1FlagHolder = null;
        this.team2FlagHolder = null;
        this.team1FlagCaptureTime = 0;
        this.team2FlagCaptureTime = 0;
        this.team1FlagBase = null;
        this.team2FlagBase = null;
        this.team1CapturePlate = null;
        this.team2CapturePlate = null;
        this.inSuddenDeath = false;
        this.carrierGlowTask = null;
        this.captureTimerTask = null;
        this.plateCheckTask = null;
        
        // Pre-populate capture map
        flagCaptures.put(1, 0);
        flagCaptures.put(2, 0);
    }

    @Override
    public void onGameStart() {
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
        // Reset flag state each round
        team1FlagHolder = null;
        team2FlagHolder = null;
        team1FlagCaptureTime = 0;
        team2FlagCaptureTime = 0;
        carryStartTime.clear();
        platePressStartTime.clear();

        // Start carrier glow effect task
        startCarrierGlowEffect();

        // Start capture timer task
        startCaptureTimerTask();
        
        // Start pressure plate check task
        startPlateCheckTask();
    }

    @Override
    public void onRoundEnd() {
        // Reset for next round if continuing
    }

    @Override
    public void onPlayerDeath(Player victim, Player killer) {
        UUID victimUuid = victim.getUniqueId();

        // Check if victim was carrying a flag
        if (victimUuid.equals(team1FlagHolder)) {
            Messages.broadcastWithPrefix(session.getPlayers(), "<red><bold>Team Red's flag holder was eliminated!</bold></red>");
            team1FlagHolder = null;
            carryStartTime.remove(victimUuid);
            SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        } else if (victimUuid.equals(team2FlagHolder)) {
            Messages.broadcastWithPrefix(session.getPlayers(), "<blue><bold>Team Blue's flag holder was eliminated!</bold></blue>");
            team2FlagHolder = null;
            carryStartTime.remove(victimUuid);
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
        team1FlagHolder = null;
        team2FlagHolder = null;
        carryStartTime.clear();
        platePressStartTime.clear();
        flagCaptures.clear();
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
            team1FlagBase = world.getSpawnLocation().clone().add(0, 1, 0);
            team2FlagBase = world.getSpawnLocation().clone().add(50, 1, 0);
        }
    }

    /**
     * Place pressure plates from template config at their capture locations
     */
    private void placePressurePlates() {
        var template = session.getArenaTemplate();
        if (template == null) {
            Messages.debug("GAMEMODE_CTF", "Template not found for CTF pressure plates");
            return;
        }

        World world = session.getGameWorld();
        if (world == null) return;

        // Place Team 1's capture plate (from template)
        Location team1PlateLocation = template.getCTFCaptureTeam1Plate();
        if (team1PlateLocation != null) {
            // Convert template location to world copy location
            Location copiedLoc = team1PlateLocation.clone();
            copiedLoc.setWorld(world);
            Block block = world.getBlockAt(copiedLoc);
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            team1CapturePlate = copiedLoc;
            Messages.debug("GAMEMODE_CTF", "Placed Team 1 capture plate at " + copiedLoc);
        }

        // Place Team 2's capture plate (from template)
        Location team2PlateLocation = template.getCTFCaptureTeam2Plate();
        if (team2PlateLocation != null) {
            // Convert template location to world copy location
            Location copiedLoc = team2PlateLocation.clone();
            copiedLoc.setWorld(world);
            Block block = world.getBlockAt(copiedLoc);
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            team2CapturePlate = copiedLoc;
            Messages.debug("GAMEMODE_CTF", "Placed Team 2 capture plate at " + copiedLoc);
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
        if (team2FlagHolder != null && team2CapturePlate != null) {
            Player carrier = Bukkit.getPlayer(team2FlagHolder);
            if (carrier != null && carrier.isOnline() && isPlayerOnBlock(carrier, team2CapturePlate)) {
                // Player is on the capture plate
                if (!platePressStartTime.containsKey(team2FlagHolder)) {
                    // First time on plate, start timer
                    platePressStartTime.put(team2FlagHolder, now);
                } else {
                    long pressedTime = now - platePressStartTime.get(team2FlagHolder);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        // Capture completed!
                        flagCapture(carrier, 2);
                        platePressStartTime.remove(team2FlagHolder);
                    }
                }
            } else {
                // Player left the plate or is offline
                platePressStartTime.remove(team2FlagHolder);
            }
        }

        // Check Team 2's flag holder on Team 1's capture plate
        if (team1FlagHolder != null && team1CapturePlate != null) {
            Player carrier = Bukkit.getPlayer(team1FlagHolder);
            if (carrier != null && carrier.isOnline() && isPlayerOnBlock(carrier, team1CapturePlate)) {
                // Player is on the capture plate
                if (!platePressStartTime.containsKey(team1FlagHolder)) {
                    // First time on plate, start timer
                    platePressStartTime.put(team1FlagHolder, now);
                } else {
                    long pressedTime = now - platePressStartTime.get(team1FlagHolder);
                    if (pressedTime >= PLATE_ACTIVATION_MS) {
                        // Capture completed!
                        flagCapture(carrier, 1);
                        platePressStartTime.remove(team1FlagHolder);
                    }
                }
            } else {
                // Player left the plate or is offline
                platePressStartTime.remove(team1FlagHolder);
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
        applyGlowIfCarrying(team1FlagHolder);
        applyGlowIfCarrying(team2FlagHolder);
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
        
        if (team1FlagCaptureTime > 0 && (now - team1FlagCaptureTime) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(1);
            team1FlagCaptureTime = 0;
        }
        
        if (team2FlagCaptureTime > 0 && (now - team2FlagCaptureTime) >= CAPTURE_TIMER_MS) {
            awardCaptureBonus(2);
            team2FlagCaptureTime = 0;
        }
    }

    /**
     * Handle flag pickup
     */
    public void flagPickup(Player player, int enemyTeamNumber) {
        UUID playerUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (enemyTeamNumber == 1) {
            team1FlagHolder = playerUuid;
            team1FlagCaptureTime = now;
            carryStartTime.put(playerUuid, now);
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<blue>" + player.getName() + " has stolen Team Red's flag!</blue>");
        } else {
            team2FlagHolder = playerUuid;
            team2FlagCaptureTime = now;
            carryStartTime.put(playerUuid, now);
            Messages.broadcastWithPrefix(session.getPlayers(),
                    "<red>" + player.getName() + " has stolen Team Blue's flag!</red>");
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

        Messages.broadcastWithPrefix(session.getPlayers(),
                "<gold><bold>" + player.getName() + " has captured the flag! " +
                captures + "/" + targetCaptures + "</bold></gold>");

        SoundUtils.playTo(session.getPlayers(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // Reset flag holders
        if (teamNumber == 1) {
            team2FlagHolder = null;
            team2FlagCaptureTime = 0;
        } else {
            team1FlagHolder = null;
            team1FlagCaptureTime = 0;
        }
    }

    /**
     * Award capture bonus after 45 seconds
     */
    private void awardCaptureBonus(int team) {
        long bonusPerPlayer = CAPTURE_BONUS / 4;
        var teamObj = team == 1 ? session.getTeam1() : session.getTeam2();
        
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
        Messages.broadcastWithPrefix(session.getPlayers(),
                "<red><bold>SUDDEN DEATH! Both teams must now capture 4 flags to win!</bold></red>");
    }

    // Getters
    public boolean isFlagHeld(int teamNumber) {
        return teamNumber == 1 ? team1FlagHolder != null : team2FlagHolder != null;
    }

    public UUID getFlagHolder(int teamNumber) {
        return teamNumber == 1 ? team1FlagHolder : team2FlagHolder;
    }

    public Location getFlagBase(int teamNumber) {
        return teamNumber == 1 ? team1FlagBase : team2FlagBase;
    }

    public int getFlagCaptures(int teamNumber) {
        return flagCaptures.get(teamNumber);
    }

    public boolean isSilenced(UUID playerUuid) {
        return playerUuid.equals(team1FlagHolder) || playerUuid.equals(team2FlagHolder);
    }
}









