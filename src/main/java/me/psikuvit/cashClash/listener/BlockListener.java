package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated listener for all BlockBreakEvent and BlockPlaceEvent handling.
 * Handles: lobby protection, shop phase protection, map protection, custom items,
 * water/lava flow restrictions, web limits, leaf blocks.
 */
public class BlockListener implements Listener {

    // Map of session UUID to set of player-placed block locations
    private static final Map<UUID, Set<Location>> placedBlocks = new ConcurrentHashMap<>();

    // Map of session UUID to map of team number to water/lava source count
    private static final Map<UUID, Map<Integer, Integer>> waterLavaSourceCount = new ConcurrentHashMap<>();

    // Map of session UUID to map of player UUID to leaf block count
    private static final Map<UUID, Map<UUID, Integer>> playerLeafBlockCount = new ConcurrentHashMap<>();

    // Map of session UUID to map of player UUID to web block count
    private static final Map<UUID, Map<UUID, Integer>> playerWebBlockCount = new ConcurrentHashMap<>();

    // Map of web location to despawn task for web blocks
    private static final Map<Location, BukkitTask> webDespawnTasks = new ConcurrentHashMap<>();

    // Map of player UUID to water bucket refill task
    private static final Map<UUID, BukkitTask> waterBucketRefillTasks = new ConcurrentHashMap<>();

    // ==================== BLOCK PLACE ====================

    public static void cleanupRound(UUID sessionId) {
        Set<Location> blocks = placedBlocks.get(sessionId);
        if (blocks == null) {
            Messages.debug("No blocks to clean up for session " + sessionId);
            return;
        }
        for (Location loc : blocks) {
            if (loc == null) continue;
            loc.getBlock().setType(Material.AIR);
        }
        blocks.clear();
    }

    /**
     * Check if a block was placed by a player in the given session.
     */
    public static boolean isPlayerPlaced(UUID sessionId, Location location) {
        Set<Location> blocks = placedBlocks.get(sessionId);
        return blocks != null && blocks.contains(location.toBlockLocation());
    }


    /**
     * Clean up tracked blocks when a session ends.
     */
    public static void cleanupSession(UUID sessionId) {
        placedBlocks.remove(sessionId);
        waterLavaSourceCount.remove(sessionId);
        playerLeafBlockCount.remove(sessionId);
        playerWebBlockCount.remove(sessionId);
        // Note: waterBucketRefillTasks are per-player and handled separately
    }

    /**
     * Schedule water bucket refill after 10 seconds of use.
     * Only applies to water buckets, not lava.
     */
    private static void scheduleWaterBucketRefill(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing refill task
        BukkitTask existing = waterBucketRefillTasks.get(playerId);
        if (existing != null) {
            existing.cancel();
        }

        // Schedule new refill task
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            if (player.isOnline()) {
                // Add water bucket to inventory
                org.bukkit.inventory.ItemStack waterBucket = new org.bukkit.inventory.ItemStack(Material.WATER_BUCKET);
                player.getInventory().addItem(waterBucket);
                Messages.send(player, "listener.water-bucket-refilled");
            }
            waterBucketRefillTasks.remove(playerId);
        }, 200); // 200 ticks = 10 seconds

        waterBucketRefillTasks.put(playerId, task);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlaceGame(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (!validateBlockPlaceContext(event, player, session)) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();
        UUID sessionId = session.getSessionId();
        UUID playerId = player.getUniqueId();

        // Handle block type-specific placement logic
        if (handleWebPlacement(event, blockType, sessionId, playerId, block)) return;
        if (handleLeafPlacement(event, blockType, sessionId, playerId, block)) return;
        if (handleWaterLavaPlacement(event, blockType, sessionId, playerId, player, session)) return;

        // Track other player-placed blocks
        trackPlacedBlock(sessionId, block);
    }

    /**
     * Validate block placement context (session, world, state)
     */
    private boolean validateBlockPlaceContext(BlockPlaceEvent event, Player player, GameSession session) {
        if (session == null) return false;

        // Check if in correct world
        if (!player.getWorld().equals(session.getGameWorld())) {
            Messages.debug("World mismatch for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return false;
        }

        // Only allow placing during combat
        if (session.getState() != GameState.COMBAT) {
            Messages.debug("Session state is not COMBAT for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return false;
        }

        return true;
    }

    /**
     * Handle web block placement (max 4 per player)
     */
    private boolean handleWebPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Block block) {
        if (blockType != Material.COBWEB) return false;

        int currentCount = playerWebBlockCount.computeIfAbsent(sessionId, k -> new HashMap<>())
            .getOrDefault(playerId, 0);

        if (currentCount >= 4) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "listener.max-webs-reached");
            return true;
        }

        // Increment web count and track
        playerWebBlockCount.get(sessionId).put(playerId, currentCount + 1);
        trackPlacedBlock(sessionId, block);
        scheduleWebDespawn(block);
        return true;
    }

    /**
     * Handle leaf block placement (max 64 per player, max 3 stack height)
     */
    private boolean handleLeafPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Block block) {
        if (!isLeafBlock(blockType)) return false;

        int currentCount = playerLeafBlockCount.computeIfAbsent(sessionId, k -> new HashMap<>())
            .getOrDefault(playerId, 0);

        if (currentCount >= 64) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "listener.max-leaf-blocks-reached");
            return true;
        }

        // Check stacking height
        if (!validateLeafStackHeight(event, block)) {
            return true;
        }

        playerLeafBlockCount.get(sessionId).put(playerId, currentCount + 1);
        trackPlacedBlock(sessionId, block);
        scheduleLeafDecay(block);
        return true;
    }

    /**
     * Validate leaf block stacking (max 3 vertically)
     */
    private boolean validateLeafStackHeight(BlockPlaceEvent event, Block block) {
        int stackHeight = 1;
        Block below = block.getRelative(0, -1, 0);
        while (isLeafBlock(below.getType()) && stackHeight < 3) {
            stackHeight++;
            below = below.getRelative(0, -1, 0);
        }

        if (stackHeight >= 3) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "listener.leaf-stack-limit");
            return false;
        }
        return true;
    }

    /**
     * Handle water/lava placement (max 4 per team)
     */
    private boolean handleWaterLavaPlacement(BlockPlaceEvent event, Material blockType, UUID sessionId, UUID playerId, Player player, GameSession session) {
        if (blockType != Material.WATER && blockType != Material.LAVA) return false;

        Team team = session.getTeamRed().hasPlayer(playerId) ? session.getTeamRed() : session.getTeamBlue();
        int teamNum = team.getTeamNumber();

        int currentCount = waterLavaSourceCount.computeIfAbsent(sessionId, k -> new HashMap<>())
            .getOrDefault(teamNum, 0);

        if (currentCount >= 4) {
            event.setCancelled(true);
            Messages.send(player, "listener.max-water-lava-sources");
            return true;
        }

        waterLavaSourceCount.get(sessionId).put(teamNum, currentCount + 1);
        trackPlacedBlock(sessionId, event.getBlock());

        // Schedule water bucket refill (only for water, not lava)
        if (blockType == Material.WATER) {
            scheduleWaterBucketRefill(player);
        }
        return true;
    }

    /**
     * Track a placed block location
     */
    private void trackPlacedBlock(UUID sessionId, Block block) {
        Location loc = block.getLocation().toBlockLocation();
        placedBlocks.computeIfAbsent(sessionId, k -> new HashSet<>()).add(loc);
    }

    /**
     * Prevent water/lava from spreading, except to break webs or extinguish fire.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.isCancelled()) return;

        Block source = event.getSource();
        Material sourceType = source.getType();

        // Only handle water and lava spreading
        if (sourceType == Material.WATER || sourceType == Material.LAVA) {
            event.setCancelled(true);
        }
    }

    // ==================== STATIC UTILITIES ====================

    /**
     * Handle leaf block breaks - don't drop items.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeafBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Material type = block.getType();

        if (isLeafBlock(type)) {
            event.setDropItems(false);

            // Decrement player's leaf count
            decrementPlayerLeaf(event, playerLeafBlockCount);
        }
    }

    private void decrementPlayerLeaf(BlockBreakEvent event, Map<UUID, Map<UUID, Integer>> playerLeafBlockCount) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            Map<UUID, Integer> counts = playerLeafBlockCount.get(session.getSessionId());
            if (counts != null) {
                int current = counts.getOrDefault(player.getUniqueId(), 0);
                if (current > 0) {
                    counts.put(player.getUniqueId(), current - 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreakShopPhase(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session != null && session.getState() == GameState.SHOPPING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreakMapProtection(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        if (session == null) return;

        UUID sessionId = session.getSessionId();
        Block block = event.getBlock();
        Location loc = block.getLocation().toBlockLocation();

        Set<Location> sessionPlacedBlocks = placedBlocks.get(sessionId);

        // If the block was NOT placed by a player, cancel the break
        if (!isBlockPlayerPlaced(sessionPlacedBlocks, loc)) {
            event.setCancelled(true);
            return;
        }

        // Block was player-placed, allow breaking and remove from tracking
        sessionPlacedBlocks.remove(loc);

        // Update water/lava source count if applicable
        updateBlockTypeCountOnBreak(session, player, block);
    }

    /**
     * Check if a block was placed by a player
     */
    private boolean isBlockPlayerPlaced(Set<Location> sessionPlacedBlocks, Location loc) {
        return sessionPlacedBlocks != null && sessionPlacedBlocks.contains(loc);
    }

    /**
     * Update block type counts when a block is broken
     */
    private void updateBlockTypeCountOnBreak(GameSession session, Player player, Block block) {
        Material blockType = block.getType();
        if (blockType != Material.WATER && blockType != Material.LAVA) return;

        Team playerTeam = session.getTeamRed().hasPlayer(player.getUniqueId()) ? session.getTeamRed() : session.getTeamBlue();
        if (playerTeam == null) return;

        int teamNum = playerTeam.getTeamNumber();
        Map<Integer, Integer> counts = waterLavaSourceCount.get(session.getSessionId());
        if (counts != null) {
            int current = counts.getOrDefault(teamNum, 0);
            if (current > 0) {
                counts.put(teamNum, current - 1);
            }
        }
    }

    /**
     * Schedule web block to despawn after 5 seconds of no touch.
     */
    private void scheduleWebDespawn(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        BukkitTask task = SchedulerUtils.runTaskLater(() -> {
            if (block.getType() == Material.COBWEB) {
                block.setType(Material.AIR);
            }
            webDespawnTasks.remove(loc);
        }, 100);
        
        webDespawnTasks.put(loc, task);
    }

    /**
     * Cancel web despawn task if player touches it.
     */
    private void cancelWebDespawnTask(Location loc) {
        BukkitTask task = webDespawnTasks.remove(loc);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handle web block breaks - don't drop items.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWebBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        if (block.getType() == Material.COBWEB) {
            event.setDropItems(false);
            cancelWebDespawnTask(block.getLocation());

            // Decrement player's web count
            decrementPlayerLeaf(event, playerWebBlockCount);
        }
    }

    /**
     * Schedule leaf block to decay after 6 seconds.
     */
    private void scheduleLeafDecay(Block block) {
        SchedulerUtils.runTaskLater(() -> {
            if (isLeafBlock(block.getType())) {
                block.setType(Material.AIR);
            }
        }, 120);
    }

    /**
     * Check if a material is a leaf block.
     */
    private boolean isLeafBlock(Material material) {
        return material == Material.OAK_LEAVES || material == Material.SPRUCE_LEAVES ||
               material == Material.BIRCH_LEAVES || material == Material.JUNGLE_LEAVES ||
               material == Material.ACACIA_LEAVES || material == Material.DARK_OAK_LEAVES ||
               material == Material.MANGROVE_LEAVES || material == Material.CHERRY_LEAVES ||
               material == Material.PALE_OAK_LEAVES;
    }
}

