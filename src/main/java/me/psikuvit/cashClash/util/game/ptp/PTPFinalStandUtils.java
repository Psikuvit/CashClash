package me.psikuvit.cashClash.util.game.ptp;

import me.psikuvit.cashClash.arena.Arena;
import me.psikuvit.cashClash.arena.ArenaManager;
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
 * Extracts border management and elimination logic from the gamemode.
 */
public class PTPFinalStandUtils {

    private PTPFinalStandUtils() {
        throw new AssertionError("Utility class");
    }

    /**
     * Eliminate all non-presidents during final stand and notify the surviving presidents.
     *
     * @param session The current session
     * @param presidentTeamLookup Function mapping player UUID to president team number (0 if not president)
     * @param opponentNameLookup Function returning the opposing president's name for a given team
     */
    public static void eliminateNonPresidents(
            GameSession session,
            ToIntFunction<UUID> presidentTeamLookup,
            Function<Integer, String> opponentNameLookup) {

        Messages.debug("[PTP] Final Stand activated - eliminating all non-Presidents");

        for (UUID playerUuid : new ArrayList<>(session.getPlayers())) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            int presTeam = presidentTeamLookup.applyAsInt(playerUuid);
            if (presTeam == 0) {
                player.setHealth(0);
                Messages.debug("[PTP] Eliminated non-president: " + player.getName());
            } else {
                Messages.send(player, "gamemode-ptp.final-stand-president-survived",
                        "opponent", opponentNameLookup.apply(presTeam == 1 ? 2 : 1));
            }
        }
    }

    /**
     * Configure and shrink the world border for final stand.
     * Returns the previous border state so it can be restored later.
     *
     * @param session The current session
     * @return A border snapshot, or null if border could not be configured
     */
    public static BorderSnapshot startFinalStandBorder(GameSession session) {
        if (session.getGameWorld() == null) {
            return null;
        }

        Arena arena = ArenaManager.getInstance().getArena(session.getArenaNumber());
        if (arena == null) {
            Messages.debug("[PTP] Cannot start border: arena not found");
            return null;
        }

        TemplateWorld template = ArenaManager.getInstance().getTemplate(arena.getTemplateId());
        if (template == null) {
            Messages.debug("[PTP] Cannot start border: template not found");
            return null;
        }

        Location redSpawn = template.getTeamRedSpawn(0);
        Location blueSpawn = template.getTeamBlueSpawn(0);
        if (redSpawn == null || blueSpawn == null) {
            Messages.debug("[PTP] Cannot start border: team spawns not found");
            return null;
        }

        Location center = new Location(session.getGameWorld(),
                (redSpawn.getX() + blueSpawn.getX()) / 2.0,
                (redSpawn.getY() + blueSpawn.getY()) / 2.0,
                (redSpawn.getZ() + blueSpawn.getZ()) / 2.0);

        WorldBorder border = session.getGameWorld().getWorldBorder();
        BorderSnapshot snapshot = new BorderSnapshot(border.getCenter().clone(), border.getSize(), border.getDamageAmount(), border.getDamageBuffer());

        border.setCenter(center);
        border.setSize(100.0);
        border.setDamageAmount(1.0);
        border.setDamageBuffer(0.0);
        border.setSize(20.0, 30L);

        Messages.debug("[PTP] Final-stand border started: center=" + center + ", initial=100x100, final=20x20 in 30s");
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

