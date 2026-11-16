package me.psikuvit.cashClash.event;

import me.psikuvit.cashClash.game.GameSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Fired when a player walks out of a running game - by disconnecting or with /leave - while
 * they're still part of it, before their spot is kept for a rejoin or given up.
 */
public class PlayerLeaveGameEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final GameSession session;

    public PlayerLeaveGameEvent(Player player, GameSession session) {
        this.player = player;
        this.session = session;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public GameSession getSession() {
        return session;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return handlers;
    }
}
