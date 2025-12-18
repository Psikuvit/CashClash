package me.psikuvit.cashClash.listener.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Handles block regeneration system
 */
public class BlockListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // TODO: Implement block regeneration
        // Blocks should regenerate unless a player is standing on that location
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Allow block placement during combat phase
    }
}

