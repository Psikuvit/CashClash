package me.psikuvit.cashClash.util.effects;

import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The floating cluster of particles shown above a player whose healing is reduced, shared by
 * every source of a healing-reduction debuff (Soul Katana's Phantom Slice, BloodWrench's blood
 * sphere and vortex) so the visual can't drift apart between them.
 * <p>
 * The mark is driven entirely by {@link CashClashPlayer#isHealingReduced(Player)} rather than by
 * its own timer, so it survives a debuff being refreshed - standing in a BloodWrench zone
 * re-applies the debuff every tick - and disappears the moment healing is genuinely available
 * again, whichever source ends up expiring last.
 */
public class HealingMarkUtils {

    private static final Map<UUID, BukkitTask> ACTIVE_MARKS = new ConcurrentHashMap<>();

    private static final int RING_POINTS = 12;
    private static final double RING_RADIUS = 0.35;
    private static final double HEAD_OFFSET = 2.4;
    private static final int SOUND_INTERVAL_TICKS = 15;

    /**
     * Shows the mark above a player until their healing reduction lapses. No-ops if a mark is
     * already running for them, so a zone that re-applies its debuff every tick doesn't restart
     * the animation or re-announce anything.
     *
     * @param target            the debuffed player
     * @param ringColor         colour of the rotating ring
     * @param wispColor         colour of the drifting wisps under the ring
     * @param pulseSound        played periodically while the mark is up; null for a silent mark
     * @param restoredMessageKey message sent when healing returns; null to stay silent
     */
    public static void show(Player target, Color ringColor, Color wispColor,
                            @Nullable Sound pulseSound, @Nullable String restoredMessageKey) {
        if (target == null || !target.isOnline()) return;
        UUID id = target.getUniqueId();
        if (ACTIVE_MARKS.containsKey(id)) return;

        BukkitTask task = SchedulerUtils.runTaskTimer(new BukkitRunnable() {
            int soundTick = 0;

            @Override
            public void run() {
                if (!target.isOnline() || !CashClashPlayer.isHealingReduced(target)) {
                    cancel();
                    ACTIVE_MARKS.remove(id);
                    CashClashPlayer.clearHealingReduction(target);
                    if (restoredMessageKey != null && target.isOnline()) {
                        Messages.send(target, restoredMessageKey);
                    }
                    return;
                }

                Location center = target.getLocation().clone().add(0, HEAD_OFFSET, 0);
                long time = System.currentTimeMillis();
                for (int i = 0; i < RING_POINTS; i++) {
                    double angle = (Math.PI * 2 / RING_POINTS * i) + (time % 1000) / 1000.0 * Math.PI * 2;
                    double x = Math.cos(angle) * RING_RADIUS;
                    double z = Math.sin(angle) * RING_RADIUS;
                    ParticleUtils.spawnDust(center.clone().add(x, 0, z), ringColor, 1.2f, 1, 0);
                }

                if (pulseSound != null && ++soundTick >= SOUND_INTERVAL_TICKS) {
                    soundTick = 0;
                    SoundUtils.playAt(target.getLocation(), pulseSound, 0.8f, 0.6f);
                }

                ParticleUtils.spawnDust(center.clone().add((Math.random() - 0.5) * 0.4, -0.3, (Math.random() - 0.5) * 0.4),
                        wispColor, 1.0f, 2, 0);
            }
        }, 0L, 2L);

        ACTIVE_MARKS.put(id, task);
    }

    /**
     * Cancels every running mark. Called at session teardown - the marks self-cancel in normal
     * play, this only covers a session ending mid-debuff.
     */
    public static void clearAll() {
        ACTIVE_MARKS.values().forEach(BukkitTask::cancel);
        ACTIVE_MARKS.clear();
    }

    private HealingMarkUtils() {
        throw new AssertionError("Nope.");
    }
}
