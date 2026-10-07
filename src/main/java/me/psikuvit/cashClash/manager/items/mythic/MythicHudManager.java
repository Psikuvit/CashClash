package me.psikuvit.cashClash.manager.items.mythic;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.gamemode.impl.CaptureTheFlagGamemode;
import me.psikuvit.cashClash.manager.Shutdownable;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.ActionBarQueue;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.game.TimerDisplayUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * The action bar above the hotbar while a mythic is held in either hand: the cooldowns of its
 * abilities that aren't ready (nothing once they all are), plus Electric Eel's charges,
 * BlazeBite's next-shot mode and BloodWrench's hit counter. The line flashes red after an
 * ability is tried on cooldown and stays red while the holder is silenced. It steps aside while
 * another action-bar display (a countdown, the Blooming Rose HP reveal) has the bar.
 */
public class MythicHudManager implements Shutdownable {

    private static final long UPDATE_TICKS = 2L;
    // Action-bar text fades out unless it's sent again
    private static final long RESEND_MS = 1000L;

    private final GameManager gameManager;
    private final MythicItemManager mythicItemManager;
    private final Map<UUID, String> shownLine;
    private final Map<UUID, Long> shownAt;
    private BukkitTask task;

    public MythicHudManager(GameManager gameManager, MythicItemManager mythicItemManager) {
        this.gameManager = gameManager;
        this.mythicItemManager = mythicItemManager;
        this.shownLine = new HashMap<>();
        this.shownAt = new HashMap<>();
    }

    public void start() {
        task = SchedulerUtils.runTaskTimer(this::tick, UPDATE_TICKS, UPDATE_TICKS);
    }

    private void tick() {
        Set<UUID> inGame = new HashSet<>();
        for (GameSession session : gameManager.getActiveSessions()) {
            for (UUID uuid : session.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                inGame.add(uuid);
                update(session, player);
            }
        }
        shownLine.keySet().retainAll(inGame);
        shownAt.keySet().retainAll(inGame);
    }

    private void update(GameSession session, Player player) {
        UUID uuid = player.getUniqueId();
        if (TimerDisplayUtils.hasTimer(uuid) || ActionBarQueue.get().hasDisplay(uuid)) {
            shownLine.remove(uuid);
            return;
        }

        String line = buildLine(session, player);
        String previous = shownLine.get(uuid);
        if (line == null) {
            if (previous != null) {
                ActionBarQueue.get().sendRaw(uuid, "");
                shownLine.remove(uuid);
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (!line.equals(previous) || now - shownAt.getOrDefault(uuid, 0L) >= RESEND_MS) {
            ActionBarQueue.get().sendRaw(uuid, line);
            shownLine.put(uuid, line);
            shownAt.put(uuid, now);
        }
    }

    private String buildLine(GameSession session, Player player) {
        MythicItem mythic = PDCDetection.getMythic(player.getInventory().getItemInMainHand());
        if (mythic == null) mythic = PDCDetection.getMythic(player.getInventory().getItemInOffHand());
        if (mythic == null) return null;

        List<HudSegment> segments = mythicItemManager.getHandler(mythic).hudSegments(player);
        boolean silenced = session.getGamemode() instanceof CaptureTheFlagGamemode ctf && ctf.isSilenced(player.getUniqueId());
        if (silenced && segments.isEmpty()) {
            segments = List.of(new HudSegment(MythicItemHandler.hudText("silenced"), true));
        }
        if (segments.isEmpty()) return null;

        boolean allRed = silenced || mythicItemManager.isCooldownFlashing(player.getUniqueId());
        StringJoiner line = new StringJoiner(MythicItemHandler.hudText("separator"));
        for (HudSegment segment : segments) {
            String color = allRed || segment.alert() ? "red" : "white";
            line.add("<" + color + ">" + segment.text() + "</" + color + ">");
        }
        return line.toString();
    }

    @Override
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        shownLine.clear();
        shownAt.clear();
    }
}
