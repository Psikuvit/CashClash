package me.psikuvit.cashClash.util.game.ptp;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.TemplateWorld;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Utility class for Protect the President final-stand handling.
 * Extracts border management and the last-life rule from the gamemode.
 */
public class PTPFinalStandUtils {

    private PTPFinalStandUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Tell each president who they're up against in the final stand.
     *
     * @param session The current session
     * @param presidentTeamLookup Function mapping player UUID to president team number (0 if not president)
     * @param opponentNameLookup Function returning the opposing president's name for a given team
     */
    public static void notifyPresidents(
            GameSession session,
            ToIntFunction<UUID> presidentTeamLookup,
            Function<Integer, String> opponentNameLookup) {

        for (UUID playerUuid : new ArrayList<>(session.getPlayers())) {
            Player player = Bukkit.getPlayer(playerUuid);
            int presTeam = presidentTeamLookup.applyAsInt(playerUuid);
            if (player == null || !player.isOnline() || presTeam == 0) {
                continue;
            }

            Messages.send(player, "gamemode-ptp.final-stand-president-survived",
                    "opponent", opponentNameLookup.apply(presTeam == 1 ? 2 : 1));
        }
    }

    /**
     * Configure and shrink the world border for final stand. It starts as the square that takes in
     * the map's two border corners ({@code /cc template set <id> border 1|2}), or 100 blocks around
     * the middle of the team spawns when they aren't set, then closes in as usual.
     * Returns the previous border state so it can be restored later.
     *
     * @param session The current session
     * @return A border snapshot, or null if border could not be configured
     */
    public static BorderSnapshot startFinalStandBorder(GameSession session) {
        if (session.getGameWorld() == null) {
            return null;
        }

        Arena arena = CashClashPlugin.getInstance().getArenaManager().getArena(session.getArenaNumber());
        if (arena == null) {
            Messages.debug("[PTP] Cannot start border: arena not found");
            return null;
        }

        TemplateWorld template = CashClashPlugin.getInstance().getArenaManager().getTemplate(arena.getTemplateId());
        if (template == null) {
            Messages.debug("[PTP] Cannot start border: template not found");
            return null;
        }

        Location corner1 = template.getBorderCorner(1);
        Location corner2 = template.getBorderCorner(2);
        Location from;
        Location to;
        double startSize;
        if (corner1 != null && corner2 != null) {
            from = corner1;
            to = corner2;
            startSize = Math.max(Math.abs(corner1.getX() - corner2.getX()), Math.abs(corner1.getZ() - corner2.getZ()));
        } else {
            from = template.getTeamRedSpawn(0);
            to = template.getTeamBlueSpawn(0);
            startSize = 100.0;
        }
        if (from == null || to == null) {
            Messages.debug("[PTP] Cannot start border: no border corners or team spawns set");
            return null;
        }

        Location center = new Location(session.getGameWorld(),
                (from.getX() + to.getX()) / 2.0,
                (from.getY() + to.getY()) / 2.0,
                (from.getZ() + to.getZ()) / 2.0);

        WorldBorder border = session.getGameWorld().getWorldBorder();
        BorderSnapshot snapshot = new BorderSnapshot(border.getCenter().clone(), border.getSize(), border.getDamageAmount(), border.getDamageBuffer());

        border.setCenter(center);
        border.setSize(Math.max(startSize, 20.0));
        border.setDamageAmount(1.0);
        border.setDamageBuffer(0.0);
        border.setSize(20.0, 30L);

        Messages.debug("[PTP] Final-stand border started: center=" + center + ", initial=" + startSize + ", final=20x20 in 30s");
        return snapshot;
    }

    /**
     * Restore the world border from a previous snapshot.
     *
     * @param session The current session
     * @param snapshot The previously captured border state
     */
    public static void resetFinalStandBorder(GameSession session, BorderSnapshot snapshot) {
        if (session.getGameWorld() == null || snapshot == null || snapshot.size() <= 0) {
            return;
        }

        WorldBorder border = session.getGameWorld().getWorldBorder();
        border.setCenter(snapshot.center());
        border.setSize(snapshot.size());
        border.setDamageAmount(snapshot.damageAmount());
        border.setDamageBuffer(snapshot.damageBuffer());
        Messages.debug("[PTP] Final-stand border reset");
    }

    public record BorderSnapshot(Location center, double size, double damageAmount, double damageBuffer) {}
}

