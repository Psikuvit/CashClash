package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.BonusType;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Handles damage events for close call bonuses
 */
public class DamageListener implements Listener {

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        var currentRound = session.getCurrentRoundData();
        if (currentRound == null) return; // defensive: no active round data yet/anymore

        CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (ccPlayer == null) return;

        // Update last damage time
        ccPlayer.setLastDamageTime(System.currentTimeMillis());
        currentRound.setLastDamageTime(player.getUniqueId(), System.currentTimeMillis());

        // Track damage dealt
        if (event.getDamage() > 0) {
            currentRound.addDamage(player.getUniqueId(), event.getFinalDamage());
        }

        // Update health tracking for close call bonus
        double healthAfter = player.getHealth() - event.getFinalDamage();
        ccPlayer.updateLowestHealth(healthAfter);

        // Check if player recovered from close call
        if (healthAfter > 2.0 && ccPlayer.checkCloseCallBonus()) {
            ccPlayer.earnBonus(BonusType.CLOSE_CALLS);
            Messages.send(player, "<gold><bold>CLOSE CALL!</bold> <yellow>+" + BonusType.CLOSE_CALLS.getReward() + "</yellow></gold>");
            ccPlayer.resetLife(); // Reset for next potential close call
        }
    }
}
