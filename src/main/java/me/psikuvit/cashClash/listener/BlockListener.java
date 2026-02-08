package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidated listener for all BlockBreakEvent and BlockPlaceEvent handling.
 * Handles: lobby protection, shop phase protection, map protection, custom items.
 */
public class BlockListener implements Listener {

    // Map of session UUID to set of player-placed block locations
    private static final Map<UUID, Set<Location>> placedBlocks = new ConcurrentHashMap<>();

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

    // ==================== BLOCK BREAK ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreakLobby(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Skip if player is in a game session
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        // Cancel for non-admins in lobby
        if (!player.hasPermission("cashclash.admin")) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if a block was placed by a player in the given session.
     */
    public static boolean isPlayerPlaced(UUID sessionId, Location location) {
        Set<Location> blocks = placedBlocks.get(sessionId);
        return blocks != null && blocks.contains(location.toBlockLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlaceGame(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);

        // Only handle players in a game session
        if (session == null) return;

        // Check if in correct world
        if (!player.getWorld().equals(session.getGameWorld())) {
            Messages.debug("World mismatch for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return;
        }

        // Only allow placing during combat
        if (session.getState() != GameState.COMBAT) {
            Messages.debug("Session state is not COMBAT for player " + player.getName() + " in BlockPlaceEvent");
            event.setCancelled(true);
            return;
        }

        // Track this block as player-placed (allow the placement)
        UUID sessionId = session.getSessionId();
        Block block = event.getBlock();

        Location loc = block.getLocation().toBlockLocation();

        placedBlocks.computeIfAbsent(sessionId, k -> new HashSet<>()).add(loc);
    }

    // ==================== STATIC UTILITIES ====================

    /**
     * Clean up tracked blocks when a session ends.
     */
    public static void cleanupSession(UUID sessionId) {
        placedBlocks.remove(sessionId);
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
        // Use block location (integer coordinates) for consistent comparison
        Location loc = block.getLocation().toBlockLocation();

        Set<Location> sessionPlacedBlocks = placedBlocks.get(sessionId);

        // If the block was NOT placed by a player, cancel the break
        if (sessionPlacedBlocks == null || !sessionPlacedBlocks.contains(loc)) {
            event.setCancelled(true);
            return;
        }

        // Block was player-placed, allow breaking and remove from tracking
        sessionPlacedBlocks.remove(loc);
    }
}

