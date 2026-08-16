package me.psikuvit.cashClash.sequence;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * An ordered list of steps played against a {@link GameSession}, each scheduled with a
 * tick delay relative to the previous step. Built with a fluent API and executed by
 * {@link SequencePlayer}.
 */
public class Sequence {

    public record Entry(long delayTicks, Consumer<GameSession> step) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public static Sequence create() {
        return new Sequence();
    }

    /**
     * Run a step immediately (no delay relative to the previous step).
     */
    public Sequence run(Consumer<GameSession> step) {
        entries.add(new Entry(0L, step));
        return this;
    }

    /**
     * Insert a pause before the next step.
     */
    public Sequence pause(long ticks) {
        entries.add(new Entry(ticks, session -> {
        }));
        return this;
    }

    public Sequence waitSeconds(double seconds) {
        return pause(Math.round(seconds * 20));
    }

    /**
     * Run a step after a delay relative to the previous step.
     */
    public Sequence then(long delayTicks, Consumer<GameSession> step) {
        entries.add(new Entry(delayTicks, step));
        return this;
    }

    /**
     * Unrolls a one-per-second countdown into individual steps, counting down from
     * {@code seconds} to 1, one second apart. Each tick plays a noteblock ding alongside
     * whatever the caller's step does (typically showing the count as a title).
     */
    public Sequence countdown(int seconds, IntFunction<Consumer<GameSession>> stepForCount) {
        for (int count = seconds; count >= 1; count--) {
            Consumer<GameSession> step = stepForCount.apply(count);
            entries.add(new Entry(20L, session -> {
                SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                step.accept(session);
            }));
        }
        return this;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
