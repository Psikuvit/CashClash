package me.psikuvit.cashClash.manager.items.mythic;

/**
 * One piece of the action-bar HUD (a cooldown, a charge count, a mode), as MiniMessage text
 * without a colour of its own. {@code alert} pieces are drawn red even when the rest of the line
 * isn't; {@code silenceable} pieces belong to an ability a CTF flag carrier's silence blocks, and
 * turn red while the holder is silenced - the rest stay white.
 */
public record HudSegment(String text, boolean alert, boolean silenceable) {

    public HudSegment(String text, boolean alert) {
        this(text, alert, false);
    }
}
