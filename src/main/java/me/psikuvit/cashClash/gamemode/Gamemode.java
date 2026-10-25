package me.psikuvit.cashClash.gamemode;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Abstract base class for all gamemodes.
 * Each gamemode implements its own win conditions, mechanics, and special features.
 */
public abstract class Gamemode {

    protected final GameSession session;
    protected final GamemodeType type;

    private int roundWinner;
    private int roundWinnerTick = -1;
    private boolean roundDraw;

    public Gamemode(GameSession session, GamemodeType type) {
        this.session = session;
        this.type = type;
    }

    /**
     * Records that {@code team} has met this round's win condition. Whoever gets there first
     * wins it; a second team getting there on the same server tick makes the round a draw, and
     * one getting there any later changes nothing.
     */
    protected void recordRoundWin(int team) {
        if (roundDraw) return;
        int tick = Bukkit.getCurrentTick();
        if (roundWinner == 0) {
            roundWinner = team;
            roundWinnerTick = tick;
        } else if (roundWinner != team && roundWinnerTick == tick) {
            roundWinner = 0;
            roundDraw = true;
        }
    }

    /**
     * The team {@link #recordRoundWin} settled on, or 0 if none yet (or the round is a draw).
     */
    protected int getRecordedRoundWinner() {
        return roundWinner;
    }

    /**
     * Whether both teams met the win condition on the same tick - see {@link #recordRoundWin}.
     */
    public boolean isRoundDraw() {
        return roundDraw;
    }

    protected void resetRoundResult() {
        roundWinner = 0;
        roundWinnerTick = -1;
        roundDraw = false;
    }

    /**
     * Final Stand's one-life rule: everyone still in the round (bar {@code exempt}) is put on
     * their last life, so their next death keeps them out until the next round. Anyone already
     * out stays out.
     */
    protected void putPlayersOnLastLife(Predicate<UUID> exempt) {
        for (UUID uuid : session.getPlayers()) {
            if (exempt.test(uuid)) continue;
            CashClashPlayer ccp = session.getCashClashPlayer(uuid);
            if (ccp != null) ccp.setLives(Math.min(ccp.getLives(), 1));
        }
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
     * Called when the game ends, before the victory sequence sends everyone to the lobby.
     * {@link #cleanup()} only runs after that sequence, so anything attached to a player (e.g. a
     * carried CTF flag) has to come off here.
     */
    public void onGameEnd() {
    }

    /**
     * Whether a player is standing in one of this gamemode's capture areas (CTF flag circles and
     * scoring zones, KC tag zones). The Invisibility Cloak can't be used in one.
     */
    public boolean isInCaptureArea(Player player) {
        return false;
    }

    /**
     * Whether a player's outline stays their team colour for their own teammates too, instead of
     * the green teammate outline - PTP's presidents, so a team can pick its president out.
     */
    public boolean keepsTeamColorOutline(UUID playerId) {
        return false;
    }

    /**
     * Check if the round is over (returns true if it should end) - called every second during
     * combat. True with {@link #getWinningTeam()} at 0 means a draw.
     */
    public abstract boolean checkGameWinner();

    /**
     * Get the winning team number (1 or 2), or 0 if no winner yet
     */
    public abstract int getWinningTeam();

    /**
     * The team ahead on this round's objective (tags confirmed, flags captured, president
     * kills), or 0 if level - who takes the round when its timer runs out.
     */
    public abstract int getLeadingTeam();

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
     * Short objective phrase (e.g. "Capture 2 Flags") used to fill the {objective}
     * placeholder in round-start and sudden-death sequence subtitles.
     */
    public abstract String getObjectiveShort();

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
     * Message key broadcast when a tied sudden-death cycle restarts - lets
     * {@link SuddenDeathManager} announce this without a gamemode-type switch of its own.
     */
    public abstract String getSuddenDeathTiedRestartMessageKey();

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
