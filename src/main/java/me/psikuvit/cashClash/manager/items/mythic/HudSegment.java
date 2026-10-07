package me.psikuvit.cashClash.manager.items.mythic;

/**
 * One piece of a held mythic's action-bar HUD (a cooldown, a charge count, a mode), as
 * MiniMessage text without a colour of its own. {@code alert} pieces are drawn red even when the
 * rest of the line isn't.
 */
public record HudSegment(String text, boolean alert) {
}
