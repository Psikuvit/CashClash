package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.util.CooldownManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

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
     * What this mythic shows above the hotbar while held: the cooldowns of its abilities that
     * aren't ready, plus any charge count, mode or hit counter. Empty when there's nothing to
     * show.
     */
    public List<HudSegment> hudSegments(Player player) {
        return List.of();
    }

    /**
     * Adds an ability's cooldown to the HUD if it's on cooldown.
     */
    protected void addCooldownSegment(List<HudSegment> segments, UUID uuid, String cooldownKey, String abilityKey) {
        long remainingMs = cooldownManager.getRemainingCooldownMs(uuid, cooldownKey);
        if (remainingMs <= 0) return;
        segments.add(new HudSegment(hudText("cooldown",
                "ability", hudText("ability." + abilityKey),
                "seconds", String.valueOf(ceilSeconds(remainingMs))), false));
    }

    /**
     * A {@code mythic-hud.*} message from messages.yml.
     */
    protected static String hudText(String key, String... placeholders) {
        return CashClashPlugin.getInstance().getMessagesConfig().getMessage("mythic-hud." + key, placeholders);
    }

    protected static long ceilSeconds(long ms) {
        return ms / 1000L + (ms % 1000L > 0 ? 1 : 0);
    }

    /**
     * Whether the target player is on the team with the given team number.
     */
    protected boolean isSameTeam(GameSession session, int teamNumber, Player target) {
        Team targetTeam = session.getPlayerTeam(target);
        return targetTeam != null && targetTeam.getTeamNumber() == teamNumber;
    }
}
