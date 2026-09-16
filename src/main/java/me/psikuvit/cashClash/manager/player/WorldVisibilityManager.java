package me.psikuvit.cashClash.manager.player;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Keeps each player's tab list (and player-list visibility) scoped to their own Bukkit world -
 * the server lobby and each arena's copied game world are separate audiences, so a match in
 * progress doesn't see the lobby or other concurrent matches in tab, and vice versa.
 */
public class WorldVisibilityManager {

    private final CashClashPlugin plugin;

    public WorldVisibilityManager(CashClashPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Re-syncs {@code player}'s visibility of/to every other online player against current
     * worlds. Symmetric, so a single call after any world change (join, teleport, respawn,
     * arena entry/exit) correctly updates both directions for everyone involved.
     */
    public void refreshVisibility(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = player.getWorld();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;

            if (other.getWorld().equals(world)) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            } else {
                player.hidePlayer(plugin, other);
                other.hidePlayer(plugin, player);
            }
        }
    }
}
