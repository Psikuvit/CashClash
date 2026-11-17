package me.psikuvit.cashClash.util.game;

import me.psikuvit.cashClash.arena.SpawnLine;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.util.enums.TeamColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The spawn zones a template's spawn lines mark out. A team's zone is the area between its two
 * spawn lines - the outline of the four line ends on the ground plane, so height never counts.
 * Anywhere outside both teams' zones is the neutral zone. Coordinates are compared as they are,
 * so a template's lines work in every game copied from it.
 */
public final class SpawnZoneUtils {

    // A block's half diagonal: standing anywhere on a block a line runs through counts as on it
    private static final double ON_LINE_DISTANCE = 0.71;

    private SpawnZoneUtils() {
        throw new AssertionError("Nope.");
    }

    private record Point(double x, double z) {}

    /**
     * Whether both teams have both their spawn lines on this template - the zones only count
     * once the whole map is marked out.
     */
    public static boolean hasZones(TemplateWorld template) {
        if (template == null) return false;
        for (TeamColor team : TeamColor.values()) {
            if (!template.getSpawnLine(team, 1).isComplete() || !template.getSpawnLine(team, 2).isComplete()) return false;
        }
        return true;
    }

    /**
     * Whether a spot is in the team's spawn zone: between its two lines, or on either of them.
     */
    public static boolean isInZone(TemplateWorld template, TeamColor team, Location loc) {
        SpawnLine line1 = template.getSpawnLine(team, 1);
        SpawnLine line2 = template.getSpawnLine(team, 2);
        if (!line1.isComplete() || !line2.isComplete() || loc == null) return false;

        Point p = new Point(loc.getX(), loc.getZ());
        if (distanceToSegment(p, blockCenter(line1.start()), blockCenter(line1.end())) <= ON_LINE_DISTANCE) return true;
        if (distanceToSegment(p, blockCenter(line2.start()), blockCenter(line2.end())) <= ON_LINE_DISTANCE) return true;

        List<Point> hull = convexHull(blockCenter(line1.start()), blockCenter(line1.end()),
                blockCenter(line2.start()), blockCenter(line2.end()));
        return insideConvex(hull, p);
    }

    private static Point blockCenter(Location loc) {
        return new Point(loc.getBlockX() + 0.5, loc.getBlockZ() + 0.5);
    }

    /**
     * The four line ends' outline, anticlockwise. Taking the hull rather than joining the ends
     * in order means it doesn't matter which way round each line was drawn.
     */
    private static List<Point> convexHull(Point... points) {
        Point[] sorted = points.clone();
        Arrays.sort(sorted, Comparator.comparingDouble(Point::x).thenComparingDouble(Point::z));

        List<Point> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int base = hull.size();
            for (int i = 0; i < sorted.length; i++) {
                Point point = sorted[pass == 0 ? i : sorted.length - 1 - i];
                while (hull.size() >= base + 2 && cross(hull.get(hull.size() - 2), hull.getLast(), point) <= 0) {
                    hull.removeLast();
                }
                hull.add(point);
            }
            hull.removeLast();
        }
        return hull;
    }

    private static boolean insideConvex(List<Point> hull, Point p) {
        if (hull.size() < 3) return false;
        for (int i = 0; i < hull.size(); i++) {
            if (cross(hull.get(i), hull.get((i + 1) % hull.size()), p) < 0) return false;
        }
        return true;
    }

    private static double cross(Point o, Point a, Point b) {
        return (a.x() - o.x()) * (b.z() - o.z()) - (a.z() - o.z()) * (b.x() - o.x());
    }

    private static double distanceToSegment(Point p, Point a, Point b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq == 0 ? 0 : Math.clamp(((p.x() - a.x()) * dx + (p.z() - a.z()) * dz) / lengthSq, 0, 1);
        double nearestX = a.x() + t * dx;
        double nearestZ = a.z() + t * dz;
        return Math.hypot(p.x() - nearestX, p.z() - nearestZ);
    }
}
