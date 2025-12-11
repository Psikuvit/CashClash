package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.round.RoundData;
import me.psikuvit.cashClash.manager.BonusManager;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Handles damage events for bonus tracking (close calls, damage tracking)
 */
public class DamageListener implements Listener {

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;


        RoundData currentRound = session.getCurrentRoundData();
        if (currentRound == null) return;

        CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (ccPlayer == null) return;

        ccPlayer.setLastDamageTime(System.currentTimeMillis());
        currentRound.setLastDamageTime(player.getUniqueId(), System.currentTimeMillis());

        // Track damage received
        if (event.getDamage() > 0) {
            currentRound.addDamage(player.getUniqueId(), event.getFinalDamage());
        }

        // Update health tracking for close call bonus
        double healthAfter = Math.max(0, player.getHealth() - event.getFinalDamage());
        ccPlayer.updateLowestHealth(healthAfter);

        // Notify BonusManager of low health state
        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            if (healthAfter <= 2.0 && healthAfter > 0) {
                bonusManager.onReachLowHealth(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) {
            return;
        }

        CashClashPlayer ccPlayer = session.getCashClashPlayer(player.getUniqueId());
        if (ccPlayer == null) {
            return;
        }

        double healthBefore = player.getHealth();
        double healthAfter = Math.min(
                player.getHealth() + event.getAmount(),
                player.getAttribute(Attribute.MAX_HEALTH).getValue()
        );

        // Update lowest health tracking
        ccPlayer.updateLowestHealth(healthAfter);

        // Notify BonusManager of healing from low health
        BonusManager bonusManager = session.getBonusManager();
        if (bonusManager != null) {
            // Was at or below 1 heart, now above
            if (healthBefore <= 2.0 && healthAfter > 2.0) {
                bonusManager.onHealFromLowHealth(player.getUniqueId());
            }
            // Was above 1 heart but dropped back to low health (shouldn't happen in heal event, but defensive)
            else if (healthAfter <= 2.0) {
                bonusManager.onDropBackToLowHealth(player.getUniqueId());
            }
        }
    }
}
