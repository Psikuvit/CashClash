package me.psikuvit.cashClash.gamemode;

import me.psikuvit.cashClash.game.GameSession;
import org.bukkit.entity.Player;

/**
 * Abstract base class for all gamemodes.
 * Each gamemode implements its own win conditions, mechanics, and special features.
 */
public abstract class Gamemode {

    protected final GameSession session;
    protected final GamemodeType type;

    public Gamemode(GameSession session, GamemodeType type) {
        this.session = session;
        this.type = type;
    }

    /**
     * Get the gamemode type
     */
    public GamemodeType getType() {
        return type;
    }

    /**
     * Called when the game starts (shopping phase begins)
     */
    public abstract void onGameStart();

    /**
     * Called when a combat phase begins
     */
    public abstract void onCombatPhaseStart();

    /**
     * Called when a round ends
     */
    public abstract void onRoundEnd();

    /**
     * Called when a player dies
     */
    public abstract void onPlayerDeath(Player victim, Player killer);

    /**
     * Called when a player respawns/joins
     */
    public abstract void onPlayerSpawn(Player player);

    /**
     * Called when a player is removed from the game (disconnect, leave, etc.)
     * Used for cleanup specific to the player (e.g., removing banners)
     */
    public void onPlayerRemove(Player player) {
        // Default: no special handling
    }

    /**
     * Check if the game has a winner (returns true if game should end)
     * This is called periodically during combat
     */
    public abstract boolean checkGameWinner();

    /**
     * Get the winning team number (1 or 2), or 0 if no winner yet
     */
    public abstract int getWinningTeam();

    /**
     * Cleanup when the game ends
     */
    public abstract void cleanup();

    /**
     * Get custom message to display at round start
     */
    public abstract String getRoundStartMessage();

    /**
     * Get custom message to display in buy phase
     */
    public abstract String getBuyPhaseMessage();

    /**
     * Called when Final Stand is activated (3 minute initial sudden-death period elapses or gamemode triggers final stand)
     * Gamemodes can override this to handle final stand specific logic
     */
    public abstract void onFinalStandActivated();

    /**
     * Called when a sudden-death cycle ends.
     * Gamemodes can inspect their cycle-specific scoring state and set a winner.
     */
    public void onSuddenDeathCycleEnded() {
        // Default: no special handling
    }

    /**
     * Called when a tied sudden-death cycle is restarting.
     * Gamemodes can reset per-cycle counters here.
     */
    public void onSuddenDeathCycleRestart() {
        // Default: no special handling
    }


    public boolean forceSuddenDeathForTesting() {
        return false;
    }

    public abstract void prepareSuddenDeathRound();

    public boolean isFinalStandActive() {
        FinalStandManager fsm = getFinalStandManager();
        return fsm.isActive();
    }


    /**
     * Get the sudden death manager for this gamemode
     */
    public abstract SuddenDeathManager getSuddenDeathManager();

    /**
     * Get the final stand manager for this gamemode (optional).
     * Default: null - gamemodes that support final stand should override to return an instance.
     */
    public abstract FinalStandManager getFinalStandManager();
}
