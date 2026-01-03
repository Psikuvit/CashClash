package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.items.CustomArmorManager;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Consolidated listener for all PlayerMoveEvent handling.
 * Handles: bounce pads, custom armor effects (landing, dragon jump).
 */
public class MoveListener implements Listener {

    private final CustomItemManager customItemManager = CustomItemManager.getInstance();
    private final CustomArmorManager armorManager = CustomArmorManager.getInstance();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        // Skip if in shopping phase
        if (session.getState().isShopping()) return;

        // Check block player is standing on for bounce pads
        Block blockBelow = player.getLocation().subtract(0, 0.1, 0).getBlock();
        if (blockBelow.getType() == Material.SLIME_BLOCK) {
            if (customItemManager.isBouncePad(blockBelow)) {
                customItemManager.handleBouncePad(player, blockBelow);
            }
        }

        // Only trigger if player actually moved position (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Custom armor landing detection
        if (player.isOnGround() && !player.isFlying()) {
            armorManager.onPlayerLand(player);
        }

        // Dragon Set: detect jump and enable double jump
        if (event.getFrom().getY() < event.getTo().getY() &&
            player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {

            armorManager.onDragonJump(player);

            if (armorManager.hasDragonSet(player)) {
                player.setAllowFlight(true);
            }
        }
    }
}

