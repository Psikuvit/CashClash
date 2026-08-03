package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.entity.Player;

/**
 * Base class for a single mythic item's behaviour. Each mythic item in the game
 * gets its own handler class holding its state and ability methods; the owning
 * {@link MythicItemManager} acts purely as a registry that hands out handlers
 * and fans out cleanup.
 */
public abstract class MythicItemHandler {

    protected final MythicItemManager manager;
    protected final ItemsConfig cfg;
    protected final CooldownManager cooldownManager;

    protected MythicItemHandler(MythicItemManager manager) {
        this.manager = manager;
        this.cfg = manager.getCfg();
        this.cooldownManager = manager.getCooldownManager();
    }

    /**
     * Releases all state held by this handler (game end / plugin shutdown).
     */
    public abstract void cleanup();

    /**
     * Releases this handler's state for a single player (death or quit).
     * Default no-op; overridden by handlers that track per-player state.
     */
    public void cleanupPlayer(Player player) {
    }

    /**
     * Whether the target player is on the team with the given team number.
     */
    protected boolean isSameTeam(GameSession session, int teamNumber, Player target) {
        Team targetTeam = session.getPlayerTeam(target);
        return targetTeam != null && targetTeam.getTeamNumber() == teamNumber;
    }
}
