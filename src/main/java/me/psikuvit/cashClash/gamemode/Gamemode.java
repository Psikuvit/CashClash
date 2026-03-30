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
}

