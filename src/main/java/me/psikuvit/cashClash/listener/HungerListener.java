package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.manager.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Listener to prevent hunger loss in lobby and shopping phase.
 */
public class HungerListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);

        // Prevent hunger loss in lobby (no session) or shopping phase
        if (session == null || session.getState() == GameState.WAITING || session.getState() == GameState.SHOPPING) {
            event.setCancelled(true);
        }
    }
}

