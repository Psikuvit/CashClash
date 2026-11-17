package me.psikuvit.cashClash.arena;

import org.bukkit.Location;

/**
 * One of a team's two spawn lines on a template: a straight line between two blocks, set with
 * {@code /cc template set <id> spawnline <start|end> <1|2> <red|blue>}. The space between a
 * team's two lines is its spawn zone. Either end can still be unset while it's being drawn.
 */
public record SpawnLine(Location start, Location end) {

    public static final SpawnLine EMPTY = new SpawnLine(null, null);

    public boolean isComplete() {
        return start != null && end != null;
    }

    public SpawnLine withStart(Location start) {
        return new SpawnLine(start, end);
    }

    public SpawnLine withEnd(Location end) {
        return new SpawnLine(start, end);
    }
}
