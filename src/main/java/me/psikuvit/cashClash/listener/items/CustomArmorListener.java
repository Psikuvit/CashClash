package me.psikuvit.cashClash.listener.items;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.CustomArmorManager;
import me.psikuvit.cashClash.manager.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Hooks runtime events to custom armor logic
 */
public class CustomArmorListener implements Listener {

    private final CustomArmorManager manager = CustomArmorManager.getInstance();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Early exit: only trigger if player actually moved position (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player p = event.getPlayer();
        manager.onPlayerMove(p);

        if (p.isOnGround() && !p.isFlying()) {
            manager.onPlayerLand(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (GameManager.getInstance().getPlayerSession(p) == null) return;

        // Magic Helmet: right click to disable invisibility
        manager.onMagicHelmetRightClick(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (GameManager.getInstance().getPlayerSession(p) == null) return;

        // Bunny Shoes: crouch + uncrouch to activate
        manager.onPlayerToggleSneak(p, event.isSneaking());
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        // Dragon Set: double jump
        if (event.isFlying() && manager.tryDragonDoubleJump(p)) {
            event.setCancelled(true);
            p.setAllowFlight(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        GameSession session = GameManager.getInstance().getPlayerSession(p);
        if (session == null) return;

        double healthAfter = Math.max(0, p.getHealth() - event.getFinalDamage());

        // Dragon Set: immune to explosions
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
            event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (manager.isDragonSetImmuneToExplosion(p)) {
                event.setCancelled(true);
                return;
            }
        }

        // Guardian's Vest: resistance when low health
        manager.onPlayerDamaged(p, healthAfter);

        // Deathmauler: track damage for absorption
        manager.onDeathmaulerDamageTaken(p);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(attacker);
        if (session == null) return;

        // Handle all attack-based armor effects
        manager.onPlayerAttack(attacker, target);

        // Investor's Set: bonus damage in rounds 4/5
        double damageMultiplier = manager.getInvestorMeleeDamageMultiplier(attacker, session.getCurrentRound());
        if (damageMultiplier > 1.0) {
            event.setDamage(event.getDamage() * damageMultiplier);
        }
    }

    @EventHandler
    public void onPlayerJump(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (GameManager.getInstance().getPlayerSession(p) == null) return;

        // Detect jump (Y increased and player was on ground)
        if (event.getFrom().getY() < event.getTo().getY() &&
            p.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {

            // Dragon Set: enable double jump after first jump
            manager.onDragonJump(p);

            // Allow flight briefly for double jump detection
            if (manager.hasDragonSet(p)) {
                p.setAllowFlight(true);
            }
        }
    }
}
