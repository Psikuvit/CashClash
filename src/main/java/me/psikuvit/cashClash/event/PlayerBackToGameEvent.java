package me.psikuvit.cashClash.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Fired when a player is put back into the game after death (not as a spectator).
 * This is different from PlayerRespawnEvent which fires when respawning as a spectator.
 * This event fires when the player is actually back in combat and ready to play.
 */
public class PlayerBackToGameEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public PlayerBackToGameEvent(Player player) {
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return handlers;
    }
}

